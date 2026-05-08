package com.docforge.collaboration;

import com.docforge.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

/**
 * Validates the JWT from the {@code ?token=} query parameter during WebSocket handshake.
 *
 * On success: stores the login in session attributes under key "login".
 * On failure: rejects the handshake (returns false → HTTP 401).
 *
 * Browsers cannot set custom headers during WS upgrade, so the token is passed
 * as a query parameter: {@code wss://host/ws/collab/{docId}?token=<JWT>}
 */
@Slf4j
@RequiredArgsConstructor
class JwtHandshakeInterceptor implements HandshakeInterceptor {

    static final String ATTR_LOGIN = "login";

    private final JwtTokenProvider jwtTokenProvider;

    @Override
    public boolean beforeHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Map<String, Object> attributes
    ) {
        String query = request.getURI().getQuery();
        String token = extractToken(query);

        if (token == null || !jwtTokenProvider.validate(token)) {
            log.warn("Collab: rejected WS handshake — invalid or missing token. URI={}", request.getURI());
            return false;
        }

        String login = jwtTokenProvider.getLogin(token);
        attributes.put(ATTR_LOGIN, login);
        log.debug("Collab: WS handshake accepted for login={}", login);
        return true;
    }

    @Override
    public void afterHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Exception exception
    ) {
        // no-op
    }

    private String extractToken(String query) {
        if (query == null) return null;
        for (String param : query.split("&")) {
            if (param.startsWith("token=")) {
                return param.substring("token=".length());
            }
        }
        return null;
    }
}
