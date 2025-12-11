/**
 * Nova Support Admin Dashboard
 */

class AdminDashboard {
    constructor() {
        this.apiUrl = 'http://localhost:8080/api';
        this.wsUrl = 'http://localhost:8080/ws';
        this.projectId = 1;
        this.stompClient = null;
        this.subscription = null;
        this.tickets = [];
        this.filteredTickets = [];
        this.currentTicket = null;
        
        this.init();
    }

    init() {
        this.setupEventListeners();
        this.connectWebSocket();
        this.loadTickets();
        this.loadKnowledgeBase();
    }

    setupEventListeners() {
        // Navigation
        document.querySelectorAll('.nav-item').forEach(item => {
            item.addEventListener('click', (e) => {
                e.preventDefault();
                this.switchView(item.dataset.view);
            });
        });

        // Filters
        document.getElementById('statusFilter').addEventListener('change', () => this.applyFilters());
        document.getElementById('priorityFilter').addEventListener('change', () => this.applyFilters());
        document.getElementById('sentimentFilter').addEventListener('change', () => this.applyFilters());
        document.getElementById('searchInput').addEventListener('input', () => this.applyFilters());

        // Buttons
        document.getElementById('refreshBtn').addEventListener('click', () => this.loadTickets());
        document.getElementById('reconnectWs').addEventListener('click', () => this.connectWebSocket());
        document.getElementById('closeModal').addEventListener('click', () => this.closeModal());
        document.getElementById('addKbBtn').addEventListener('click', () => this.showAddKbModal());
    }

    switchView(viewName) {
        // Update navigation
        document.querySelectorAll('.nav-item').forEach(item => item.classList.remove('active'));
        document.querySelector(`[data-view="${viewName}"]`).classList.add('active');

        // Update views
        document.querySelectorAll('.view').forEach(view => view.classList.remove('active'));
        document.getElementById(`${viewName}View`).classList.add('active');

        // Update title
        const titles = {
            tickets: 'Тикеты',
            analytics: 'Аналитика',
            knowledge: 'База знаний',
            settings: 'Настройки'
        };
        document.getElementById('viewTitle').textContent = titles[viewName];
    }

    async connectWebSocket() {
        if (this.stompClient && this.stompClient.connected) {
            console.log('Already connected, disconnecting first...');
            this.disconnectWebSocket();
        }

        const socket = new SockJS(this.wsUrl);
        this.stompClient = Stomp.over(socket);

        this.stompClient.connect({}, (frame) => {
            console.log('WebSocket connected:', frame);
            this.updateConnectionStatus(true);

            if (this.subscription) {
                this.subscription.unsubscribe();
            }

            this.subscription = this.stompClient.subscribe(`/topic/tickets/${this.projectId}`, (message) => {
                const ticket = JSON.parse(message.body);
                console.log('New ticket received via WebSocket:', ticket);
                this.handleNewTicket(ticket);
            });
        }, (error) => {
            console.error('WebSocket error:', error);
            this.updateConnectionStatus(false);
        });
    }

    disconnectWebSocket() {
        if (this.subscription) {
            this.subscription.unsubscribe();
            this.subscription = null;
        }
        if (this.stompClient) {
            this.stompClient.disconnect();
            this.stompClient = null;
        }
        this.updateConnectionStatus(false);
    }

    updateConnectionStatus(connected) {
        const status = document.getElementById('wsStatus');
        if (connected) {
            status.className = 'status connected';
            status.querySelector('.text').textContent = 'Подключено';
        } else {
            status.className = 'status disconnected';
            status.querySelector('.text').textContent = 'Отключено';
        }
    }

    handleNewTicket(ticket) {
        // Add to beginning of list
        this.tickets.unshift(ticket);
        this.applyFilters();
        this.updateTicketsCount();
        
        // Show notification
        this.showNotification(`Новый тикет #${ticket.id}`, ticket.originalText || ticket.transcribedText);
    }

    async loadTickets() {
        try {
            document.getElementById('loadingSpinner').style.display = 'block';
            document.getElementById('emptyState').style.display = 'none';

            const response = await fetch(`${this.apiUrl}/admin/tickets?projectId=${this.projectId}&size=100`);
            
            if (!response.ok) {
                throw new Error(`HTTP ${response.status}: ${response.statusText}`);
            }
            
            const data = await response.json();
            
            this.tickets = data.content || [];
            this.applyFilters();
            this.updateTicketsCount();
            this.loadAnalytics();

            document.getElementById('loadingSpinner').style.display = 'none';
        } catch (error) {
            console.error('Error loading tickets:', error);
            document.getElementById('loadingSpinner').style.display = 'none';
            this.showError(`Ошибка загрузки тикетов: ${error.message}`);
        }
    }

    applyFilters() {
        const status = document.getElementById('statusFilter').value;
        const priority = document.getElementById('priorityFilter').value;
        const sentiment = document.getElementById('sentimentFilter').value;
        const search = document.getElementById('searchInput').value.toLowerCase();

        this.filteredTickets = this.tickets.filter(ticket => {
            if (status && ticket.status !== status) return false;
            if (priority && ticket.priority !== priority) return false;
            if (sentiment && ticket.sentiment !== sentiment) return false;
            if (search) {
                const text = (ticket.originalText || ticket.transcribedText || '').toLowerCase();
                const summary = (ticket.aiSummary || '').toLowerCase();
                if (!text.includes(search) && !summary.includes(search)) return false;
            }
            return true;
        });

        this.renderTickets();
    }

    renderTickets() {
        const container = document.getElementById('ticketsList');
        
        if (this.filteredTickets.length === 0) {
            container.innerHTML = '';
            document.getElementById('emptyState').style.display = 'block';
            return;
        }

        document.getElementById('emptyState').style.display = 'none';

        container.innerHTML = this.filteredTickets.map(ticket => `
            <div class="ticket-card priority-${ticket.priority.toLowerCase()} sentiment-${ticket.sentiment.toLowerCase()}" 
                 data-id="${ticket.id}" onclick="dashboard.showTicketDetails(${ticket.id})">
                <div class="ticket-header">
                    <div class="ticket-id">#${ticket.id}</div>
                    <div class="ticket-badges">
                        <span class="badge badge-${ticket.priority.toLowerCase()}">${ticket.priority}</span>
                        <span class="badge badge-${ticket.sentiment.toLowerCase()}">${this.getSentimentIcon(ticket.sentiment)}</span>
                        <span class="badge badge-status">${ticket.status}</span>
                    </div>
                </div>
                
                <div class="ticket-content">
                    ${ticket.aiSummary ? `<p class="ticket-summary"><strong>🤖 AI Summary:</strong> ${this.escapeHtml(ticket.aiSummary.substring(0, 150))}${ticket.aiSummary.length > 150 ? '...' : ''}</p>` : ''}
                    <p class="ticket-text-preview">${this.escapeHtml((ticket.originalText || ticket.transcribedText || 'Нет текста').substring(0, 80))}...</p>
                </div>
                
                <div class="ticket-footer">
                    <span class="ticket-time">${this.formatDate(ticket.createdAt)}</span>
                    ${ticket.imageUrl ? '<span class="ticket-attachment">📷</span>' : ''}
                    ${ticket.audioUrl ? '<span class="ticket-attachment">🎤</span>' : ''}
                </div>
            </div>
        `).join('');
    }

    async showTicketDetails(ticketId) {
        const ticket = this.tickets.find(t => t.id === ticketId);
        if (!ticket) return;

        this.currentTicket = ticket;

        const modal = document.getElementById('ticketModal');
        const title = document.getElementById('modalTicketTitle');
        const body = document.getElementById('modalTicketBody');
        const deleteBtn = document.getElementById('deleteTicketBtn');

        title.textContent = `Тикет #${ticket.id} - Чат с клиентом`;
        
        if (deleteBtn) {
            deleteBtn.style.display = 'inline-block';
            deleteBtn.onclick = () => {
                this.closeModal();
                this.deleteTicket(ticketId);
            };
        }
        
        body.innerHTML = `
            <div class="ticket-chat-layout">
                <!-- Left: Chat -->
                <div class="chat-column">
                    <div class="chat-header-info">
                        <span class="badge badge-${ticket.status.toLowerCase()}">${ticket.status}</span>
                        <span class="badge badge-${ticket.priority.toLowerCase()}">${ticket.priority}</span>
                        <span class="badge badge-${ticket.sentiment.toLowerCase()}">${this.getSentimentIcon(ticket.sentiment)}</span>
                    </div>
                    
                    <div class="chat-messages-area" id="chatMessagesContainer">
                        <div id="chatMessages">
                            <div style="text-align: center; color: #999; padding: 20px;">Загрузка сообщений...</div>
                        </div>
                    </div>
                    
                    <div class="chat-input-area">
                        <textarea 
                            id="operatorMessageInput" 
                            placeholder="Введите ответ клиенту..."
                            rows="3"
                        ></textarea>
                        <div class="chat-actions">
                            <div class="chat-actions-left">
                                <button class="btn btn-primary" onclick="dashboard.sendOperatorMessage()">💬 Отправить</button>
                            </div>
                            <div class="chat-actions-right">
                                <button class="btn btn-danger" onclick="dashboard.deleteTicket(${ticketId})" title="Удалить тикет">🗑️ Удалить</button>
                                <button class="btn" onclick="dashboard.closeTicketFromChat()">🔒 Закрыть тикет</button>
                            </div>
                        </div>
                    </div>
                </div>
                
                <!-- Right: AI Panel -->
                <div class="ai-panel">
                    <div class="ai-section">
                        <h4>📝 AI Summary (первое сообщение)</h4>
                        <div class="ai-summary">
                            ${ticket.aiSummary ? this.escapeHtml(ticket.aiSummary) : '<em style="color: #999;">Нет резюме</em>'}
                        </div>
                    </div>
                    
                    <div class="ai-section rag-section">
                        <div class="rag-header">
                            <h4>🤖 RAG Ответ</h4>
                            <button class="btn btn-sm" onclick="dashboard.refreshRagAnswer()" id="refreshRagBtn">🔄</button>
                        </div>
                        <div class="rag-answer" id="ragAnswer">
                            <div style="text-align: center; color: #999; padding: 20px;">Загрузка...</div>
                        </div>
                        <button class="btn btn-secondary" onclick="dashboard.insertRagAnswer()" style="width: 100%; margin-top: 12px;">
                            ⬅️ Вставить RAG ответ
                        </button>
                    </div>
                </div>
            </div>
        `;

        modal.classList.add('active');
        
        // Load chat messages
        await this.loadChatMessages(ticket.id);
        
        // Load RAG answer
        await this.loadRagAnswer(ticket.id);
        
        // Subscribe to new messages
        this.subscribeToChatMessages(ticket.id);
    }
    
    toggleTicketDetails() {
        const details = document.getElementById('ticketDetailsExpanded');
        const btn = document.getElementById('toggleDetailsBtn');
        if (details.style.display === 'none') {
            details.style.display = 'block';
            btn.textContent = '📋 Скрыть';
        } else {
            details.style.display = 'none';
            btn.textContent = '📋 Детали';
        }
    }
    
    async loadChatMessages(ticketId) {
        try {
            const response = await fetch(`${this.apiUrl}/tickets/${ticketId}/messages`);
            if (!response.ok) throw new Error('Failed to load messages');
            
            const messages = await response.json();
            this.renderChatMessages(messages);
        } catch (error) {
            console.error('Error loading chat messages:', error);
            document.getElementById('chatMessages').innerHTML = '<div style="text-align: center; color: #ef4444; padding: 20px;">Ошибка загрузки сообщений</div>';
        }
    }
    
    renderChatMessages(messages) {
        const container = document.getElementById('chatMessages');
        
        if (messages.length === 0) {
            container.innerHTML = '<div style="text-align: center; color: #999; padding: 20px;">Нет сообщений</div>';
            return;
        }
        
        const minioUrl = 'http://localhost:9000/support-tickets/';
        
        container.innerHTML = messages.map(msg => {
            const isOperator = msg.senderType === 'OPERATOR';
            const alignStyle = isOperator ? 'flex-start' : 'flex-end';
            const bgColor = isOperator ? '#f3f4f6' : 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)';
            const textColor = isOperator ? '#333' : 'white';
            const borderRadius = isOperator ? '16px 16px 16px 4px' : '16px 16px 4px 16px';
            
            // Get transcription and image description from metadata
            let transcription = '';
            let hasTranscription = false;
            let imageDescription = '';
            if (msg.metadata) {
                try {
                    const metadata = typeof msg.metadata === 'string' ? JSON.parse(msg.metadata) : msg.metadata;
                    
                    // Транскрипция аудио
                    if (metadata.transcription) {
                        hasTranscription = true;
                        transcription = `<div style="margin-top: 4px; font-size: 12px; font-style: italic; color: #999; line-height: 1.4;">
                            🎤 ${this.escapeHtml(metadata.transcription)}
                        </div>`;
                    }
                    
                    // Описание изображения
                    if (metadata.imageDescription) {
                        imageDescription = `<div style="margin-top: 4px; font-size: 12px; font-style: italic; color: #999; line-height: 1.4;">
                            🖼️ ${this.escapeHtml(metadata.imageDescription)}
                        </div>`;
                    }
                } catch (e) {
                    console.error('Failed to parse metadata:', e);
                }
            }
            
            // Показывать основной текст только если это не "Голосовое сообщение" с транскрипцией
            const showMainMessage = !(hasTranscription && (msg.message === 'Голосовое сообщение' || msg.message.includes('Голосовое')));
            
            return `
                <div style="display: flex; justify-content: ${alignStyle}; margin-bottom: 12px;">
                    <div style="max-width: 70%;">
                        <div style="background: ${bgColor}; color: ${textColor}; padding: 12px 16px; border-radius: ${borderRadius};">
                            ${showMainMessage ? `<p style="margin: 0; font-size: 14px; line-height: 1.5;">${this.escapeHtml(msg.message)}</p>` : ''}
                            ${msg.audioUrl ? `<audio controls src="${minioUrl}${msg.audioUrl}" style="width: 100%; margin-top: ${showMainMessage ? '8px' : '0'};"></audio>` : ''}
                            ${msg.imageUrl ? `<img src="${minioUrl}${msg.imageUrl}" style="max-width: 100%; border-radius: 8px; margin-top: 8px;">` : ''}
                        </div>
                        ${transcription}
                        ${imageDescription}
                        <div style="font-size: 11px; color: #999; margin-top: 4px; text-align: ${isOperator ? 'left' : 'right'};">
                            ${this.formatTime(msg.createdAt)}
                        </div>
                    </div>
                </div>
            `;
        }).join('');
        
        // Scroll to bottom
        const messagesContainer = document.getElementById('chatMessagesContainer');
        messagesContainer.scrollTop = messagesContainer.scrollHeight;
    }
    
    subscribeToChatMessages(ticketId) {
        if (!this.stompClient || !this.stompClient.connected) {
            console.warn('WebSocket not connected, cannot subscribe to chat messages');
            return;
        }
        
        // Unsubscribe from previous chat if any
        if (this.chatSubscription) {
            this.chatSubscription.unsubscribe();
        }
        
        if (this.ragSubscription) {
            this.ragSubscription.unsubscribe();
        }
        
        // Subscribe to chat messages
        this.chatSubscription = this.stompClient.subscribe(
            `/topic/tickets/${ticketId}/messages`,
            (message) => {
                const msg = JSON.parse(message.body);
                console.log('New chat message received:', msg);
                this.loadChatMessages(ticketId);
            }
        );
        
        // Subscribe to RAG updates
        this.ragSubscription = this.stompClient.subscribe(
            `/topic/tickets/${ticketId}/rag-updated`,
            (message) => {
                console.log('RAG update notification received');
                this.loadRagAnswer(ticketId);
            }
        );
    }
    
    async sendOperatorMessage() {
        const input = document.getElementById('operatorMessageInput');
        const text = input.value.trim();
        
        if (!text) {
            alert('Введите сообщение');
            return;
        }
        
        try {
            const payload = {
                ticketId: this.currentTicket.id,
                senderType: 'OPERATOR',
                senderName: 'Support Team',
                message: text
            };
            
            const response = await fetch(`${this.apiUrl}/tickets/${this.currentTicket.id}/messages`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(payload)
            });
            
            if (!response.ok) throw new Error('Failed to send message');
            
            input.value = '';
            // Message will be rendered via WebSocket subscription
            
        } catch (error) {
            console.error('Error sending message:', error);
            alert('Ошибка отправки сообщения');
        }
    }
    
    insertAiAnswer() {
        if (!this.currentTicket || !this.currentTicket.suggestedAnswer) {
            alert('Нет предложенного AI ответа');
            return;
        }
        
        const input = document.getElementById('operatorMessageInput');
        input.value = this.currentTicket.suggestedAnswer;
        input.focus();
    }
    
    async closeTicketFromChat() {
        if (!confirm('Закрыть этот тикет? Клиент не сможет отправлять новые сообщения.')) {
            return;
        }
        
        try {
            const response = await fetch(`${this.apiUrl}/tickets/${this.currentTicket.id}/close`, {
                method: 'PATCH'
            });
            
            if (!response.ok) throw new Error('Failed to close ticket');
            
            alert('Тикет закрыт');
            this.closeModal();
            this.loadTickets();
            
        } catch (error) {
            console.error('Error closing ticket:', error);
            alert('Ошибка закрытия тикета');
        }
    }
    
    formatTime(timestamp) {
        const date = new Date(timestamp);
        return date.toLocaleString('ru-RU', { 
            day: '2-digit',
            month: '2-digit',
            hour: '2-digit',
            minute: '2-digit'
        });
    }
    
    async loadRagAnswer(ticketId) {
        try {
            const ragAnswer = document.getElementById('ragAnswer');
            ragAnswer.innerHTML = '<div style="text-align: center; color: #999; padding: 20px;">Загрузка...</div>';
            
            const response = await fetch(`http://localhost:8080/api/tickets/${ticketId}/rag-answer`);
            
            if (!response.ok) {
                throw new Error(`Failed to load RAG answer: ${response.status}`);
            }
            
            const data = await response.json();
            
            if (data.messagesCount > 0) {
                ragAnswer.setAttribute('data-answer', data.answer);
                ragAnswer.innerHTML = `
                    <p>${this.escapeHtml(data.answer)}</p>
                    <div style="margin-top: 12px; padding-top: 12px; border-top: 1px solid #e2e8f0; font-size: 12px; color: #94a3b8;">
                        📊 ${data.messagesCount} сообщений • Обновлено: ${new Date(data.lastUpdated).toLocaleTimeString('ru-RU')}
                    </div>
                `;
            } else {
                ragAnswer.removeAttribute('data-answer');
                ragAnswer.innerHTML = '<em style="color: #999;">Нет новых сообщений для анализа</em>';
            }
        } catch (error) {
            console.error('Error loading RAG answer:', error);
            document.getElementById('ragAnswer').innerHTML = '<em style="color: #ef4444;">Ошибка загрузки RAG</em>';
        }
    }
    
    async refreshRagAnswer() {
        if (!this.currentTicket) return;
        
        const btn = document.getElementById('refreshRagBtn');
        btn.disabled = true;
        btn.textContent = '⏳';
        
        await this.loadRagAnswer(this.currentTicket.id);
        
        setTimeout(() => {
            btn.disabled = false;
            btn.textContent = '🔄';
        }, 500);
    }
    
    insertRagAnswer() {
        const ragAnswer = document.getElementById('ragAnswer');
        const textarea = document.getElementById('operatorMessageInput');
        const text = ragAnswer.getAttribute('data-answer');
        
        if (text && text.trim()) {
            textarea.value = text;
            textarea.focus();
        } else {
            alert('Нет RAG ответа для вставки');
        }
    }

    async updateTicketStatus() {
        const newStatus = document.getElementById('modalStatus').value;
        
        try {
            const response = await fetch(`${this.apiUrl}/admin/tickets/${this.currentTicket.id}/status?status=${newStatus}`, {
                method: 'PATCH'
            });

            if (response.ok) {
                const updated = await response.json();
                // Update local data
                const index = this.tickets.findIndex(t => t.id === this.currentTicket.id);
                if (index !== -1) {
                    this.tickets[index] = updated;
                }
                this.applyFilters();
                this.showNotification('Успех', 'Статус тикета обновлен');
                this.closeModal();
            }
        } catch (error) {
            console.error('Error updating ticket:', error);
            this.showError('Ошибка обновления статуса');
        }
    }

    async deleteTicket(ticketId) {
        if (!confirm(`Удалить тикет #${ticketId}?`)) return;

        try {
            const response = await fetch(`${this.apiUrl}/admin/tickets/${ticketId}`, {
                method: 'DELETE'
            });

            if (response.ok) {
                this.tickets = this.tickets.filter(t => t.id !== ticketId);
                this.applyFilters();
                this.updateTicketsCount();
                this.showNotification('Успех', 'Тикет удален');
                if (this.currentTicket && this.currentTicket.id === ticketId) {
                    this.closeModal();
                }
            }
        } catch (error) {
            console.error('Error deleting ticket:', error);
            this.showError('Ошибка удаления тикета');
        }
    }
    
    async deleteCurrentTicket() {
        if (this.currentTicket) {
            await this.deleteTicket(this.currentTicket.id);
        }
    }

    closeModal() {
        document.getElementById('ticketModal').classList.remove('active');
        this.currentTicket = null;
    }

    async loadAnalytics() {
        const stats = {
            critical: this.tickets.filter(t => t.priority === 'CRITICAL').length,
            high: this.tickets.filter(t => t.priority === 'HIGH').length,
            low: this.tickets.filter(t => t.priority === 'LOW').length,
            total: this.tickets.length
        };

        document.getElementById('criticalCount').textContent = stats.critical;
        document.getElementById('highCount').textContent = stats.high;
        document.getElementById('lowCount').textContent = stats.low;
        document.getElementById('totalCount').textContent = stats.total;

        this.renderPriorityChart();
        this.renderSentimentChart();
        this.renderTimelineChart();
    }

    renderPriorityChart() {
        const ctx = document.getElementById('priorityChart');
        if (this.priorityChart) this.priorityChart.destroy();

        const data = {
            critical: this.tickets.filter(t => t.priority === 'CRITICAL').length,
            high: this.tickets.filter(t => t.priority === 'HIGH').length,
            low: this.tickets.filter(t => t.priority === 'LOW').length
        };

        this.priorityChart = new Chart(ctx, {
            type: 'doughnut',
            data: {
                labels: ['Критичные', 'Высокие', 'Низкие'],
                datasets: [{
                    data: [data.critical, data.high, data.low],
                    backgroundColor: ['#ef4444', '#f97316', '#22c55e']
                }]
            },
            options: {
                responsive: true,
                maintainAspectRatio: false
            }
        });
    }

    renderSentimentChart() {
        const ctx = document.getElementById('sentimentChart');
        if (this.sentimentChart) this.sentimentChart.destroy();

        const data = {
            negative: this.tickets.filter(t => t.sentiment === 'NEGATIVE').length,
            neutral: this.tickets.filter(t => t.sentiment === 'NEUTRAL').length,
            positive: this.tickets.filter(t => t.sentiment === 'POSITIVE').length
        };

        this.sentimentChart = new Chart(ctx, {
            type: 'pie',
            data: {
                labels: ['Негативные', 'Нейтральные', 'Позитивные'],
                datasets: [{
                    data: [data.negative, data.neutral, data.positive],
                    backgroundColor: ['#ef4444', '#94a3b8', '#22c55e']
                }]
            },
            options: {
                responsive: true,
                maintainAspectRatio: false
            }
        });
    }

    renderTimelineChart() {
        const ctx = document.getElementById('timelineChart');
        if (this.timelineChart) this.timelineChart.destroy();

        // Group tickets by date
        const last7Days = Array.from({length: 7}, (_, i) => {
            const d = new Date();
            d.setDate(d.getDate() - (6 - i));
            return d.toISOString().split('T')[0];
        });

        const ticketsByDate = last7Days.map(date => {
            return this.tickets.filter(t => t.createdAt.startsWith(date)).length;
        });

        this.timelineChart = new Chart(ctx, {
            type: 'line',
            data: {
                labels: last7Days.map(d => new Date(d).toLocaleDateString('ru-RU', { month: 'short', day: 'numeric' })),
                datasets: [{
                    label: 'Количество тикетов',
                    data: ticketsByDate,
                    borderColor: '#667eea',
                    backgroundColor: 'rgba(102, 126, 234, 0.1)',
                    tension: 0.4,
                    fill: true
                }]
            },
            options: {
                responsive: true,
                maintainAspectRatio: false,
                scales: {
                    y: {
                        beginAtZero: true,
                        ticks: {
                            stepSize: 1
                        }
                    }
                }
            }
        });
    }

    async loadKnowledgeBase() {
        try {
            const response = await fetch(`${this.apiUrl}/admin/knowledge-base?projectId=${this.projectId}&size=100`);
            if (!response.ok) {
                throw new Error('Failed to load knowledge base');
            }
            const data = await response.json();
            const articles = data.content || [];
            
            document.getElementById('kbCount').textContent = articles.length;
            
            const container = document.getElementById('kbList');
            if (articles.length === 0) {
                container.innerHTML = '<div class="empty-state"><div class="empty-icon">📚</div><h3>База знаний пуста</h3><p>Добавьте первую статью</p></div>';
                return;
            }
            
            container.innerHTML = articles.map(article => `
                <div class="kb-card" onclick="dashboard.showKbArticle(${article.id})" style="cursor: pointer;">
                    <h3>${this.escapeHtml(article.title)}</h3>
                    <p>${this.escapeHtml(article.content.substring(0, 200))}...</p>
                    <div class="kb-footer">
                        <span>${this.formatDate(article.createdAt)}</span>
                        <div class="kb-actions">
                            <button class="btn btn-sm btn-primary" onclick="event.stopPropagation(); dashboard.showKbArticle(${article.id})">Просмотр</button>
                            <button class="btn btn-sm btn-danger" onclick="event.stopPropagation(); dashboard.deleteKbArticle(${article.id})">Удалить</button>
                        </div>
                    </div>
                </div>
            `).join('');
        } catch (error) {
            console.error('Error loading KB:', error);
            document.getElementById('kbList').innerHTML = '<div class="empty-state"><div class="empty-icon">❌</div><h3>Ошибка загрузки</h3><p>' + error.message + '</p></div>';
        }
    }

    updateTicketsCount() {
        document.getElementById('ticketsCount').textContent = this.tickets.length;
    }

    getSentimentIcon(sentiment) {
        const icons = {
            POSITIVE: '😊',
            NEUTRAL: '😐',
            NEGATIVE: '😡'
        };
        return icons[sentiment] || '❓';
    }

    formatDate(dateString) {
        const date = new Date(dateString);
        const now = new Date();
        const diff = now - date;
        
        if (diff < 60000) return 'Только что';
        if (diff < 3600000) return `${Math.floor(diff / 60000)} мин назад`;
        if (diff < 86400000) return `${Math.floor(diff / 3600000)} ч назад`;
        
        return date.toLocaleString('ru-RU', {
            day: 'numeric',
            month: 'short',
            hour: '2-digit',
            minute: '2-digit'
        });
    }

    escapeHtml(text) {
        const div = document.createElement('div');
        div.textContent = text;
        return div.innerHTML;
    }

    showNotification(title, message) {
        // Simple toast notification
        const toast = document.createElement('div');
        toast.className = 'toast success';
        toast.innerHTML = `<strong>${title}</strong><p>${message}</p>`;
        document.body.appendChild(toast);
        
        setTimeout(() => {
            toast.classList.add('show');
        }, 100);
        
        setTimeout(() => {
            toast.classList.remove('show');
            setTimeout(() => toast.remove(), 300);
        }, 3000);
    }

    showError(message) {
        const toast = document.createElement('div');
        toast.className = 'toast error';
        toast.innerHTML = `<strong>Ошибка</strong><p>${message}</p>`;
        document.body.appendChild(toast);
        
        setTimeout(() => {
            toast.classList.add('show');
        }, 100);
        
        setTimeout(() => {
            toast.classList.remove('show');
            setTimeout(() => toast.remove(), 300);
        }, 3000);
    }

    showAddKbModal() {
        const modal = document.getElementById('ticketModal');
        const title = document.getElementById('modalTicketTitle');
        const body = document.getElementById('modalTicketBody');
        const deleteBtn = document.getElementById('deleteTicketBtn');
        
        title.textContent = 'Добавить статью в БЗ';
        if (deleteBtn) deleteBtn.style.display = 'none';
        
        body.innerHTML = `
            <div class="kb-form">
                <div class="form-group">
                    <label for="kbTitle"><strong>Заголовок:</strong></label>
                    <input type="text" id="kbTitle" class="input" placeholder="Например: Как оформить возврат?" required>
                </div>
                <div class="form-group">
                    <label for="kbContent"><strong>Содержание:</strong></label>
                    <textarea id="kbContent" class="textarea" rows="10" placeholder="Подробное описание..." required></textarea>
                </div>
                <div class="form-group">
                    <label for="kbTags"><strong>Теги (через запятую):</strong></label>
                    <input type="text" id="kbTags" class="input" placeholder="возврат, доставка, заказ">
                </div>
                <button class="btn btn-primary" onclick="dashboard.saveKbArticle()" style="width: 100%; margin-top: 16px;">
                    ✅ Сохранить статью
                </button>
            </div>
        `;
        
        modal.classList.add('active');
    }

    async saveKbArticle() {
        const title = document.getElementById('kbTitle').value.trim();
        const content = document.getElementById('kbContent').value.trim();
        const tags = document.getElementById('kbTags').value.trim();
        
        if (!title || !content) {
            this.showError('Заполните заголовок и содержание');
            return;
        }
        
        try {
            const response = await fetch(`${this.apiUrl}/admin/knowledge-base`, {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json'
                },
                body: JSON.stringify({
                    projectId: this.projectId,
                    title: title,
                    content: content,
                    tags: tags ? tags.split(',').map(t => t.trim()) : []
                })
            });
            
            if (!response.ok) {
                throw new Error(`HTTP ${response.status}`);
            }
            
            this.showNotification('Успех', 'Статья успешно добавлена!');
            this.closeModal();
            this.loadKnowledgeBase();
        } catch (error) {
            console.error('Error saving KB article:', error);
            this.showError(`Ошибка сохранения: ${error.message}`);
        }
    }

    async deleteKbArticle(articleId) {
        if (!confirm('Удалить эту статью из базы знаний?')) {
            return;
        }
        
        try {
            const response = await fetch(`${this.apiUrl}/admin/knowledge-base/${articleId}`, {
                method: 'DELETE'
            });
            
            if (!response.ok) {
                throw new Error(`HTTP ${response.status}`);
            }
            
            this.showNotification('Успех', 'Статья удалена');
            this.loadKnowledgeBase();
        } catch (error) {
            console.error('Error deleting KB article:', error);
            this.showError(`Ошибка удаления: ${error.message}`);
        }
    }

    async showKbArticle(articleId) {
        try {
            const response = await fetch(`${this.apiUrl}/admin/knowledge-base/${articleId}`);
            if (!response.ok) {
                throw new Error(`HTTP ${response.status}`);
            }
            
            const article = await response.json();
            
            const modal = document.getElementById('ticketModal');
            const title = document.getElementById('modalTicketTitle');
            const body = document.getElementById('modalTicketBody');
            const deleteBtn = document.getElementById('deleteTicketBtn');
            
            title.textContent = article.title;
            if (deleteBtn) {
                deleteBtn.style.display = 'inline-block';
                deleteBtn.onclick = () => {
                    this.closeModal();
                    this.deleteKbArticle(articleId);
                };
            }
            
            body.innerHTML = `
                <div class="kb-article-view">
                    <div class="detail-section">
                        <strong>Создано:</strong>
                        <p>${this.formatDate(article.createdAt)}</p>
                    </div>
                    
                    ${article.tags && article.tags.length > 0 ? `
                        <div class="detail-section">
                            <strong>Теги:</strong>
                            <div class="kb-tags">
                                ${article.tags.map(tag => `<span class="kb-tag">${this.escapeHtml(tag)}</span>`).join('')}
                            </div>
                        </div>
                    ` : ''}
                    
                    <div class="detail-section">
                        <strong>Содержание:</strong>
                        <div class="kb-content">${this.escapeHtml(article.content).replace(/\n/g, '<br>')}</div>
                    </div>
                </div>
            `;
            
            modal.classList.add('active');
        } catch (error) {
            console.error('Error loading KB article:', error);
            this.showError(`Ошибка загрузки: ${error.message}`);
        }
    }
}

// Initialize dashboard
let dashboard;
window.onload = () => {
    dashboard = new AdminDashboard();
};
