package com.nova.support.controller;

import com.nova.support.service.MinioService;
import com.nova.support.service.WhisperService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/upload")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class UploadController {

    private final MinioService minioService;
    private final WhisperService whisperService;

    @PostMapping
    public ResponseEntity<Map<String, String>> uploadFile(@RequestParam("file") MultipartFile file) {
        try {
            String url = minioService.uploadFile(file, "chat-attachments");
            
            Map<String, String> response = new HashMap<>();
            response.put("url", url);
            
            // Если это аудио файл - транскрибировать
            if (file.getContentType() != null && file.getContentType().startsWith("audio/")) {
                try {
                    byte[] audioBytes = file.getBytes();
                    String transcription = whisperService.transcribe(audioBytes, null);
                    response.put("transcription", transcription);
                    log.info("Audio transcribed: {}", transcription);
                } catch (Exception e) {
                    log.error("Failed to transcribe audio", e);
                    // Не прерываем загрузку, просто не возвращаем транскрипцию
                }
            }
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Failed to upload file", e);
            return ResponseEntity.badRequest().build();
        }
    }
}
