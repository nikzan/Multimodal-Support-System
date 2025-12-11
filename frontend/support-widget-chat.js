/**
 * Nova Support Widget - Chat Version
 * Real-time chat support with session persistence
 */
(function() {
    'use strict';

    class SupportWidget {
        constructor(config) {
            this.apiKey = config.apiKey;
            this.apiUrl = config.apiUrl || 'http://localhost:8080';
            this.wsUrl = config.wsUrl || 'http://localhost:8080/ws';
            this.position = config.position || 'bottom-right';
            this.primaryColor = config.primaryColor || '#667eea';
            
            // Chat state
            this.sessionId = this.getOrCreateSessionId();
            this.currentTicket = null;
            this.messages = [];
            this.isTicketClosed = false;
            
            // WebSocket
            this.stompClient = null;
            this.subscription = null;
            
            // Media recording
            this.mediaRecorder = null;
            this.audioChunks = [];
            this.isRecording = false;
            
            this.init();
        }

        init() {
            console.log('[Nova Widget] Initializing...');
            this.injectStyles();
            this.createWidget();
            this.attachEventListeners();
            this.loadActiveTicket();
            console.log('[Nova Widget] Initialized successfully');
        }

        getOrCreateSessionId() {
            let sessionId = localStorage.getItem('nova_support_session');
            if (!sessionId) {
                sessionId = this.generateUUID();
                localStorage.setItem('nova_support_session', sessionId);
            }
            return sessionId;
        }

        generateUUID() {
            return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, function(c) {
                const r = Math.random() * 16 | 0;
                const v = c === 'x' ? r : (r & 0x3 | 0x8);
                return v.toString(16);
            });
        }

        injectStyles() {
            const link = document.createElement('link');
            link.rel = 'stylesheet';
            link.href = 'support-widget-chat.css';
            document.head.appendChild(link);
        }

        createWidget() {
            console.log('[Nova Widget] Creating widget container...');
            const container = document.createElement('div');
            container.className = 'support-widget-container';
            container.innerHTML = `
                <button class="support-widget-button" id="supportWidgetBtn">
                    💬
                </button>
                
                <div class="support-widget-window" id="supportWidgetWindow">
                    <div class="support-widget-header">
                        <h3>Поддержка</h3>
                        <button class="support-widget-close" id="supportWidgetClose">×</button>
                    </div>
                    
                    <div class="support-widget-chat" id="chatArea">
                        <!-- Chat messages will be inserted here -->
                    </div>
                    
                    <div class="support-widget-status" id="ticketStatus" style="display: none;">
                        <span>Тикет закрыт</span>
                        <button class="btn-new-ticket" id="newTicketBtn">Создать новый тикет</button>
                    </div>
                    
                    <form class="support-widget-form" id="messageForm">
                        <textarea 
                            id="messageInput" 
                            placeholder="Опишите вашу проблему..."
                            rows="3"
                        ></textarea>
                        
                        <div class="support-widget-actions">
                            <div class="support-widget-upload-group">
                                <label for="imageUpload" class="support-widget-upload-label">
                                    📷 Фото
                                    <input type="file" id="imageUpload" accept="image/*" style="display: none;">
                                </label>
                                
                                <button 
                                    type="button" 
                                    class="support-widget-record-btn" 
                                    id="recordBtn"
                                    title="Запись голоса">
                                    🎤 Голос
                                </button>
                            </div>
                            
                            <button type="submit" class="support-widget-submit" id="sendBtn">
                                Отправить
                            </button>
                        </div>
                        
                        <div id="audioPreview" class="audio-preview" style="display: none;">
                            <audio controls id="audioPlayer"></audio>
                            <button class="remove-audio" id="removeAudio">×</button>
                        </div>
                        
                        <div id="imagePreview" class="image-preview" style="display: none;">
                            <img id="previewImg" alt="Preview">
                            <button class="remove-image" id="removeImage">×</button>
                        </div>
                    </form>
                </div>
            `;
            
            console.log('[Nova Widget] Appending container to body...');
            document.body.appendChild(container);
            console.log('[Nova Widget] Button should be visible now');
        }

        attachEventListeners() {
            const btn = document.getElementById('supportWidgetBtn');
            const closeBtn = document.getElementById('supportWidgetClose');
            const window = document.getElementById('supportWidgetWindow');
            const form = document.getElementById('messageForm');
            const sendBtn = document.getElementById('sendBtn');
            const recordBtn = document.getElementById('recordBtn');
            const imageUpload = document.getElementById('imageUpload');
            const removeAudio = document.getElementById('removeAudio');
            const removeImage = document.getElementById('removeImage');
            const newTicketBtn = document.getElementById('newTicketBtn');

            btn.addEventListener('click', () => {
                window.classList.add('active');
                this.scrollToBottom();
            });

            closeBtn.addEventListener('click', () => {
                window.classList.remove('active');
            });

            form.addEventListener('submit', (e) => {
                e.preventDefault();
                this.sendMessage();
            });
            
            sendBtn.addEventListener('click', (e) => {
                e.preventDefault();
                this.sendMessage();
            });

            recordBtn.addEventListener('click', () => this.toggleRecording());
            imageUpload.addEventListener('change', (e) => this.handleImageUpload(e));
            removeAudio.addEventListener('click', () => this.removeAudio());
            removeImage.addEventListener('click', () => this.removeImage());
            newTicketBtn.addEventListener('click', () => this.createNewTicket());
        }

        async loadActiveTicket() {
            try {
                const response = await fetch(`${this.apiUrl}/api/tickets/session/${this.sessionId}`);
                
                if (response.status === 404) {
                    // Нет активного тикета - показываем форму создания
                    this.showNewTicketForm();
                    return;
                }
                
                if (!response.ok) throw new Error('Failed to load ticket');
                
                const ticket = await response.json();
                this.currentTicket = ticket;
                this.isTicketClosed = ticket.isClosed;
                
                if (ticket.isClosed) {
                    this.showTicketClosed();
                } else {
                    await this.loadChatHistory(ticket.id);
                    this.connectWebSocket(ticket.id);
                }
            } catch (error) {
                console.error('Error loading ticket:', error);
                this.showNewTicketForm();
            }
        }

        async loadChatHistory(ticketId) {
            try {
                const response = await fetch(`${this.apiUrl}/api/tickets/${ticketId}/messages`);
                if (!response.ok) throw new Error('Failed to load chat history');
                
                const serverMessages = await response.json();
                
                // Удаляем оптимистичные сообщения, так как реальные пришли с сервера
                this.messages = this.messages.filter(m => !m._isOptimistic);
                
                // Добавляем сообщения с сервера, избегая дубликатов
                serverMessages.forEach(msg => {
                    const exists = this.messages.find(m => m.id === msg.id);
                    if (!exists) {
                        this.messages.push(msg);
                    }
                });
                
                this.renderMessages();
            } catch (error) {
                console.error('Error loading chat history:', error);
            }
        }

        connectWebSocket(ticketId) {
            const socket = new SockJS(this.wsUrl);
            this.stompClient = Stomp.over(socket);
            
            this.stompClient.connect({}, () => {
                console.log('WebSocket connected');
                
                // Загружаем историю сообщений чтобы заменить оптимистичные
                this.loadChatHistory(ticketId);
                
                // Подписка на новые сообщения
                this.subscription = this.stompClient.subscribe(
                    `/topic/tickets/${ticketId}/messages`,
                    (message) => {
                        const msg = JSON.parse(message.body);
                        this.handleNewMessage(msg);
                    }
                );
                
                // Подписка на закрытие тикета
                this.stompClient.subscribe(
                    `/topic/tickets/${ticketId}/closed`,
                    () => {
                        this.handleTicketClosed();
                    }
                );
            }, (error) => {
                console.error('WebSocket error:', error);
            });
        }

        handleNewMessage(message) {
            // Удаляем оптимистичное сообщение если пришло настоящее CLIENT сообщение
            if (message.senderType === 'CLIENT') {
                this.messages = this.messages.filter(m => !m._isOptimistic);
            }
            
            // Проверяем что сообщение ещё не добавлено
            const exists = this.messages.find(m => m.id === message.id);
            if (!exists) {
                this.messages.push(message);
                this.renderMessages();
                this.scrollToBottom();
            }
        }

        handleTicketClosed() {
            this.isTicketClosed = true;
            this.showTicketClosed();
            if (this.subscription) {
                this.subscription.unsubscribe();
            }
        }

        renderMessages() {
            const chatArea = document.getElementById('chatArea');
            
            if (this.messages.length === 0) {
                chatArea.innerHTML = '<div class="empty-chat">Начните переписку...</div>';
                return;
            }
            
            chatArea.innerHTML = this.messages.map(msg => {
                const isOperator = msg.senderType === 'OPERATOR';
                const messageClass = isOperator ? 'message-operator' : 'message-client';
                
                // Обработка временных вложений
                let imageHtml = '';
                let audioHtml = '';
                
                if (msg.imageUrl) {
                    if (msg.imageUrl === 'TEMP_IMAGE' && msg._tempImage) {
                        imageHtml = `<img src="${msg._tempImage}" alt="Attachment" style="opacity: 0.7;">`;
                    } else {
                        imageHtml = `<img src="http://localhost:9000/support-tickets/${msg.imageUrl}" alt="Attachment">`;
                    }
                }
                
                if (msg.audioUrl) {
                    if (msg.audioUrl === 'TEMP_AUDIO' && msg._tempAudio) {
                        const tempUrl = URL.createObjectURL(msg._tempAudio);
                        audioHtml = `<audio controls src="${tempUrl}" style="opacity: 0.7;"></audio>`;
                    } else {
                        audioHtml = `<audio controls src="http://localhost:9000/support-tickets/${msg.audioUrl}"></audio>`;
                    }
                }
                
                return `
                    <div class="chat-message ${messageClass}" data-msg-id="${msg.id || ''}">
                        <div class="message-content">
                            <p>${this.escapeHtml(msg.message)}</p>
                            ${imageHtml}
                            ${audioHtml}
                        </div>
                        <div class="message-time">${this.formatTime(msg.createdAt)}</div>
                    </div>
                `;
            }).join('');
        }

        async sendMessage() {
            console.log('sendMessage called');
            const input = document.getElementById('messageInput');
            const text = input.value.trim();
            
            console.log('Message text:', text);
            console.log('Current ticket:', this.currentTicket);
            
            if (!text && !this.recordedAudioBlob && !this.selectedImage) {
                alert('Введите сообщение или прикрепите файл');
                return;
            }
            
            try {
                // Если нет активного тикета - создаем новый
                if (!this.currentTicket) {
                    console.log('Creating new ticket...');
                    await this.createTicket(text);
                } else {
                    // Отправляем сообщение в существующий тикет
                    console.log('Sending message to existing ticket...');
                    await this.sendChatMessage(text);
                }
                
            } catch (error) {
                console.error('Error sending message:', error);
                this.showError('Не удалось отправить сообщение. Пожалуйста, попробуйте ещё раз.');
            }
        }

        async createTicket(text) {
            // Сохраняем ссылки на файлы перед очисткой UI
            const savedAudioBlob = this.recordedAudioBlob;
            const savedImage = this.selectedImage;
            
            let ticketMessageText = text;
            if (!text) {
                if (savedAudioBlob) ticketMessageText = 'Голосовое сообщение';
                else if (savedImage) ticketMessageText = 'Изображение';
            }
            
            // Оптимистичное отображение сообщения
            const optimisticMessage = {
                senderType: 'CLIENT',
                message: ticketMessageText,
                createdAt: new Date().toISOString(),
                imageUrl: savedImage ? 'TEMP_IMAGE' : null,
                audioUrl: savedAudioBlob ? 'TEMP_AUDIO' : null,
                _tempImage: savedImage, // Сохраним base64 для отображения
                _tempAudio: savedAudioBlob, // Сохраним blob
                _isOptimistic: true // Флаг для замены через WebSocket
            };
            this.messages.push(optimisticMessage);
            this.renderMessages();
            this.scrollToBottom();
            
            // Сразу очищаем UI для оптимистичного отклика
            const messageInput = document.getElementById('messageInput');
            if (messageInput) messageInput.value = '';
            this.clearAttachments();
            
            const payload = {
                projectApiKey: this.apiKey,
                sessionId: this.sessionId,
                text: ticketMessageText,
                language: 'ru'
            };
            
            if (savedAudioBlob) {
                payload.audioBase64 = await this.blobToBase64(savedAudioBlob);
            }
            
            if (savedImage) {
                payload.imageBase64 = savedImage;
            }
            
            console.log('Creating ticket with payload:', payload);
            
            const response = await fetch(`${this.apiUrl}/api/tickets`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(payload)
            });
            
            console.log('Response status:', response.status);
            
            if (!response.ok) {
                const errorText = await response.text();
                console.error('Error response:', errorText);
                throw new Error(`Failed to create ticket: ${response.status} - ${errorText}`);
            }
            
            const ticket = await response.json();
            console.log('Ticket created:', ticket);
            this.currentTicket = ticket;
            
            // НЕ удаляем оптимистичное сообщение - оно заменится реальным через WebSocket
            // this.messages = []; // <-- убрали, чтобы оптимистичное сообщение осталось
            
            this.connectWebSocket(ticket.id);
        }

        async sendChatMessage(text) {
            let msgText = text;
            if (!text) {
                if (this.recordedAudioBlob) msgText = 'Голосовое сообщение';
                else if (this.selectedImage) msgText = 'Изображение';
            }
            
            // Сохраняем ссылки на файлы перед очисткой UI
            const savedAudioBlob = this.recordedAudioBlob;
            const savedImage = this.selectedImage;
            
            // Оптимистичное отображение
            const tempId = 'temp_' + Date.now();
            const optimisticMessage = {
                id: tempId,
                senderType: 'CLIENT',
                message: msgText,
                createdAt: new Date().toISOString(),
                imageUrl: savedImage ? 'TEMP_IMAGE' : null,
                audioUrl: savedAudioBlob ? 'TEMP_AUDIO' : null,
                _tempImage: savedImage,
                _tempAudio: savedAudioBlob,
                _isOptimistic: true
            };
            this.messages.push(optimisticMessage);
            this.renderMessages();
            this.scrollToBottom();
            
            // Сразу очищаем UI для оптимистичного отклика
            const messageInput = document.getElementById('messageInput');
            if (messageInput) messageInput.value = '';
            this.clearAttachments();
            
            const payload = {
                ticketId: this.currentTicket.id,
                senderType: 'CLIENT',
                message: msgText
            };
            
            if (savedAudioBlob) {
                // Upload audio to MinIO first
                const formData = new FormData();
                formData.append('file', savedAudioBlob, 'audio.webm');
                const uploadResponse = await fetch(`${this.apiUrl}/api/upload`, {
                    method: 'POST',
                    body: formData
                });
                const uploadData = await uploadResponse.json();
                payload.audioUrl = uploadData.url;
                
                // Сохранить транскрипцию если она есть
                if (uploadData.transcription) {
                    payload.transcription = uploadData.transcription;
                }
            }
            
            if (savedImage) {
                // Upload image
                const blob = this.base64ToBlob(savedImage);
                const formData = new FormData();
                formData.append('file', blob, 'image.png');
                const uploadResponse = await fetch(`${this.apiUrl}/api/upload`, {
                    method: 'POST',
                    body: formData
                });
                const uploadData = await uploadResponse.json();
                payload.imageUrl = uploadData.url;
                
                // Сохранить описание изображения если оно есть
                if (uploadData.imageDescription) {
                    payload.imageDescription = uploadData.imageDescription;
                }
            }
            
            const response = await fetch(`${this.apiUrl}/api/tickets/${this.currentTicket.id}/messages`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(payload)
            });
            
            if (!response.ok) throw new Error('Failed to send message');
            
            // Сообщение придёт через WebSocket и заменит оптимистичное
        }

        toggleRecording() {
            if (this.isRecording) {
                this.stopRecording();
            } else {
                this.startRecording();
            }
        }

        async startRecording() {
            try {
                const stream = await navigator.mediaDevices.getUserMedia({ audio: true });
                this.mediaRecorder = new MediaRecorder(stream);
                this.audioChunks = [];

                this.mediaRecorder.ondataavailable = (e) => {
                    this.audioChunks.push(e.data);
                };

                this.mediaRecorder.onstop = () => {
                    this.recordedAudioBlob = new Blob(this.audioChunks, { type: 'audio/webm' });
                    this.showAudioPreview();
                    stream.getTracks().forEach(track => track.stop());
                };

                this.mediaRecorder.start();
                this.isRecording = true;

                const recordBtn = document.getElementById('recordBtn');
                recordBtn.textContent = '⏹️ Стоп';
                recordBtn.style.background = '#ef4444';
            } catch (error) {
                console.error('Error starting recording:', error);
                alert('Не удалось получить доступ к микрофону');
            }
        }

        stopRecording() {
            if (this.mediaRecorder && this.isRecording) {
                this.mediaRecorder.stop();
                this.isRecording = false;

                const recordBtn = document.getElementById('recordBtn');
                recordBtn.textContent = '🎤 Голос';
                recordBtn.style.background = '';
            }
        }

        showAudioPreview() {
            const preview = document.getElementById('audioPreview');
            const player = document.getElementById('audioPlayer');
            const url = URL.createObjectURL(this.recordedAudioBlob);
            player.src = url;
            preview.style.display = 'flex';
        }

        removeAudio() {
            this.recordedAudioBlob = null;
            document.getElementById('audioPreview').style.display = 'none';
        }

        handleImageUpload(e) {
            const file = e.target.files[0];
            if (!file) return;

            const reader = new FileReader();
            reader.onload = (event) => {
                this.selectedImage = event.target.result.split(',')[1];
                this.showImagePreview(event.target.result);
            };
            reader.readAsDataURL(file);
        }

        showImagePreview(dataUrl) {
            const preview = document.getElementById('imagePreview');
            const img = document.getElementById('previewImg');
            img.src = dataUrl;
            preview.style.display = 'flex';
        }

        removeImage() {
            this.selectedImage = null;
            document.getElementById('imagePreview').style.display = 'none';
            document.getElementById('imageUpload').value = '';
        }

        clearAttachments() {
            this.removeAudio();
            this.removeImage();
        }

        showNewTicketForm() {
            document.getElementById('ticketStatus').style.display = 'none';
            const form = document.getElementById('messageForm');
            form.style.display = 'block';
            form.style.visibility = 'visible';
            document.getElementById('chatArea').innerHTML = '<div class="empty-chat">Опишите вашу проблему, и мы поможем!</div>';
        }

        showTicketClosed() {
            document.getElementById('ticketStatus').style.display = 'flex';
            document.getElementById('messageForm').style.display = 'none';
        }

        createNewTicket() {
            this.currentTicket = null;
            this.messages = [];
            this.isTicketClosed = false;
            this.showNewTicketForm();
        }

        scrollToBottom() {
            setTimeout(() => {
                const chatArea = document.getElementById('chatArea');
                chatArea.scrollTop = chatArea.scrollHeight;
            }, 100);
        }

        async blobToBase64(blob) {
            return new Promise((resolve) => {
                const reader = new FileReader();
                reader.onloadend = () => resolve(reader.result.split(',')[1]);
                reader.readAsDataURL(blob);
            });
        }

        base64ToBlob(base64) {
            const byteString = atob(base64);
            const ab = new ArrayBuffer(byteString.length);
            const ia = new Uint8Array(ab);
            for (let i = 0; i < byteString.length; i++) {
                ia[i] = byteString.charCodeAt(i);
            }
            return new Blob([ab], { type: 'image/png' });
        }
        
        showError(message) {
            const errorDiv = document.createElement('div');
            errorDiv.style.cssText = `
                position: fixed;
                top: 20px;
                right: 20px;
                background: #ef4444;
                color: white;
                padding: 12px 20px;
                border-radius: 8px;
                box-shadow: 0 4px 6px rgba(0,0,0,0.1);
                z-index: 10001;
            `;
            errorDiv.textContent = message;
            document.body.appendChild(errorDiv);
            
            setTimeout(() => {
                errorDiv.remove();
            }, 4000);
        }

        escapeHtml(text) {
            const div = document.createElement('div');
            div.textContent = text;
            return div.innerHTML;
        }

        formatTime(timestamp) {
            const date = new Date(timestamp);
            return date.toLocaleTimeString('ru-RU', { hour: '2-digit', minute: '2-digit' });
        }
    }

    // Export
    window.NovaSupportWidget = {
        init: (config) => new SupportWidget(config)
    };
})();
