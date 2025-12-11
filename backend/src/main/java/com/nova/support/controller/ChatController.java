package com.nova.support.controller;

import com.nova.support.dto.ChatMessageRequest;
import com.nova.support.dto.ChatMessageResponse;
import com.nova.support.dto.RagAnswerResponse;
import com.nova.support.dto.TicketResponse;
import com.nova.support.service.ChatService;
import com.nova.support.service.TicketService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/tickets")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class ChatController {
    
    private final ChatService chatService;
    private final TicketService ticketService;
    
    /**
     * Отправить сообщение в чат тикета
     */
    @PostMapping("/{ticketId}/messages")
    public ResponseEntity<ChatMessageResponse> sendMessage(
            @PathVariable Long ticketId,
            @RequestBody ChatMessageRequest request) {
        log.info("Sending message to ticket {}", ticketId);
        request.setTicketId(ticketId);
        ChatMessageResponse response = chatService.sendMessage(request);
        return ResponseEntity.ok(response);
    }
    
    /**
     * Получить историю сообщений тикета
     */
    @GetMapping("/{ticketId}/messages")
    public ResponseEntity<List<ChatMessageResponse>> getMessages(@PathVariable Long ticketId) {
        log.info("Getting messages for ticket {}", ticketId);
        List<ChatMessageResponse> messages = chatService.getTicketMessages(ticketId);
        return ResponseEntity.ok(messages);
    }
    
    /**
     * Получить активный тикет по session ID
     */
    @GetMapping("/session/{sessionId}")
    public ResponseEntity<TicketResponse> getActiveTicketBySession(@PathVariable String sessionId) {
        log.info("Getting active ticket for session {}", sessionId);
        TicketResponse ticket = ticketService.getActiveTicketBySession(sessionId);
        if (ticket == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(ticket);
    }
    
    /**
     * Закрыть тикет (для оператора)
     */
    @PatchMapping("/{ticketId}/close")
    public ResponseEntity<TicketResponse> closeTicket(@PathVariable Long ticketId) {
        log.info("Closing ticket {}", ticketId);
        TicketResponse response = ticketService.closeTicket(ticketId);
        return ResponseEntity.ok(response);
    }
    
    /**
     * Получить RAG ответ для accumulated messages в bucket
     */
    @GetMapping("/{ticketId}/rag-answer")
    public ResponseEntity<RagAnswerResponse> getRagAnswer(@PathVariable Long ticketId) {
        log.info("Getting RAG answer for ticket {}", ticketId);
        RagAnswerResponse response = ticketService.generateRagAnswer(ticketId);
        return ResponseEntity.ok(response);
    }
}
