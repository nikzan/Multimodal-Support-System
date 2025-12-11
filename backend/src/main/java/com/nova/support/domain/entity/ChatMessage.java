package com.nova.support.domain.entity;

import io.hypersistence.utils.hibernate.type.json.JsonType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Type;

import java.time.LocalDateTime;

@Entity
@Table(name = "chat_messages")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessage {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "ticket_id", nullable = false)
    private Long ticketId;
    
    @Column(name = "sender_type", nullable = false, length = 50)
    @Enumerated(EnumType.STRING)
    private SenderType senderType;
    
    @Column(name = "sender_name")
    private String senderName;
    
    @Column(nullable = false, columnDefinition = "TEXT")
    private String message;
    
    @Column(name = "image_url", length = 500)
    private String imageUrl;
    
    @Column(name = "audio_url", length = 500)
    private String audioUrl;
    
    @Column(name = "metadata")
    @Type(JsonType.class)
    private String metadata;
    
    @Column(name = "created_at")
    private LocalDateTime createdAt;
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
    
    public enum SenderType {
        CLIENT,
        OPERATOR
    }
}
