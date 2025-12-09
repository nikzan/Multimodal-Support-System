package com.nova.support.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Сервис для работы с AI моделями через Ollama
 * - Анализ тикетов (sentiment, summary)
 * - Генерация эмбеддингов для RAG
 * - Анализ изображений (через gemma3:4b - мультимодальная модель)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiService {

    private final ChatClient.Builder chatClientBuilder;
    private final EmbeddingModel embeddingModel;

    /**
     * Анализ тикета: определение тональности и приоритета
     * 
     * @param text текст тикета (оригинальный или транскрибированный)
     * @return результат анализа
     */
    public TicketAnalysis analyzeTicket(String text) {
        log.info("Analyzing ticket text (length: {})", text.length());
        
        String prompt = String.format("""
                Проанализируй следующее обращение клиента в службу поддержки.
                
                Текст обращения:
                %s
                
                Определи:
                1. Тональность (POSITIVE, NEUTRAL, NEGATIVE)
                2. Приоритет (LOW, MEDIUM, HIGH, CRITICAL)
                3. Краткое резюме проблемы (1-2 предложения)
                
                Ответь СТРОГО в формате JSON:
                {
                  "sentiment": "POSITIVE|NEUTRAL|NEGATIVE",
                  "priority": "LOW|MEDIUM|HIGH|CRITICAL",
                  "summary": "краткое описание проблемы"
                }
                """, text);
        
        try {
            String response = chatClientBuilder.build()
                    .prompt(new Prompt(prompt))
                    .call()
                    .content();
            
            log.debug("AI analysis response: {}", response);
            
            // Парсим JSON ответ (упрощенная версия, в продакшене использовать Jackson)
            return parseAnalysisResponse(response);
            
        } catch (Exception e) {
            log.error("Error during ticket analysis", e);
            // Fallback значения
            return new TicketAnalysis("NEUTRAL", "MEDIUM", "Не удалось проанализировать тикет");
        }
    }
    
    /**
     * Генерация краткого резюме текста
     */
    public String generateSummary(String text) {
        log.info("Generating summary for text (length: {})", text.length());
        
        String prompt = String.format("""
                Создай краткое резюме (1-2 предложения) следующего обращения клиента:
                
                %s
                
                Резюме должно содержать суть проблемы или вопроса.
                """, text);
        
        try {
            return chatClientBuilder.build()
                    .prompt(new Prompt(prompt))
                    .call()
                    .content()
                    .trim();
        } catch (Exception e) {
            log.error("Error generating summary", e);
            return "Не удалось создать резюме";
        }
    }
    
    /**
     * Генерация эмбеддинга для текста (для RAG поиска)
     * nomic-embed-text создает вектор размерности 768
     */
    public List<Double> generateEmbedding(String text) {
        log.info("Generating embedding for text (length: {})", text.length());
        
        try {
            EmbeddingResponse response = embeddingModel.embedForResponse(List.of(text));
            
            if (response != null && !response.getResults().isEmpty()) {
                float[] floatVector = response.getResults().get(0).getOutput();
                
                // Конвертируем float[] в List<Double> для PostgreSQL vector
                List<Double> embedding = new java.util.ArrayList<>(floatVector.length);
                for (float v : floatVector) {
                    embedding.add((double) v);
                }
                return embedding;
            }
            
            throw new RuntimeException("Empty embedding response");
            
        } catch (Exception e) {
            log.error("Error generating embedding", e);
            throw new RuntimeException("Failed to generate embedding: " + e.getMessage(), e);
        }
    }
    
    /**
     * Анализ изображения (описание проблемы на скриншоте)
     * gemma3:4b - мультимодальная модель
     */
    public String analyzeImage(byte[] imageData, String additionalContext) {
        log.info("Analyzing image (size: {} bytes)", imageData.length);
        
        // TODO: Реализовать когда Spring AI добавит поддержку мультимодальных запросов к Ollama
        // Пока возвращаем заглушку
        return "Анализ изображений будет добавлен в следующей версии";
    }
    
    // Вспомогательные методы
    
    private TicketAnalysis parseAnalysisResponse(String jsonResponse) {
        // Простой парсинг JSON (в продакшене использовать Jackson ObjectMapper)
        String sentiment = extractJsonValue(jsonResponse, "sentiment");
        String priority = extractJsonValue(jsonResponse, "priority");
        String summary = extractJsonValue(jsonResponse, "summary");
        
        return new TicketAnalysis(sentiment, priority, summary);
    }
    
    private String extractJsonValue(String json, String key) {
        try {
            String pattern = "\"" + key + "\"\\s*:\\s*\"([^\"]+)\"";
            java.util.regex.Pattern p = java.util.regex.Pattern.compile(pattern);
            java.util.regex.Matcher m = p.matcher(json);
            if (m.find()) {
                return m.group(1);
            }
        } catch (Exception e) {
            log.warn("Failed to extract '{}' from JSON: {}", key, json);
        }
        return "";
    }
    
    /**
     * Результат анализа тикета
     */
    public record TicketAnalysis(
            String sentiment,
            String priority,
            String summary
    ) {}
}
