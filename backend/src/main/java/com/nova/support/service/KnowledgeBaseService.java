package com.nova.support.service;

import com.nova.support.domain.entity.KnowledgeBase;
import com.nova.support.domain.entity.Project;
import com.nova.support.dto.KnowledgeBaseRequest;
import com.nova.support.dto.KnowledgeBaseResponse;
import com.nova.support.repository.KnowledgeBaseRepository;
import com.nova.support.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeBaseService {
    
    private final KnowledgeBaseRepository knowledgeBaseRepository;
    private final ProjectRepository projectRepository;
    private final OllamaService ollamaService;
    
    public Page<KnowledgeBaseResponse> getAll(Long projectId, Pageable pageable) {
        return knowledgeBaseRepository.findByProjectId(projectId, pageable)
                .map(this::mapToResponse);
    }
    
    public KnowledgeBaseResponse getById(Long id) {
        KnowledgeBase kb = knowledgeBaseRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Knowledge base entry not found"));
        return mapToResponse(kb);
    }
    
    @Transactional
    public KnowledgeBaseResponse create(KnowledgeBaseRequest request) {
        Project project = projectRepository.findById(request.getProjectId())
                .orElseThrow(() -> new RuntimeException("Project not found"));
        
        KnowledgeBase kb = new KnowledgeBase();
        kb.setProject(project);
        kb.setTitle(request.getTitle());
        kb.setContent(request.getContent());
        kb.setSourceType(request.getSourceType());
        kb.setSourceUrl(request.getSourceUrl());
        
        // Генерируем эмбеддинг для семантического поиска
        String textToEmbed = request.getTitle() + "\n\n" + request.getContent();
        float[] embeddingArray = ollamaService.generateEmbedding(textToEmbed);
        String embeddingStr = convertEmbeddingToString(embeddingArray);
        kb.setEmbedding(embeddingStr);
        
        kb = knowledgeBaseRepository.save(kb);
        log.info("Created knowledge base entry: {}", kb.getTitle());
        
        return mapToResponse(kb);
    }
    
    @Transactional
    public KnowledgeBaseResponse update(Long id, KnowledgeBaseRequest request) {
        KnowledgeBase kb = knowledgeBaseRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Knowledge base entry not found"));
        
        kb.setTitle(request.getTitle());
        kb.setContent(request.getContent());
        kb.setSourceType(request.getSourceType());
        kb.setSourceUrl(request.getSourceUrl());
        
        // Перегенерируем эмбеддинг при изменении контента
        String textToEmbed = request.getTitle() + "\n\n" + request.getContent();
        float[] embeddingArray = ollamaService.generateEmbedding(textToEmbed);
        String embeddingStr = convertEmbeddingToString(embeddingArray);
        kb.setEmbedding(embeddingStr);
        
        kb = knowledgeBaseRepository.save(kb);
        return mapToResponse(kb);
    }
    
    private String convertEmbeddingToString(float[] array) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < array.length; i++) {
            if (i > 0) sb.append(",");
            sb.append(array[i]);
        }
        sb.append("]");
        return sb.toString();
    }
    
    @Transactional
    public void delete(Long id) {
        if (!knowledgeBaseRepository.existsById(id)) {
            throw new RuntimeException("Knowledge base entry not found");
        }
        knowledgeBaseRepository.deleteById(id);
    }
    
    public Page<KnowledgeBaseResponse> search(Long projectId, String query, Pageable pageable) {
        return knowledgeBaseRepository.searchByKeyword(projectId, query, pageable)
                .map(this::mapToResponse);
    }
    
    private KnowledgeBaseResponse mapToResponse(KnowledgeBase kb) {
        return KnowledgeBaseResponse.builder()
                .id(kb.getId())
                .projectId(kb.getProject().getId())
                .title(kb.getTitle())
                .content(kb.getContent())
                .sourceType(kb.getSourceType())
                .sourceUrl(kb.getSourceUrl())
                .createdAt(kb.getCreatedAt())
                .build();
    }
}
