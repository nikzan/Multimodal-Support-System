package com.nova.support.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * Сервис для работы с Ollama (локальные LLM модели)
 */
@Slf4j
@Service
public class OllamaService {
    
    private final WebClient webClient;
    
    @Value("${ollama.model.chat}")
    private String chatModel;
    
    @Value("${ollama.model.embedding}")
    private String embeddingModel;
    
    public OllamaService(@Value("${ollama.base-url}") String ollamaBaseUrl) {
        this.webClient = WebClient.builder()
                .baseUrl(ollamaBaseUrl)
                .build();
    }
    
    /**
     * Генерация текста через LLM
     */
    public String generateText(String prompt) {
        try {
            log.info("Generating text with model: {}", chatModel);
            
            Map<String, Object> request = Map.of(
                "model", chatModel,
                "prompt", prompt,
                "stream", false
            );
            
            Map<String, Object> response = webClient.post()
                    .uri("/api/generate")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();
            
            if (response != null && response.containsKey("response")) {
                return (String) response.get("response");
            }
            
            throw new RuntimeException("Empty response from Ollama");
            
        } catch (Exception e) {
            log.error("Error generating text", e);
            throw new RuntimeException("Failed to generate text: " + e.getMessage(), e);
        }
    }
    
    /**
     * Анализ изображения через мультимодальную модель (gemma3:4b)
     */
    public String analyzeImage(byte[] imageBytes, String prompt) {
        try {
            log.info("Analyzing image with model: {}", chatModel);
            
            String base64Image = Base64.getEncoder().encodeToString(imageBytes);
            
            Map<String, Object> request = Map.of(
                "model", chatModel,
                "prompt", prompt,
                "images", List.of(base64Image),
                "stream", false
            );
            
            Map<String, Object> response = webClient.post()
                    .uri("/api/generate")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();
            
            if (response != null && response.containsKey("response")) {
                return (String) response.get("response");
            }
            
            throw new RuntimeException("Empty response from Ollama");
            
        } catch (Exception e) {
            log.error("Error analyzing image", e);
            throw new RuntimeException("Failed to analyze image: " + e.getMessage(), e);
        }
    }
    
    /**
     * Генерация краткого резюме текста
     */
    public String generateSummary(String text) {
        String prompt = String.format(
            "Кратко резюмируй следующий текст обращения клиента в 2-3 предложениях:\n\n%s",
            text
        );
        return generateText(prompt);
    }
    
    /**
     * Анализ тональности текста
     */
    public String analyzeSentiment(String text) {
        String prompt = String.format(
            "Определи тональность следующего обращения клиента (positive/neutral/negative):\n\n%s\n\n" +
            "Ответь одним словом: positive, neutral или negative",
            text
        );
        return generateText(prompt);
    }
    
    /**
     * Генерация эмбеддинга текста
     */
    public float[] generateEmbedding(String text) {
        try {
            log.info("Generating embedding with model: {}", embeddingModel);
            
            Map<String, Object> request = Map.of(
                "model", embeddingModel,
                "prompt", text
            );
            
            Map<String, Object> response = webClient.post()
                    .uri("/api/embeddings")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();
            
            if (response != null && response.containsKey("embedding")) {
                List<Double> embeddingList = (List<Double>) response.get("embedding");
                float[] embedding = new float[embeddingList.size()];
                for (int i = 0; i < embeddingList.size(); i++) {
                    embedding[i] = embeddingList.get(i).floatValue();
                }
                return embedding;
            }
            
            throw new RuntimeException("Empty embedding response from Ollama");
            
        } catch (Exception e) {
            log.error("Error generating embedding", e);
            throw new RuntimeException("Failed to generate embedding: " + e.getMessage(), e);
        }
    }
}
