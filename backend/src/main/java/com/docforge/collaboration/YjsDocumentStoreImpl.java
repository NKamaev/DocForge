package com.docforge.collaboration;

import com.docforge.document.DocumentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;

/**
 * Single-instance Yjs document store backed by MongoDB via {@link DocumentService}.
 *
 * State lifecycle:
 *   load (first getUpdates) → accumulate update list in memory → debounce-save → evict on last disconnect
 *
 * Each Yjs update is stored as a separate element in the list.
 * They are sent to new clients as individual SYNC_UPDATE frames so that
 * Y.applyUpdate can parse each one independently (concatenation of raw Yjs
 * update bytes is NOT a valid single Yjs update — only the first chunk would
 * be parsed, silently discarding the rest).
 *
 * MongoDB persistence format: each update prefixed with its 4-byte big-endian
 * length, all packed into a single byte[].
 *
 * Thread safety: ConcurrentHashMap for cache; list mutations use per-docId synchronization.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class YjsDocumentStoreImpl implements YjsDocumentStore {

    private static final int DEBOUNCE_SECONDS = 5;

    private final DocumentService documentService;

    /** In-memory list of Yjs updates per docId. */
    private final ConcurrentHashMap<String, List<byte[]>> updateCache = new ConcurrentHashMap<>();

    /** Per-docId debounce timers. */
    private final ConcurrentHashMap<String, ScheduledFuture<?>> debounceTimers = new ConcurrentHashMap<>();

    /** Single scheduler for debounced persist tasks. */
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "yjs-persist");
        t.setDaemon(true);
        return t;
    });

    // ── YjsDocumentStore ──────────────────────────────────────────────────────

    @Override
    public List<byte[]> getUpdates(String docId) {
        List<byte[]> list = updateCache.computeIfAbsent(docId, this::loadFromDb);
        synchronized (list) {
            return new ArrayList<>(list);
        }
    }

    @Override
    public void applyUpdate(String docId, byte[] update) {
        List<byte[]> list = updateCache.computeIfAbsent(docId, this::loadFromDb);
        synchronized (list) {
            list.add(update);
        }
    }

    @Override
    public void schedulePersist(String docId) {
        ScheduledFuture<?> existing = debounceTimers.get(docId);
        if (existing != null) existing.cancel(false);

        ScheduledFuture<?> task = scheduler.schedule(
                () -> doPersist(docId),
                DEBOUNCE_SECONDS,
                TimeUnit.SECONDS
        );
        debounceTimers.put(docId, task);
    }

    @Override
    public void persistNow(String docId) {
        ScheduledFuture<?> pending = debounceTimers.remove(docId);
        if (pending != null) pending.cancel(false);
        doPersist(docId);
    }

    @Override
    public void evict(String docId) {
        updateCache.remove(docId);
        log.debug("YjsDocumentStore: evicted docId={}", docId);
    }

    // ── Private ───────────────────────────────────────────────────────────────

    private List<byte[]> loadFromDb(String docId) {
        try {
            UUID id = UUID.fromString(docId);
            byte[] packed = documentService.getYjsState(id);
            if (packed != null && packed.length > 0) {
                List<byte[]> updates = unpack(packed);
                log.debug("YjsDocumentStore: loaded {} update(s) from DB for docId={}", updates.size(), docId);
                return Collections.synchronizedList(new ArrayList<>(updates));
            }
        } catch (IllegalArgumentException e) {
            log.warn("YjsDocumentStore: invalid docId format: {}", docId);
        }
        return Collections.synchronizedList(new ArrayList<>());
    }

    private void doPersist(String docId) {
        List<byte[]> updates = updateCache.get(docId);
        if (updates == null) return;
        List<byte[]> snapshot;
        synchronized (updates) {
            if (updates.isEmpty()) return;
            snapshot = new ArrayList<>(updates);
        }
        try {
            UUID id = UUID.fromString(docId);
            byte[] packed = pack(snapshot);
            documentService.saveYjsState(id, packed);
            log.debug("YjsDocumentStore: persisted {} update(s) ({} bytes) for docId={}",
                    snapshot.size(), packed.length, docId);
        } catch (Exception e) {
            log.error("YjsDocumentStore: failed to persist docId={}", docId, e);
        }
    }

    /**
     * Serializes a list of byte arrays into a single byte array.
     * Format: for each entry, 4-byte big-endian length followed by the entry bytes.
     */
    static byte[] pack(List<byte[]> updates) {
        int totalLen = updates.stream().mapToInt(u -> 4 + u.length).sum();
        ByteBuffer buf = ByteBuffer.allocate(totalLen);
        for (byte[] update : updates) {
            buf.putInt(update.length);
            buf.put(update);
        }
        return buf.array();
    }

    /**
     * Deserializes a packed byte array back into individual updates.
     * Tolerates trailing garbage by stopping on length = 0 or buffer underflow.
     */
    static List<byte[]> unpack(byte[] packed) {
        List<byte[]> result = new ArrayList<>();
        ByteBuffer buf = ByteBuffer.wrap(packed);
        while (buf.remaining() >= 4) {
            int len = buf.getInt();
            if (len <= 0 || len > buf.remaining()) break;
            byte[] update = new byte[len];
            buf.get(update);
            result.add(update);
        }
        return result;
    }
}
