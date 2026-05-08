package com.docforge.collaboration;

import org.springframework.web.socket.WebSocketSession;

import java.util.Set;

/**
 * Abstracts how Yjs messages are broadcast to document participants.
 *
 * MVP: {@link LocalCollaborationBroadcaster} — in-memory, single instance.
 * Future: Redis pub/sub implementation for multi-instance deployments.
 */
public interface CollaborationBroadcaster {

    /** Register a session as a participant of the given document. */
    void registerSession(String docId, WebSocketSession session);

    /** Remove a session from the document's participant set. */
    void unregisterSession(String docId, WebSocketSession session);

    /**
     * Send {@code data} to every open session of the document except {@code sender}.
     * Silently skips closed sessions.
     */
    void broadcastToOthers(String docId, WebSocketSession sender, byte[] data);

    /** Returns all currently registered sessions for the document. */
    Set<WebSocketSession> getSessions(String docId);
}
