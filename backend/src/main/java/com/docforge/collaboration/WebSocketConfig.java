package com.docforge.collaboration;

import com.docforge.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * Registers the Yjs collaboration WebSocket endpoint.
 *
 * Endpoint: /ws/collab/{documentId}
 * Auth:     JWT via ?token= query param (validated in JwtHandshakeInterceptor)
 * Origins:  configured via app.ws.allowed-origins in application.yml
 */
@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketConfigurer {

    private final YjsDocumentStore     store;
    private final CollaborationBroadcaster broadcaster;
    private final JwtTokenProvider     jwtTokenProvider;

    @Value("${app.ws.allowed-origins}")
    private String allowedOrigins;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        String[] origins = allowedOrigins.split(",");

        registry.addHandler(
                        new YjsWebSocketHandler(store, broadcaster),
                        "/ws/collab/*"
                )
                .addInterceptors(new JwtHandshakeInterceptor(jwtTokenProvider))
                .setAllowedOrigins(origins);
    }
}
