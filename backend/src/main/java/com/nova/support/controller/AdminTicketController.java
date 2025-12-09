package com.nova.support.controller;

import com.nova.support.dto.TicketResponse;
import com.nova.support.service.TicketService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST API для управления тикетами (Admin)
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/tickets")
@RequiredArgsConstructor
public class AdminTicketController {
    
    private final TicketService ticketService;
    
    /**
     * Получить все тикеты проекта с пагинацией
     */
    @GetMapping
    public ResponseEntity<Page<TicketResponse>> getTickets(
            @RequestParam Long projectId,
            Pageable pageable) {
        log.info("Getting tickets for project: {}", projectId);
        Page<TicketResponse> tickets = ticketService.getTicketsByProject(projectId, pageable);
        return ResponseEntity.ok(tickets);
    }
    
    /**
     * Получить тикет по ID
     */
    @GetMapping("/{id}")
    public ResponseEntity<TicketResponse> getTicket(@PathVariable Long id) {
        log.info("Getting ticket: {}", id);
        TicketResponse ticket = ticketService.getTicket(id);
        return ResponseEntity.ok(ticket);
    }
    
    /**
     * Обновить статус тикета
     */
    @PatchMapping("/{id}/status")
    public ResponseEntity<TicketResponse> updateStatus(
            @PathVariable Long id,
            @RequestParam String status) {
        log.info("Updating ticket {} status to: {}", id, status);
        TicketResponse ticket = ticketService.updateStatus(id, status);
        return ResponseEntity.ok(ticket);
    }
    
    /**
     * Удалить тикет
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTicket(@PathVariable Long id) {
        log.info("Deleting ticket: {}", id);
        ticketService.deleteTicket(id);
        return ResponseEntity.noContent().build();
    }
}
