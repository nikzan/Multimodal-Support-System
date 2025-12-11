package com.nova.support.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Response для RAG ответа на accumulated messages
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RagAnswerResponse {
    
    /**
     * Сгенерированный RAG ответ
     */
    private String answer;
    
    /**
     * Количество сообщений в bucket
     */
    private Integer messagesCount;
    
    /**
     * Время последнего обновления RAG ответа
     */
    private LocalDateTime lastUpdated;
    
    /**
     * ID сообщений, которые были использованы для генерации
     */
    private String messageIds;
}
