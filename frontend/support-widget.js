/**
 * Nova Support Widget
 * Embeddable support ticket widget with multimodal support
 */
(function() {
    'use strict';

    class SupportWidget {
        constructor(config) {
            this.apiKey = config.apiKey;
            this.apiUrl = config.apiUrl || 'http://localhost:8080';
            this.position = config.position || 'bottom-right';
            this.primaryColor = config.primaryColor || '#667eea';
            this.mediaRecorder = null;
            this.audioChunks = [];
            this.isRecording = false;
            this.init();
        }

        init() {
            this.injectStyles();
            this.createWidget();
            this.attachEventListeners();
        }

        injectStyles() {
            const link = document.createElement('link');
            link.rel = 'stylesheet';
            link.href = 'support-widget.css';
            document.head.appendChild(link);
        }

        createWidget() {
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
                    
                    <div class="support-widget-content">
                        <div class="support-widget-messages" id="messagesArea">
                            <div class="support-widget-success" id="successMessage">
                                ✅ Ваше обращение принято! Мы свяжемся с вами в ближайшее время.
                            </div>
                            
                            <div class="support-widget-loading" id="loadingSpinner">
                                <div class="support-widget-spinner"></div>
                            </div>
                        </div>
                        
                        <form class="support-widget-form" id="supportForm">
                            <textarea 
                                id="messageText" 
                                placeholder="Введите сообщение..."
                                required
                            ></textarea>
                            
                            <div class="support-widget-uploads">
                                <label class="support-widget-upload-btn">
                                    📷 Прикрепить фото
                                    <input type="file" accept="image/*" id="imageUpload">
                                </label>
                                
                                <button type="button" class="support-widget-upload-btn support-widget-record-btn" id="recordBtn">
                                    🎤 <span id="recordBtnText">Записать голос</span>
                                </button>
                            </div>
                            
                            <div id="previewArea"></div>
                            
                            <button type="submit" class="support-widget-submit">
                                Отправить
                            </button>
                        </form>
                    </div>
                </div>
            `;
            
            document.body.appendChild(container);
            
            this.elements = {
                button: container.querySelector('#supportWidgetBtn'),
                window: container.querySelector('#supportWidgetWindow'),
                close: container.querySelector('#supportWidgetClose'),
                form: container.querySelector('#supportForm'),
                messageText: container.querySelector('#messageText'),
                imageUpload: container.querySelector('#imageUpload'),
                recordBtn: container.querySelector('#recordBtn'),
                recordBtnText: container.querySelector('#recordBtnText'),
                previewArea: container.querySelector('#previewArea'),
                successMessage: container.querySelector('#successMessage'),
                loadingSpinner: container.querySelector('#loadingSpinner'),
                messagesArea: container.querySelector('#messagesArea')
            };
        }

        attachEventListeners() {
            // Toggle widget
            this.elements.button.addEventListener('click', () => this.toggleWidget());
            this.elements.close.addEventListener('click', () => this.closeWidget());
            
            // Form submission
            this.elements.form.addEventListener('submit', (e) => this.handleSubmit(e));
            
            // File previews
            this.elements.imageUpload.addEventListener('change', (e) => this.previewImage(e));
            
            // Audio recording
            this.elements.recordBtn.addEventListener('click', () => this.toggleRecording());
        }

        toggleWidget() {
            this.elements.window.classList.toggle('open');
            this.elements.button.classList.toggle('open');
        }

        closeWidget() {
            this.elements.window.classList.remove('open');
            this.elements.button.classList.remove('open');
        }

        previewImage(event) {
            const file = event.target.files[0];
            if (!file) return;

            const reader = new FileReader();
            reader.onload = (e) => {
                this.elements.previewArea.innerHTML = `
                    <img src="${e.target.result}" class="support-widget-preview" alt="Preview">
                `;
            };
            reader.readAsDataURL(file);
        }

        async toggleRecording() {
            if (!this.isRecording) {
                await this.startRecording();
            } else {
                this.stopRecording();
            }
        }

        async startRecording() {
            try {
                const stream = await navigator.mediaDevices.getUserMedia({ audio: true });
                this.mediaRecorder = new MediaRecorder(stream);
                this.audioChunks = [];

                this.mediaRecorder.ondataavailable = (event) => {
                    this.audioChunks.push(event.data);
                };

                this.mediaRecorder.onstop = async () => {
                    const audioBlob = new Blob(this.audioChunks, { type: 'audio/webm' });
                    this.recordedAudioBlob = audioBlob;
                    
                    // Show preview
                    const url = URL.createObjectURL(audioBlob);
                    this.elements.previewArea.innerHTML = `
                        <audio controls class="support-widget-audio-preview">
                            <source src="${url}" type="audio/webm">
                        </audio>
                    `;
                    
                    // Auto-fill text field if empty
                    if (!this.elements.messageText.value.trim()) {
                        this.elements.messageText.value = 'Голосовое сообщение';
                    }
                    
                    // Stop all tracks
                    stream.getTracks().forEach(track => track.stop());
                };

                this.mediaRecorder.start();
                this.isRecording = true;
                this.elements.recordBtn.style.background = 'linear-gradient(135deg, #ff6b6b 0%, #ee5a6f 100%)';
                this.elements.recordBtnText.textContent = '⏹️ Остановить';
                
            } catch (error) {
                console.error('Error accessing microphone:', error);
                alert('Не удалось получить доступ к микрофону');
            }
        }

        stopRecording() {
            if (this.mediaRecorder && this.isRecording) {
                this.mediaRecorder.stop();
                this.isRecording = false;
                this.elements.recordBtn.style.background = '';
                this.elements.recordBtnText.textContent = 'Записать голос';
            }
        }

        previewAudio(event) {
            const file = event.target.files[0];
            if (!file) return;

            const url = URL.createObjectURL(file);
            this.elements.previewArea.innerHTML = `
                <audio controls class="support-widget-audio-preview">
                    <source src="${url}" type="${file.type}">
                </audio>
            `;
        }

        async handleSubmit(event) {
            event.preventDefault();
            
            const text = this.elements.messageText.value.trim();
            if (!text) return;

            const imageFile = this.elements.imageUpload.files[0];

            // Show loading
            this.elements.form.style.display = 'none';
            this.elements.loadingSpinner.classList.add('show');

            try {
                const ticketData = {
                    projectApiKey: this.apiKey,
                    text: text
                };

                // Convert files to base64 if present
                if (imageFile) {
                    ticketData.imageBase64 = await this.fileToBase64(imageFile);
                }

                // Convert recorded audio to base64 if present
                if (this.recordedAudioBlob) {
                    ticketData.audioBase64 = await this.blobToBase64(this.recordedAudioBlob);
                }

                const response = await fetch(`${this.apiUrl}/api/tickets`, {
                    method: 'POST',
                    headers: {
                        'Content-Type': 'application/json'
                    },
                    body: JSON.stringify(ticketData)
                });

                if (!response.ok) {
                    throw new Error('Failed to submit ticket');
                }

                const result = await response.json();
                console.log('Ticket created:', result);

                // Show success
                this.elements.loadingSpinner.classList.remove('show');
                this.elements.successMessage.classList.add('show');

                // Reset form
                this.elements.form.reset();
                this.elements.previewArea.innerHTML = '';
                this.recordedAudioBlob = null;

                // Hide success and show form after 3 seconds
                setTimeout(() => {
                    this.elements.successMessage.classList.remove('show');
                    this.elements.form.style.display = 'flex';
                    this.closeWidget();
                }, 3000);

            } catch (error) {
                console.error('Error submitting ticket:', error);
                alert('Произошла ошибка. Пожалуйста, попробуйте позже.');
                
                this.elements.loadingSpinner.classList.remove('show');
                this.elements.form.style.display = 'flex';
            }
        }

        fileToBase64(file) {
            return new Promise((resolve, reject) => {
                const reader = new FileReader();
                reader.onload = () => {
                    // Remove data URL prefix (e.g., "data:image/png;base64,")
                    const base64 = reader.result.split(',')[1];
                    resolve(base64);
                };
                reader.onerror = reject;
                reader.readAsDataURL(file);
            });
        }

        blobToBase64(blob) {
            return new Promise((resolve, reject) => {
                const reader = new FileReader();
                reader.onload = () => {
                    // Remove data URL prefix
                    const base64 = reader.result.split(',')[1];
                    resolve(base64);
                };
                reader.onerror = reject;
                reader.readAsDataURL(blob);
            });
        }
    }

    // Global initialization
    window.NovaSupportWidget = {
        init: function(config) {
            if (!config.apiKey) {
                console.error('NovaSupportWidget: API key is required');
                return;
            }
            new SupportWidget(config);
        }
    };

})();
