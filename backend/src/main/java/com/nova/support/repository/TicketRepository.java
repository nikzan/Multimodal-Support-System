package com.nova.support.repository;

import com.nova.support.domain.entity.Ticket;
import com.nova.support.domain.enums.TicketStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Репозиторий для работы с тикетами
 */
@Repository
public interface TicketRepository extends JpaRepository<Ticket, Long> {
    
    /**
     * Найти все тикеты проекта с пагинацией
     * @param projectId ID проекта
     * @param pageable параметры пагинации
     * @return страница тикетов
     */
    Page<Ticket> findByProjectId(Long projectId, Pageable pageable);
    
    /**
     * Найти тикеты проекта по статусу
     * @param projectId ID проекта
     * @param status статус тикета
     * @param pageable параметры пагинации
     * @return страница тикетов
     */
    Page<Ticket> findByProjectIdAndStatus(Long projectId, TicketStatus status, Pageable pageable);
    
    /**
     * Найти тикеты проекта по email клиента
     * @param projectId ID проекта
     * @param customerEmail email клиента
     * @param pageable параметры пагинации
     * @return страница тикетов
     */
    Page<Ticket> findByProjectIdAndCustomerEmail(Long projectId, String customerEmail, Pageable pageable);
    
    /**
     * Подсчитать количество тикетов проекта по статусу
     * @param projectId ID проекта
     * @param status статус тикета
     * @return количество тикетов
     */
    long countByProjectIdAndStatus(Long projectId, TicketStatus status);
    
    /**
     * Найти новые тикеты, ожидающие AI обработки
     * @return список тикетов со статусом NEW
     */
    @Query("SELECT t FROM Ticket t WHERE t.status = 'NEW' ORDER BY t.createdAt ASC")
    List<Ticket> findNewTicketsForProcessing();
    
    /**
     * Найти активный (незакрытый) тикет по session ID
     */
    @Query("SELECT t FROM Ticket t WHERE t.sessionId = :sessionId AND t.isClosed = false ORDER BY t.createdAt DESC")
    List<Ticket> findActiveTicketsBySessionId(String sessionId);
}

