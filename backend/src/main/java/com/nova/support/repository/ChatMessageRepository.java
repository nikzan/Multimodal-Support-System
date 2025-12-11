package com.nova.support.repository;

import com.nova.support.domain.entity.ChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {
    
    /**
     * Получить все сообщения тикета, отсортированные по времени
     */
    List<ChatMessage> findByTicketIdOrderByCreatedAtAsc(Long ticketId);
    
    /**
     * Подсчитать количество сообщений в тикете
     */
    long countByTicketId(Long ticketId);
}
