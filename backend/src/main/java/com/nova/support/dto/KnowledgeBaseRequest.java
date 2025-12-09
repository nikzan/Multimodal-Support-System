package com.nova.support.dto;

import lombok.Data;

@Data
public class KnowledgeBaseRequest {
    private Long projectId;
    private String title;
    private String content;
    private String sourceType;  // MANUAL, IMPORT, AUTO
    private String sourceUrl;
}
