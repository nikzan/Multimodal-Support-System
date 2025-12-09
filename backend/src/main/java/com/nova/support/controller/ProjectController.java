package com.nova.support.controller;

import com.nova.support.dto.ProjectRequest;
import com.nova.support.dto.ProjectResponse;
import com.nova.support.service.ProjectService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/admin/projects")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class ProjectController {
    
    private final ProjectService projectService;
    
    @GetMapping
    public ResponseEntity<Page<ProjectResponse>> getAllProjects(Pageable pageable) {
        Page<ProjectResponse> projects = projectService.getAllProjects(pageable);
        return ResponseEntity.ok(projects);
    }
    
    @GetMapping("/{id}")
    public ResponseEntity<ProjectResponse> getProject(@PathVariable Long id) {
        ProjectResponse project = projectService.getProject(id);
        return ResponseEntity.ok(project);
    }
    
    @PostMapping
    public ResponseEntity<ProjectResponse> createProject(@RequestBody ProjectRequest request) {
        log.info("Creating new project: {}", request.getName());
        ProjectResponse project = projectService.createProject(request);
        return ResponseEntity.ok(project);
    }
    
    @PutMapping("/{id}")
    public ResponseEntity<ProjectResponse> updateProject(
            @PathVariable Long id,
            @RequestBody ProjectRequest request) {
        log.info("Updating project {}: {}", id, request.getName());
        ProjectResponse project = projectService.updateProject(id, request);
        return ResponseEntity.ok(project);
    }
    
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteProject(@PathVariable Long id) {
        log.info("Deleting project: {}", id);
        projectService.deleteProject(id);
        return ResponseEntity.noContent().build();
    }
    
    @PostMapping("/{id}/regenerate-api-key")
    public ResponseEntity<ProjectResponse> regenerateApiKey(@PathVariable Long id) {
        log.info("Regenerating API key for project: {}", id);
        ProjectResponse project = projectService.regenerateApiKey(id);
        return ResponseEntity.ok(project);
    }
}
