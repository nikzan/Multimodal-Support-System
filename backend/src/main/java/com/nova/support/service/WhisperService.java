package com.nova.support.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

/**
 * Сервис для взаимодействия с Whisper микросервисом (транскрибация аудио)
 */
@Slf4j
@Service
public class WhisperService {

    private final WebClient webClient;

    public WhisperService(@Value("${whisper.service-url}") String whisperServiceUrl) {
        this.webClient = WebClient.builder()
                .baseUrl(whisperServiceUrl)
                .build();
    }

    /**
     * Транскрибировать аудио из byte array
     * 
     * @param audioBytes аудио данные
     * @param language язык аудио (ru, en, auto)
     * @return транскрибированный текст
     */
    public String transcribe(byte[] audioBytes, String language) {
        try {
            return transcribe(new ByteArrayInputStream(audioBytes), "audio.webm", language);
        } catch (Exception e) {
            log.error("Error during transcription from bytes", e);
            throw new RuntimeException("Failed to transcribe audio: " + e.getMessage(), e);
        }
    }
    
    /**
     * Транскрибировать аудио файл в текст
     * 
     * @param audioStream поток аудио данных
     * @param filename имя файла (для определения формата)
     * @param language язык аудио (ru, en, auto)
     * @return транскрибированный текст
     */
    public String transcribe(InputStream audioStream, String filename, String language) {
        try {
            log.info("Sending audio to Whisper service: filename={}, language={}", filename, language);
            
            byte[] audioBytes = audioStream.readAllBytes();
            
            MultipartBodyBuilder builder = new MultipartBodyBuilder();
            builder.part("audio", new ByteArrayResource(audioBytes) {
                @Override
                public String getFilename() {
                    return filename;
                }
            });
            
            if (language != null && !language.isEmpty() && !language.equalsIgnoreCase("auto")) {
                builder.part("language", language);
            }
            
            TranscriptionResponse response = webClient.post()
                    .uri("/transcribe")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(BodyInserters.fromMultipartData(builder.build()))
                    .retrieve()
                    .bodyToMono(TranscriptionResponse.class)
                    .block();
            
            if (response != null && response.text() != null) {
                log.info("Transcription successful: language={}, segments={}", 
                        response.language(), response.segments());
                return response.text();
            }
            
            throw new RuntimeException("Empty response from Whisper service");
            
        } catch (Exception e) {
            log.error("Error during transcription", e);
            throw new RuntimeException("Failed to transcribe audio: " + e.getMessage(), e);
        }
    }
    
    /**
     * Проверить здоровье Whisper сервиса
     */
    public boolean isHealthy() {
        try {
            HealthResponse response = webClient.get()
                    .uri("/health")
                    .retrieve()
                    .bodyToMono(HealthResponse.class)
                    .block();
            
            return response != null && "healthy".equals(response.status());
        } catch (Exception e) {
            log.error("Whisper service health check failed", e);
            return false;
        }
    }
    
    // Response DTOs
    private record TranscriptionResponse(String text, String language, Integer segments) {}
    private record HealthResponse(String status, String service, String model) {}
}
