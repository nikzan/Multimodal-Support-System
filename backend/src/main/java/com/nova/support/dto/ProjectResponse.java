package com.nova.support.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class ProjectResponse {
    private Long id;
    private String name;
    private String apiKey;
    private String websiteUrl;
    private LocalDateTime createdAt;
}
