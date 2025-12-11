package com.nova.support.service;

import com.nova.support.dto.ChatMessageRequest;
import com.nova.support.dto.ChatMessageResponse;
import com.nova.support.domain.entity.ChatMessage;
import com.nova.support.repository.ChatMessageRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
public class ChatService {
    
    private final ChatMessageRepository chatMessageRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final TicketService ticketService;
    
    public ChatService(
        ChatMessageRepository chatMessageRepository,
        SimpMessagingTemplate messagingTemplate,
        @Lazy TicketService ticketService
    ) {
        this.chatMessageRepository = chatMessageRepository;
        this.messagingTemplate = messagingTemplate;
        this.ticketService = ticketService;
    }
    
    /**
     * Отправить сообщение в чат
     */
    @Transactional
    public ChatMessageResponse sendMessage(ChatMessageRequest request) {
        log.info("Sending message to ticket {}: {}", request.getTicketId(), request.getMessage());
        
        ChatMessage message = new ChatMessage();
        message.setTicketId(request.getTicketId());
        message.setSenderType(request.getSenderType());
        message.setSenderName(request.getSenderName());
        message.setMessage(request.getMessage());
        message.setImageUrl(request.getImageUrl());
        message.setAudioUrl(request.getAudioUrl());
        
        // Сохранить транскрипцию в metadata если есть
        if (request.getTranscription() != null && !request.getTranscription().isEmpty()) {
            try {
                com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                String metadata = mapper.writeValueAsString(
                    java.util.Map.of("transcription", request.getTranscription())
                );
                message.setMetadata(metadata);
            } catch (Exception e) {
                log.error("Failed to serialize metadata", e);
            }
        }
        
        ChatMessage saved = chatMessageRepository.save(message);
        ChatMessageResponse response = ChatMessageResponse.from(saved);
        
        // RAG Bucket Logic
        if (request.getSenderType() == ChatMessage.SenderType.CLIENT) {
            // Add client message to RAG bucket
            ticketService.addMessageToBucket(request.getTicketId(), saved.getId());
        } else if (request.getSenderType() == ChatMessage.SenderType.OPERATOR) {
            // Clear RAG bucket after operator response
            ticketService.clearBucket(request.getTicketId());
        }
        
        // Отправляем через WebSocket
        messagingTemplate.convertAndSend("/topic/tickets/" + request.getTicketId() + "/messages", response);
        
        log.info("Message sent successfully: {}", saved.getId());
        return response;
    }
    
    /**
     * Получить всю историю сообщений тикета
     */
    public List<ChatMessageResponse> getTicketMessages(Long ticketId) {
        log.info("Getting messages for ticket {}", ticketId);
        return chatMessageRepository.findByTicketIdOrderByCreatedAtAsc(ticketId)
                .stream()
                .map(ChatMessageResponse::from)
                .collect(Collectors.toList());
    }
}
