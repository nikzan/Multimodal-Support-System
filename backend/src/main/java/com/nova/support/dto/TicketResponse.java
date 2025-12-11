package com.nova.support.dto;

import com.nova.support.domain.enums.Priority;
import com.nova.support.domain.enums.Sentiment;
import com.nova.support.domain.enums.TicketStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class TicketResponse {
    private Long id;
    private String sessionId;
    private String originalText;
    private String transcribedText;
    private String aiSummary;
    private Sentiment sentiment;
    private Double sentimentScore;
    private Priority priority;
    private String suggestedAnswer;
    private TicketStatus status;
    private Boolean isClosed;
    private String audioUrl;
    private String imageUrl;
    private LocalDateTime createdAt;
}

