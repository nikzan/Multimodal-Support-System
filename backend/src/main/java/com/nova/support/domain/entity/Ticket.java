package com.nova.support.domain.entity;

import com.nova.support.domain.enums.Priority;
import com.nova.support.domain.enums.Sentiment;
import com.nova.support.domain.enums.TicketStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Тикет технической поддержки
 * Поддерживает мультимодальный контент (текст, аудио, изображения)
 * AI автоматически анализирует и обогащает тикет данными
 */
@Entity
@Table(name = "tickets")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Ticket {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    /**
     * Проект (tenant), к которому относится тикет
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;
    
    /**
     * Статус тикета
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private TicketStatus status = TicketStatus.NEW;
    
    // === Информация о клиенте ===
    
    @Column(name = "customer_email")
    private String customerEmail;
    
    @Column(name = "customer_name")
    private String customerName;
    
    // === Оригинальный контент от клиента ===
    
    @Column(name = "original_text", columnDefinition = "TEXT")
    private String originalText;
    
    /**
     * URL аудио файла в MinIO
     */
    @Column(name = "audio_url", length = 500)
    private String audioUrl;
    
    /**
     * URL изображения в MinIO
     */
    @Column(name = "image_url", length = 500)
    private String imageUrl;
    
    // === Результаты AI обработки ===
    
    /**
     * Расшифровка аудио (Speech-to-Text)
     */
    @Column(name = "transcribed_text", columnDefinition = "TEXT")
    private String transcribedText;
    
    /**
     * Краткое содержание тикета (AI summary)
     */
    @Column(name = "ai_summary", columnDefinition = "TEXT")
    private String aiSummary;
    
    /**
     * Sentiment analysis - эмоциональная окраска
     */
    @Enumerated(EnumType.STRING)
    @Column(length = 50)
    private Sentiment sentiment;
    
    /**
     * Оценка sentiment (-1.00 до 1.00)
     * -1 = очень негативный, 0 = нейтральный, 1 = очень позитивный
     */
    @Column(name = "sentiment_score", precision = 3, scale = 2)
    private BigDecimal sentimentScore;
    
    /**
     * Приоритет (определяется AI)
     */
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private Priority priority;
    
    /**
     * Предложенный ответ из базы знаний (RAG)
     */
    @Column(name = "suggested_answer", columnDefinition = "TEXT")
    private String suggestedAnswer;
    
    // === Ответ оператора ===
    
    @Column(name = "operator_response", columnDefinition = "TEXT")
    private String operatorResponse;
    
    /**
     * ID оператора, который ответил на тикет
     */
    @Column(name = "operator_id")
    private Long operatorId;
    
    // === Chat Session ===
    
    /**
     * Session ID для связи тикетов одного клиента
     */
    @Column(name = "session_id")
    private String sessionId;
    
    /**
     * Флаг закрытия тикета
     */
    @Column(name = "is_closed")
    @Builder.Default
    private Boolean isClosed = false;
    
    // === RAG Bucket для накопления сообщений ===
    
    /**
     * Timestamp последнего ответа оператора (для определения границы bucket)
     */
    @Column(name = "last_operator_response_at")
    private LocalDateTime lastOperatorResponseAt;
    
    /**
     * Список ID сообщений в RAG bucket (накапливаются после ответа оператора)
     * Хранится как строка с разделителями
     */
    @Column(name = "rag_bucket_message_ids", columnDefinition = "TEXT")
    private String ragBucketMessageIds;
    
    // === Временные метки ===
    
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
    
    @Column(name = "closed_at")
    private LocalDateTime closedAt;
}
