package com.nova.support.dto;

import com.nova.support.domain.entity.ChatMessage;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessageRequest {
    
    private Long ticketId;
    private ChatMessage.SenderType senderType;
    private String senderName;
    private String message;
    private String imageUrl;
    private String audioUrl;
    private String transcription;  // Транскрипция аудио (если есть)
}
