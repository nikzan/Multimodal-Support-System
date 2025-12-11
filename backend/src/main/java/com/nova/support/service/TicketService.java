package com.nova.support.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nova.support.domain.entity.KnowledgeBase;
import com.nova.support.domain.entity.Project;
import com.nova.support.domain.entity.Ticket;
import com.nova.support.domain.entity.ChatMessage;
import com.nova.support.domain.entity.ChatMessage.SenderType;
import com.nova.support.domain.enums.Priority;
import com.nova.support.domain.enums.Sentiment;
import com.nova.support.domain.enums.TicketStatus;
import com.nova.support.repository.KnowledgeBaseRepository;
import com.nova.support.repository.ProjectRepository;
import com.nova.support.repository.TicketRepository;
import com.nova.support.repository.ChatMessageRepository;
import com.nova.support.dto.TicketRequest;
import com.nova.support.dto.TicketResponse;
import com.nova.support.dto.RagAnswerResponse;
import com.nova.support.dto.ChatMessageResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class TicketService {
    
    private final TicketRepository ticketRepository;
    private final ProjectRepository projectRepository;
    private final KnowledgeBaseRepository knowledgeBaseRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final WhisperService whisperService;
    private final OllamaService ollamaService;
    private final MinioService minioService;
    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectMapper objectMapper;
    
    @Transactional
    public TicketResponse processTicket(TicketRequest request) {
        // 1. Найти проект по API ключу
        Project project = projectRepository.findByApiKey(request.getProjectApiKey())
                .orElseThrow(() -> new RuntimeException("Invalid API key"));
        
        Ticket ticket = new Ticket();
        ticket.setProject(project);
        ticket.setStatus(TicketStatus.OPEN);
        ticket.setIsClosed(false);
        
        // Установить session ID (из запроса или сгенерировать новый)
        if (request.getSessionId() != null && !request.getSessionId().isEmpty()) {
            ticket.setSessionId(request.getSessionId());
        } else {
            ticket.setSessionId(java.util.UUID.randomUUID().toString());
        }
        
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
                
                // Транскрибировать через Whisper (только если файл больше 1KB)
                if (audioBytes.length > 1024) {
                    try {
                        String transcription = whisperService.transcribe(audioBytes, request.getLanguage());
                        if (transcription != null && !transcription.trim().isEmpty()) {
                            ticket.setTranscribedText(transcription);
                            fullText += " " + transcription;
                            log.info("Audio transcribed successfully: {} bytes -> {} chars", audioBytes.length, transcription.length());
                        }
                    } catch (Exception whisperError) {
                        log.warn("Whisper transcription failed, continuing without transcription: {}", whisperError.getMessage());
                        // Продолжаем без транскрипции, не прерываем создание тикета
                    }
                } else {
                    log.warn("Audio file too small for transcription: {} bytes", audioBytes.length);
                }
                
            } catch (Exception e) {
                log.error("Failed to process audio", e);
                // Не прерываем создание тикета из-за ошибки аудио
            }
        }
        
        // 4. Обработка изображения (если есть)
        String imageDescription = null;
        if (request.getImageBase64() != null && !request.getImageBase64().isEmpty()) {
            try {
                byte[] imageBytes = Base64.getDecoder().decode(request.getImageBase64());
                
                // Сохранить изображение в MinIO
                String imageUrl = minioService.uploadFile(imageBytes, "image.png", "image/png");
                ticket.setImageUrl(imageUrl);
                
                // Описать изображение через Ollama (gemma3:4b поддерживает vision)
                imageDescription = ollamaService.analyzeImage(imageBytes, 
                    "Опиши что изображено на этой картинке. Это скриншот или фото проблемы пользователя.");
                // НЕ добавляем в fullText - будет в metadata
                
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
            
            // RAG: поиск подходящего ответа из базы знаний (первый ответ - нужно приветствие)
            String suggestedAnswer = findSuggestedAnswer(project.getId(), fullText, true);
            ticket.setSuggestedAnswer(suggestedAnswer);
        }
        
        // 6. Сохранить тикет
        ticket = ticketRepository.save(ticket);
        
        // 7. Создать первое сообщение клиента в чате
        if (!fullText.isEmpty()) {
            ChatMessage firstMessage = new ChatMessage();
            firstMessage.setTicketId(ticket.getId());
            firstMessage.setSenderType(ChatMessage.SenderType.CLIENT);
            firstMessage.setMessage(fullText);
            firstMessage.setImageUrl(ticket.getImageUrl());
            firstMessage.setAudioUrl(ticket.getAudioUrl());
            
            // Сохранить транскрипцию и описание изображения в metadata
            if ((ticket.getTranscribedText() != null && !ticket.getTranscribedText().isEmpty()) || imageDescription != null) {
                try {
                    java.util.Map<String, String> metadataMap = new java.util.HashMap<>();
                    if (ticket.getTranscribedText() != null && !ticket.getTranscribedText().isEmpty()) {
                        metadataMap.put("transcription", ticket.getTranscribedText());
                    }
                    if (imageDescription != null) {
                        metadataMap.put("imageDescription", imageDescription);
                    }
                    String metadata = objectMapper.writeValueAsString(metadataMap);
                    firstMessage.setMetadata(metadata);
                } catch (JsonProcessingException e) {
                    log.error("Failed to serialize metadata", e);
                }
            }
            
            firstMessage = chatMessageRepository.save(firstMessage);
            
            // Добавить первое сообщение в RAG bucket
            addMessageToBucket(ticket.getId(), firstMessage.getId());
            
            log.info("Created first chat message for ticket {}", ticket.getId());
        }
        
        // 8. Отправить WebSocket уведомление
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
        
        // Сначала закрываем тикет (отправляется прощальное сообщение)
        Ticket ticket = ticketRepository.findById(id).orElseThrow();
        if (!ticket.getIsClosed()) {
            closeTicket(id);
        }
        
        // Затем удаляем
        ticketRepository.deleteById(id);
        log.info("Deleted ticket: {}", id);
    }
    
    /**
     * Найти активный тикет по session ID
     */
    public TicketResponse getActiveTicketBySession(String sessionId) {
        List<Ticket> tickets = ticketRepository.findActiveTicketsBySessionId(sessionId);
        if (tickets.isEmpty()) {
            return null;
        }
        return mapToResponse(tickets.get(0));
    }
    
    /**
     * Закрыть тикет
     */
    @Transactional
    public TicketResponse closeTicket(Long id) {
        Ticket ticket = ticketRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Ticket not found"));
        
        ticket.setIsClosed(true);
        ticket.setStatus(TicketStatus.CLOSED);
        ticket = ticketRepository.save(ticket);
        
        log.info("Closed ticket: {}", id);
        
        // Отправить прощальное сообщение клиенту
        ChatMessage farewellMessage = new ChatMessage();
        farewellMessage.setTicketId(id);
        farewellMessage.setSenderType(SenderType.OPERATOR);
        farewellMessage.setSenderName("Служба поддержки");
        farewellMessage.setMessage(
            "Спасибо за обращение!. "
            + "Если у вас возникнут ещё вопросы, мы всегда рады помочь. "
            + "До свидания! 😊"
        );
        ChatMessage savedFarewell = chatMessageRepository.save(farewellMessage);
        
        // Отправить прощальное сообщение через WebSocket
        messagingTemplate.convertAndSend(
            "/topic/tickets/" + id + "/messages", 
            ChatMessageResponse.from(savedFarewell)
        );
        
        // Отправить WebSocket уведомление о закрытии
        TicketResponse response = mapToResponse(ticket);
        messagingTemplate.convertAndSend("/topic/tickets/" + id + "/closed", response);
        
        return response;
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
        return findSuggestedAnswer(projectId, queryText, false);
    }
    
    private String findSuggestedAnswer(Long projectId, String queryText, boolean isFirstResponse) {
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
            String greetingInstruction = isFirstResponse 
                ? "- Начни ответ с вежливого приветствия\n"
                : "- НЕ используй приветствие, так как это продолжение диалога\n";
            
            String prompt = String.format(
                "Ты - ассистент службы поддержки. Используй ТОЛЬКО информацию из базы знаний ниже для ответа.\n\n" +
                "База знаний:\n%s\n\n" +
                "Вопрос клиента: %s\n\n" +
                "ВАЖНО:\n" +
                "- Отвечай ТОЛЬКО на основе предоставленной информации\n" +
                "- Если в базе знаний НЕТ точного ответа на вопрос, скажи: 'К сожалению, у меня нет информации по этому вопросу'\n" +
                "- НЕ додумывай и НЕ добавляй информацию, которой нет в базе знаний\n" +
                "- Будь точным, очень вежливым и тактичным\n" +
                "- Используй дружелюбный и профессиональный тон общения\n" +
                "%s" +
                "Ответ:",
                context.toString(), queryText, greetingInstruction
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
                .sessionId(ticket.getSessionId())
                .originalText(ticket.getOriginalText())
                .transcribedText(ticket.getTranscribedText())
                .aiSummary(ticket.getAiSummary())
                .sentiment(ticket.getSentiment())
                .sentimentScore(ticket.getSentimentScore() != null ? 
                        ticket.getSentimentScore().doubleValue() : null)
                .priority(ticket.getPriority())
                .suggestedAnswer(ticket.getSuggestedAnswer())
                .status(ticket.getStatus())
                .isClosed(ticket.getIsClosed())
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
    
    // ===== RAG Bucket Methods =====
    
    /**
     * Добавить сообщение в RAG bucket
     */
    public void addMessageToBucket(Long ticketId, Long messageId) {
        Ticket ticket = ticketRepository.findById(ticketId)
            .orElseThrow(() -> new RuntimeException("Ticket not found: " + ticketId));
        
        String currentBucket = ticket.getRagBucketMessageIds();
        if (currentBucket == null || currentBucket.isEmpty()) {
            ticket.setRagBucketMessageIds(String.valueOf(messageId));
        } else {
            ticket.setRagBucketMessageIds(currentBucket + "," + messageId);
        }
        
        ticketRepository.save(ticket);
        log.info("Added message {} to RAG bucket for ticket {}", messageId, ticketId);
        
        // Send WebSocket notification that RAG needs update
        messagingTemplate.convertAndSend(
            "/topic/tickets/" + ticketId + "/rag-updated",
            "RAG answer needs refresh"
        );
    }
    
    /**
     * Очистить RAG bucket (после ответа оператора)
     */
    public void clearBucket(Long ticketId) {
        Ticket ticket = ticketRepository.findById(ticketId)
            .orElseThrow(() -> new RuntimeException("Ticket not found: " + ticketId));
        
        ticket.setRagBucketMessageIds(null);
        ticket.setLastOperatorResponseAt(LocalDateTime.now());
        ticketRepository.save(ticket);
        
        log.info("Cleared RAG bucket for ticket {}", ticketId);
    }
    
    /**
     * Генерация RAG ответа для accumulated messages
     */
    public RagAnswerResponse generateRagAnswer(Long ticketId) {
        Ticket ticket = ticketRepository.findById(ticketId)
            .orElseThrow(() -> new RuntimeException("Ticket not found: " + ticketId));
        
        String bucketIds = ticket.getRagBucketMessageIds();
        if (bucketIds == null || bucketIds.isEmpty()) {
            return RagAnswerResponse.builder()
                .answer("Нет новых сообщений для анализа")
                .messagesCount(0)
                .lastUpdated(LocalDateTime.now())
                .messageIds("")
                .build();
        }
        
        // Parse message IDs
        List<Long> messageIds = Arrays.stream(bucketIds.split(","))
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .map(Long::parseLong)
            .collect(Collectors.toList());
        
        // Load messages
        List<ChatMessage> messages = chatMessageRepository.findAllById(messageIds);
        
        if (messages.isEmpty()) {
            return RagAnswerResponse.builder()
                .answer("Сообщения не найдены")
                .messagesCount(0)
                .lastUpdated(LocalDateTime.now())
                .messageIds(bucketIds)
                .build();
        }
        
        // Build context from messages
        StringBuilder context = new StringBuilder();
        for (ChatMessage msg : messages) {
            context.append("Клиент: ").append(msg.getMessage()).append("\n");
            
            // Add transcription/description if exists in metadata
            if (msg.getMetadata() != null && !msg.getMetadata().isEmpty()) {
                try {
                    JsonNode metadata = objectMapper.readTree(msg.getMetadata());
                    if (metadata.has("transcription")) {
                        context.append("(Транскрипция аудио: ")
                               .append(metadata.get("transcription").asText())
                               .append(")\n");
                    }
                    if (metadata.has("imageDescription")) {
                        context.append("(Описание изображения: ")
                               .append(metadata.get("imageDescription").asText())
                               .append(")\n");
                    }
                } catch (JsonProcessingException e) {
                    log.warn("Failed to parse metadata for message {}", msg.getId(), e);
                }
            }
        }
        
        // Проверяем есть ли уже ответы от оператора в тикете
        boolean hasOperatorResponses = chatMessageRepository.existsByTicketIdAndSenderType(
            ticketId, 
            SenderType.OPERATOR
        );
        
        // Search knowledge base for context
        String kbContext = findSuggestedAnswer(
            ticket.getProject().getId(), 
            context.toString(),
            !hasOperatorResponses // Если оператор ещё не отвечал - это первый ответ
        );
        
        // Use existing suggested answer or generate new one
        String ragAnswer = kbContext != null && !kbContext.isEmpty() 
            ? kbContext 
            : "На основе новых сообщений рекомендуем проверить статус запроса клиента.";
        
        return RagAnswerResponse.builder()
            .answer(ragAnswer)
            .messagesCount(messages.size())
            .lastUpdated(LocalDateTime.now())
            .messageIds(bucketIds)
            .build();
    }
}

