package com.nova.support.domain.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

/**
 * База знаний для RAG (Retrieval-Augmented Generation)
 * Хранит документы с vector embeddings для семантического поиска
 */
@Entity
@Table(name = "knowledge_base")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class KnowledgeBase {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    /**
     * Проект (tenant), к которому относится запись
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;
    
    /**
     * Заголовок записи
     */
    @Column(nullable = false, length = 500)
    private String title;
    
    /**
     * Текстовое содержимое записи (FAQ, документация, etc.)
     */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;
    
    /**
     * Vector embedding для семантического поиска
     * Размерность 768 соответствует nomic-embed-text (Ollama)
     */
    @Column(columnDefinition = "vector(768)")
    @JdbcTypeCode(SqlTypes.VECTOR)
    private float[] embedding;
    
    /**
     * Тип источника (faq, manual, article, etc.)
     */
    @Column(name = "source_type", length = 50)
    private String sourceType;
    
    /**
     * URL источника (если применимо)
     */
    @Column(name = "source_url", length = 500)
    private String sourceUrl;
    
    // === Временные метки ===
    
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
