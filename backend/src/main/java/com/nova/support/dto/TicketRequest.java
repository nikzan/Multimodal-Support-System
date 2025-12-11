package com.nova.support.dto;

import lombok.Data;

@Data
public class TicketRequest {
    private String projectApiKey;
    private String sessionId;      // Session ID для связи тикетов клиента
    private String text;           // Текстовый тикет
    private String audioBase64;    // Аудио в base64 (опционально)
    private String imageBase64;    // Изображение в base64 (опционально)
    private String language;       // Язык для транскрибации (ru, en, auto)
}

