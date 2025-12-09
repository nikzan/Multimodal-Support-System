package com.nova.support.service;

import com.nova.support.domain.entity.KnowledgeBase;
import com.nova.support.domain.entity.Project;
import com.nova.support.domain.entity.Ticket;
import com.nova.support.domain.enums.Priority;
import com.nova.support.domain.enums.Sentiment;
import com.nova.support.domain.enums.TicketStatus;
import com.nova.support.repository.KnowledgeBaseRepository;
import com.nova.support.repository.ProjectRepository;
import com.nova.support.repository.TicketRepository;
import com.nova.support.dto.TicketRequest;
import com.nova.support.dto.TicketResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Base64;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class TicketService {
    
    private final TicketRepository ticketRepository;
    private final ProjectRepository projectRepository;
    private final KnowledgeBaseRepository knowledgeBaseRepository;
    private final WhisperService whisperService;
    private final OllamaService ollamaService;
    private final MinioService minioService;
    private final SimpMessagingTemplate messagingTemplate;
    
    @Transactional
    public TicketResponse processTicket(TicketRequest request) {
        // 1. Найти проект по API ключу
        Project project = projectRepository.findByApiKey(request.getProjectApiKey())
                .orElseThrow(() -> new RuntimeException("Invalid API key"));
        
        Ticket ticket = new Ticket();
        ticket.setProject(project);
        ticket.setStatus(TicketStatus.OPEN);
        
        String fullText = "";
        
        // 2. Обработка текста
        if (request.getText() != null && !request.getText().isEmpty()) {
            ticket.setOriginalText(request.getText());
            fullText = request.getText();
        }
        
        // 3. Обработка аудио (если есть)
        if (request.getAudioBase64() != null && !request.getAudioBase64().isEmpty()) {
            try {
                byte[] audioBytes = Base64.getDecoder().decode(request.getAudioBase64());
                
                // Сохранить аудио в MinIO
                String audioUrl = minioService.uploadFile(audioBytes, "audio.webm", "audio/webm");
                ticket.setAudioUrl(audioUrl);
                
                // Транскрибировать через Whisper
                String transcription = whisperService.transcribe(audioBytes, request.getLanguage());
                ticket.setTranscribedText(transcription);
                fullText += " " + transcription;
                
                log.info("Audio transcribed: {}", transcription);
            } catch (Exception e) {
                log.error("Failed to process audio", e);
            }
        }
        
        // 4. Обработка изображения (если есть)
        if (request.getImageBase64() != null && !request.getImageBase64().isEmpty()) {
            try {
                byte[] imageBytes = Base64.getDecoder().decode(request.getImageBase64());
                
                // Сохранить изображение в MinIO
                String imageUrl = minioService.uploadFile(imageBytes, "image.png", "image/png");
                ticket.setImageUrl(imageUrl);
                
                // Описать изображение через Ollama (gemma3:4b поддерживает vision)
                String imageDescription = ollamaService.analyzeImage(imageBytes, 
                    "Опиши что изображено на этой картинке. Это скриншот или фото проблемы пользователя.");
                fullText += " [Изображение: " + imageDescription + "]";
                
                log.info("Image analyzed: {}", imageDescription);
            } catch (Exception e) {
                log.error("Failed to process image", e);
            }
        }
        
        fullText = fullText.trim();
        
        // 5. Анализ тикета через AI
        if (!fullText.isEmpty()) {
            // Генерация summary
            String summary = ollamaService.generateSummary(fullText);
            ticket.setAiSummary(summary);
            
            // Анализ sentiment
            String sentimentAnalysis = ollamaService.analyzeSentiment(fullText);
            parseSentiment(ticket, sentimentAnalysis);
            
            // Определение приоритета
            Priority priority = determinePriority(ticket.getSentiment(), fullText);
            ticket.setPriority(priority);
            
            // RAG: поиск подходящего ответа из базы знаний
            String suggestedAnswer = findSuggestedAnswer(project.getId(), fullText);
            ticket.setSuggestedAnswer(suggestedAnswer);
        }
        
        // 6. Сохранить тикет
        ticket = ticketRepository.save(ticket);
        
        // 7. Отправить WebSocket уведомление
        TicketResponse response = mapToResponse(ticket);
        messagingTemplate.convertAndSend("/topic/tickets/" + project.getId(), response);
        log.info("Sent WebSocket notification for ticket {} to project {}", ticket.getId(), project.getId());
        
        return response;
    }
    
    public TicketResponse getTicket(Long id) {
        Ticket ticket = ticketRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Ticket not found"));
        return mapToResponse(ticket);
    }
    
    public Page<TicketResponse> getTicketsByProject(Long projectId, Pageable pageable) {
        Page<Ticket> tickets = ticketRepository.findByProjectId(projectId, pageable);
        return tickets.map(this::mapToResponse);
    }
    
    @Transactional
    public TicketResponse updateStatus(Long id, String statusStr) {
        Ticket ticket = ticketRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Ticket not found"));
        
        TicketStatus status = TicketStatus.valueOf(statusStr.toUpperCase());
        ticket.setStatus(status);
        ticket = ticketRepository.save(ticket);
        
        log.info("Updated ticket {} status to {}", id, status);
        return mapToResponse(ticket);
    }
    
    @Transactional
    public void deleteTicket(Long id) {
        if (!ticketRepository.existsById(id)) {
            throw new RuntimeException("Ticket not found");
        }
        ticketRepository.deleteById(id);
        log.info("Deleted ticket: {}", id);
    }
    
    private void parseSentiment(Ticket ticket, String sentimentAnalysis) {
        String lower = sentimentAnalysis.toLowerCase();
        
        if (lower.contains("positive") || lower.contains("позитивный")) {
            ticket.setSentiment(Sentiment.POSITIVE);
            ticket.setSentimentScore(new BigDecimal("0.8"));
        } else if (lower.contains("negative") || lower.contains("негативный")) {
            ticket.setSentiment(Sentiment.NEGATIVE);
            ticket.setSentimentScore(new BigDecimal("-0.8"));
        } else {
            ticket.setSentiment(Sentiment.NEUTRAL);
            ticket.setSentimentScore(BigDecimal.ZERO);
        }
    }
    
    private Priority determinePriority(Sentiment sentiment, String text) {
        String lower = text.toLowerCase();
        
        // Критические слова
        if (lower.contains("urgent") || lower.contains("срочно") || 
            lower.contains("critical") || lower.contains("критично") ||
            lower.contains("не работает") || lower.contains("broken")) {
            return Priority.CRITICAL;
        }
        
        // Высокий приоритет для негативных тикетов
        if (sentiment == Sentiment.NEGATIVE) {
            return Priority.HIGH;
        }
        
        // Средний приоритет по умолчанию
        if (lower.contains("важно") || lower.contains("important")) {
            return Priority.MEDIUM;
        }
        
        return Priority.LOW;
    }
    
    private String findSuggestedAnswer(Long projectId, String queryText) {
        try {
            // Получить эмбеддинг вопроса
            float[] embeddingArray = ollamaService.generateEmbedding(queryText);
            String embeddingStr = convertEmbeddingToString(embeddingArray);
            
            // Найти похожие записи в базе знаний
            List<KnowledgeBase> similarKnowledge = knowledgeBaseRepository
                    .findSimilarByEmbedding(projectId, embeddingStr, 3);
            
            if (similarKnowledge.isEmpty()) {
                return "К сожалению, подходящего ответа в базе знаний не найдено.";
            }
            
            // Собрать контекст из базы знаний
            StringBuilder context = new StringBuilder();
            for (KnowledgeBase kb : similarKnowledge) {
                context.append(kb.getTitle()).append(": ").append(kb.getContent()).append("\n\n");
            }
            
            // Сгенерировать ответ на основе контекста
            String prompt = String.format(
                "На основе следующей информации из базы знаний:\n\n%s\n\n" +
                "Ответь на вопрос пользователя: %s\n\n" +
                "Дай краткий и понятный ответ.",
                context.toString(), queryText
            );
            
            return ollamaService.generateText(prompt);
            
        } catch (Exception e) {
            log.error("Failed to find suggested answer", e);
            return "Ошибка при поиске ответа в базе знаний.";
        }
    }
    
    private TicketResponse mapToResponse(Ticket ticket) {
        return TicketResponse.builder()
                .id(ticket.getId())
                .originalText(ticket.getOriginalText())
                .transcribedText(ticket.getTranscribedText())
                .aiSummary(ticket.getAiSummary())
                .sentiment(ticket.getSentiment())
                .sentimentScore(ticket.getSentimentScore() != null ? 
                        ticket.getSentimentScore().doubleValue() : null)
                .priority(ticket.getPriority())
                .suggestedAnswer(ticket.getSuggestedAnswer())
                .status(ticket.getStatus())
                .audioUrl(ticket.getAudioUrl())
                .imageUrl(ticket.getImageUrl())
                .createdAt(ticket.getCreatedAt())
                .build();
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
}
