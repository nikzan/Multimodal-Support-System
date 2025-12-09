package com.nova.support.controller;

import com.nova.support.dto.KnowledgeBaseRequest;
import com.nova.support.dto.KnowledgeBaseResponse;
import com.nova.support.service.KnowledgeBaseService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/admin/knowledge-base")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class KnowledgeBaseController {
    
    private final KnowledgeBaseService knowledgeBaseService;
    
    @GetMapping
    public ResponseEntity<Page<KnowledgeBaseResponse>> getAll(
            @RequestParam Long projectId,
            Pageable pageable) {
        Page<KnowledgeBaseResponse> kb = knowledgeBaseService.getAll(projectId, pageable);
        return ResponseEntity.ok(kb);
    }
    
    @GetMapping("/{id}")
    public ResponseEntity<KnowledgeBaseResponse> getById(@PathVariable Long id) {
        KnowledgeBaseResponse kb = knowledgeBaseService.getById(id);
        return ResponseEntity.ok(kb);
    }
    
    @PostMapping
    public ResponseEntity<KnowledgeBaseResponse> create(@RequestBody KnowledgeBaseRequest request) {
        log.info("Creating knowledge base entry: {}", request.getTitle());
        KnowledgeBaseResponse kb = knowledgeBaseService.create(request);
        return ResponseEntity.ok(kb);
    }
    
    @PutMapping("/{id}")
    public ResponseEntity<KnowledgeBaseResponse> update(
            @PathVariable Long id,
            @RequestBody KnowledgeBaseRequest request) {
        log.info("Updating knowledge base entry: {}", id);
        KnowledgeBaseResponse kb = knowledgeBaseService.update(id, request);
        return ResponseEntity.ok(kb);
    }
    
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        log.info("Deleting knowledge base entry: {}", id);
        knowledgeBaseService.delete(id);
        return ResponseEntity.noContent().build();
    }
    
    @GetMapping("/search")
    public ResponseEntity<Page<KnowledgeBaseResponse>> search(
            @RequestParam Long projectId,
            @RequestParam String query,
            Pageable pageable) {
        log.info("Searching knowledge base for: {}", query);
        Page<KnowledgeBaseResponse> results = knowledgeBaseService.search(projectId, query, pageable);
        return ResponseEntity.ok(results);
    }
}
