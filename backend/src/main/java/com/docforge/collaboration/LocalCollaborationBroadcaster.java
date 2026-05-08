package com.docforge.collaboration;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory broadcaster for a single server instance.
 *
 * Stores {@code docId → Set<WebSocketSession>} in a {@link ConcurrentHashMap}.
 * Session sets use {@link ConcurrentHashMap#newKeySet()} for thread-safe iteration.
 *
 * Replace with a Redis pub/sub implementation to support horizontal scaling.
 */
@Slf4j
@Component
class LocalCollaborationBroadcaster implements CollaborationBroadcaster {

    /** docId → active WebSocket sessions */
    private final ConcurrentHashMap<String, Set<WebSocketSession>> sessionsByDoc =
            new ConcurrentHashMap<>();

    @Override
    public void registerSession(String docId, WebSocketSession session) {
        sessionsByDoc.computeIfAbsent(docId, k -> ConcurrentHashMap.newKeySet())
                     .add(session);
        log.debug("Collab: session {} joined doc {}", session.getId(), docId);
    }

    @Override
    public void unregisterSession(String docId, WebSocketSession session) {
        Set<WebSocketSession> sessions = sessionsByDoc.get(docId);
        if (sessions != null) {
            sessions.remove(session);
            if (sessions.isEmpty()) {
                sessionsByDoc.remove(docId);
            }
        }
        log.debug("Collab: session {} left doc {}", session.getId(), docId);
    }

    @Override
    public void broadcastToOthers(String docId, WebSocketSession sender, byte[] data) {
        Set<WebSocketSession> sessions = sessionsByDoc.getOrDefault(docId, Collections.emptySet());
        BinaryMessage message = new BinaryMessage(data);
        for (WebSocketSession s : sessions) {
            if (s.getId().equals(sender.getId()) || !s.isOpen()) continue;
            try {
                synchronized (s) {          // WebSocketSession.sendMessage is not thread-safe
                    s.sendMessage(message);
                }
            } catch (IOException e) {
                log.warn("Collab: failed to send to session {}: {}", s.getId(), e.getMessage());
            }
        }
    }

    @Override
    public Set<WebSocketSession> getSessions(String docId) {
        return sessionsByDoc.getOrDefault(docId, Collections.emptySet());
    }
}
