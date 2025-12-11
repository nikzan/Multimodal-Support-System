package com.nova.support.dto;

import com.nova.support.domain.entity.ChatMessage;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessageResponse {
    
    private Long id;
    private Long ticketId;
    private ChatMessage.SenderType senderType;
    private String senderName;
    private String message;
    private String imageUrl;
    private String audioUrl;
    private String metadata;
    private LocalDateTime createdAt;
    
    public static ChatMessageResponse from(ChatMessage message) {
        return ChatMessageResponse.builder()
                .id(message.getId())
                .ticketId(message.getTicketId())
                .senderType(message.getSenderType())
                .senderName(message.getSenderName())
                .message(message.getMessage())
                .imageUrl(message.getImageUrl())
                .audioUrl(message.getAudioUrl())
                .metadata(message.getMetadata())
                .createdAt(message.getCreatedAt())
                .build();
    }
}
