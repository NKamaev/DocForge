package com.docforge.collaboration;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.BinaryWebSocketHandler;

import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.Set;

/**
 * Handles the y-websocket binary protocol over WebSocket.
 *
 * Endpoint: /ws/collab/{documentId}
 *
 * y-websocket v3 outer message types:
 *   0 (MSG_SYNC)      — sync sub-protocol: sub-type in next varint
 *   1 (MSG_AWARENESS) — cursor / presence relay
 *
 * Sync sub-types (after MSG_SYNC varint):
 *   0 (SYNC_STEP1) — client sends state vector; server replies with step2 (full state)
 *   1 (SYNC_STEP2) — client sends update based on server's step1; server applies + broadcasts
 *   2 (SYNC_UPDATE) — incremental CRDT update; server applies + broadcasts
 *
 * Persistence:
 *   Updates accumulate in YjsDocumentStore (in-memory).
 *   Debounced flush to MongoDB every 5 s; immediate flush on last disconnect.
 */
@Slf4j
@RequiredArgsConstructor
class YjsWebSocketHandler extends BinaryWebSocketHandler {

    private final YjsDocumentStore        store;
    private final CollaborationBroadcaster broadcaster;

    // ── Connection lifecycle ──────────────────────────────────────────────────

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String docId = extractDocId(session);
        broadcaster.registerSession(docId, session);

        // 1. Send syncStep1 (empty SV) — client will reply with its full state
        sendBinary(session, Varint.encodeSyncStep1());

        // 2. Send empty syncStep2 to satisfy the sync handshake
        sendBinary(session, Varint.encodeSyncStep2(new byte[0]));

        // 3. Send each stored update individually as SYNC_UPDATE frames.
        //    Raw concatenation of Yjs updates is NOT a valid single Yjs update —
        //    Y.applyUpdate would only parse the first chunk and silently drop the rest.
        //    Sending individually guarantees all updates are applied by the client.
        var updates = store.getUpdates(docId);
        int count = 0;
        for (byte[] update : updates) {
            sendBinary(session, Varint.encodeUpdate(update));
            count++;
        }

        log.debug("Collab: connected session={} docId={} replayedUpdates={}",
                session.getId(), docId, count);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String docId = extractDocId(session);
        broadcaster.unregisterSession(docId, session);

        if (broadcaster.getSessions(docId).isEmpty()) {
            store.persistNow(docId);
            store.evict(docId);
            log.debug("Collab: last session closed docId={} — state persisted", docId);
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable ex) {
        log.warn("Collab: transport error session={}: {}", session.getId(), ex.getMessage());
    }

    // ── Message dispatch ──────────────────────────────────────────────────────

    @Override
    protected void handleBinaryMessage(WebSocketSession session, BinaryMessage message) {
        String docId = extractDocId(session);
        // Duplicate the buffer so we can re-read raw bytes for relay
        byte[] rawBytes = toByteArray(message.getPayload());
        ByteBuffer buf = ByteBuffer.wrap(rawBytes);

        int outerType;
        try { outerType = Varint.readVarUint(buf); }
        catch (Exception e) {
            log.warn("Collab: cannot read outer type session={}", session.getId());
            return;
        }

        switch (outerType) {
            case Varint.MSG_SYNC      -> handleSyncMessage(session, docId, buf, rawBytes);
            case Varint.MSG_AWARENESS -> handleAwareness(session, docId, rawBytes);
            default -> log.debug("Collab: ignored outer type={} session={}", outerType, session.getId());
        }
    }

    // ── Sync sub-protocol ─────────────────────────────────────────────────────

    private void handleSyncMessage(WebSocketSession session, String docId,
                                   ByteBuffer buf, byte[] rawBytes) {
        int syncSubType;
        try { syncSubType = Varint.readVarUint(buf); }
        catch (Exception e) {
            log.warn("Collab: cannot read sync sub-type session={}", session.getId());
            return;
        }

        switch (syncSubType) {
            case Varint.SYNC_STEP1 -> handleSyncStep1(session, docId, buf);
            case Varint.SYNC_STEP2 -> handleSyncStep2(session, docId, buf, rawBytes);
            case Varint.SYNC_UPDATE -> handleUpdate(session, docId, buf, rawBytes);
            default -> log.debug("Collab: ignored sync sub-type={} session={}", syncSubType, session.getId());
        }
    }

    /**
     * Client sends its state vector → server responds with empty syncStep2, then replays
     * all stored updates individually as SYNC_UPDATE frames.
     */
    private void handleSyncStep1(WebSocketSession session, String docId, ByteBuffer buf) {
        try { Varint.readVarUint8Array(buf); } catch (Exception ignored) { /* discard sv */ }

        try {
            sendBinary(session, Varint.encodeSyncStep2(new byte[0]));
            var updates = store.getUpdates(docId);
            for (byte[] update : updates) {
                sendBinary(session, Varint.encodeUpdate(update));
            }
            log.debug("Collab: syncStep1→updates docId={} count={}", docId, updates.size());
        } catch (IOException e) {
            log.warn("Collab: failed to send syncStep2/updates session={}", session.getId());
        }
    }

    /**
     * Client sends what the server was missing (based on server's empty state vector).
     * Apply to server state; broadcast to others as SYNC_UPDATE so they also receive it.
     */
    private void handleSyncStep2(WebSocketSession session, String docId,
                                  ByteBuffer buf, byte[] rawBytes) {
        byte[] update;
        try { update = Varint.readVarUint8Array(buf); } catch (Exception e) { return; }
        if (update.length == 0) return;

        store.applyUpdate(docId, update);
        store.schedulePersist(docId);

        // Rebroadcast as SYNC_UPDATE (not SYNC_STEP2) so peers can apply it
        try {
            byte[] relayMsg = Varint.encodeUpdate(update);
            broadcaster.broadcastToOthers(docId, session, relayMsg);
        } catch (IOException e) {
            log.warn("Collab: failed to rebroadcast step2 docId={}", docId);
        }
    }

    /**
     * Incremental CRDT update — apply + relay raw message unchanged.
     */
    private void handleUpdate(WebSocketSession session, String docId,
                               ByteBuffer buf, byte[] rawBytes) {
        byte[] update;
        try { update = Varint.readVarUint8Array(buf); } catch (Exception e) { return; }
        if (update.length == 0) return;

        store.applyUpdate(docId, update);
        store.schedulePersist(docId);
        broadcaster.broadcastToOthers(docId, session, rawBytes); // relay original frame
        log.debug("Collab: update applied docId={} bytes={}", docId, update.length);
    }

    // ── Awareness ─────────────────────────────────────────────────────────────

    /** Relay awareness (cursors, presence) unchanged — not persisted. */
    private void handleAwareness(WebSocketSession session, String docId, byte[] rawBytes) {
        broadcaster.broadcastToOthers(docId, session, rawBytes);
        log.debug("Collab: awareness relayed docId={}", docId);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String extractDocId(WebSocketSession session) {
        URI uri = session.getUri();
        if (uri == null) return "unknown";
        String path = uri.getPath();
        int last = path.lastIndexOf('/');
        return last >= 0 ? path.substring(last + 1) : path;
    }

    private void sendBinary(WebSocketSession session, byte[] data) throws IOException {
        if (session.isOpen()) {
            synchronized (session) {
                session.sendMessage(new BinaryMessage(data));
            }
        }
    }

    private byte[] toByteArray(ByteBuffer buf) {
        buf.rewind();
        byte[] arr = new byte[buf.remaining()];
        buf.get(arr);
        buf.rewind();
        return arr;
    }
}
