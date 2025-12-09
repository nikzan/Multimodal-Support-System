package com.nova.support.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * Конфигурация WebSocket для real-time уведомлений
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // Префикс для сообщений от сервера к клиентам
        config.enableSimpleBroker("/topic");
        
        // Префикс для сообщений от клиентов к серверу
        config.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // Эндпоинт для WebSocket подключения
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*") // В продакшене указать конкретные домены
                .withSockJS(); // Fallback для браузеров без WebSocket
    }
}
