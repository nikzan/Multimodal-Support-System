package com.nova.support.controller;

import com.nova.support.dto.TicketRequest;
import com.nova.support.dto.TicketResponse;
import com.nova.support.service.TicketService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/tickets")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class TicketController {
    
    private final TicketService ticketService;
    
    @PostMapping
    public ResponseEntity<TicketResponse> createTicket(@RequestBody TicketRequest request) {
        log.info("Creating ticket for project API key: {}", request.getProjectApiKey());
        
        TicketResponse response = ticketService.processTicket(request);
        
        log.info("Ticket created with ID: {}", response.getId());
        return ResponseEntity.ok(response);
    }
    
    @GetMapping("/{id}")
    public ResponseEntity<TicketResponse> getTicket(@PathVariable Long id) {
        TicketResponse response = ticketService.getTicket(id);
        return ResponseEntity.ok(response);
    }
}
