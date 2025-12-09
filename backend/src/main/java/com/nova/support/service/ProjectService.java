package com.nova.support.service;

import com.nova.support.domain.entity.Project;
import com.nova.support.dto.ProjectRequest;
import com.nova.support.dto.ProjectResponse;
import com.nova.support.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProjectService {
    
    private final ProjectRepository projectRepository;
    
    public Page<ProjectResponse> getAllProjects(Pageable pageable) {
        return projectRepository.findAll(pageable)
                .map(this::mapToResponse);
    }
    
    public ProjectResponse getProject(Long id) {
        Project project = projectRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Project not found"));
        return mapToResponse(project);
    }
    
    @Transactional
    public ProjectResponse createProject(ProjectRequest request) {
        Project project = new Project();
        project.setName(request.getName());
        project.setWebsiteUrl(request.getWebsiteUrl());
        project.setApiKey(generateApiKey());
        
        project = projectRepository.save(project);
        log.info("Created project: {} with API key: {}", project.getName(), project.getApiKey());
        
        return mapToResponse(project);
    }
    
    @Transactional
    public ProjectResponse updateProject(Long id, ProjectRequest request) {
        Project project = projectRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Project not found"));
        
        project.setName(request.getName());
        project.setWebsiteUrl(request.getWebsiteUrl());
        
        project = projectRepository.save(project);
        return mapToResponse(project);
    }
    
    @Transactional
    public void deleteProject(Long id) {
        if (!projectRepository.existsById(id)) {
            throw new RuntimeException("Project not found");
        }
        projectRepository.deleteById(id);
    }
    
    @Transactional
    public ProjectResponse regenerateApiKey(Long id) {
        Project project = projectRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Project not found"));
        
        String oldKey = project.getApiKey();
        project.setApiKey(generateApiKey());
        
        project = projectRepository.save(project);
        log.info("Regenerated API key for project {}: {} -> {}", 
                project.getName(), oldKey, project.getApiKey());
        
        return mapToResponse(project);
    }
    
    private String generateApiKey() {
        return "sk-" + UUID.randomUUID().toString().replace("-", "");
    }
    
    private ProjectResponse mapToResponse(Project project) {
        return ProjectResponse.builder()
                .id(project.getId())
                .name(project.getName())
                .apiKey(project.getApiKey())
                .websiteUrl(project.getWebsiteUrl())
                .createdAt(project.getCreatedAt())
                .build();
    }
}
