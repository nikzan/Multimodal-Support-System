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
        document.getElementById('closeTicketBtn').addEventListener('click', () => this.closeModal());
        document.getElementById('deleteTicketBtn').addEventListener('click', () => this.deleteCurrentTicket());
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

        title.textContent = `Тикет #${ticket.id}`;
        
        const minioUrl = 'http://localhost:9000/support-tickets/';
        const imageUrl = ticket.imageUrl ? (ticket.imageUrl.startsWith('http') ? ticket.imageUrl : minioUrl + ticket.imageUrl) : null;
        const audioUrl = ticket.audioUrl ? (ticket.audioUrl.startsWith('http') ? ticket.audioUrl : minioUrl + ticket.audioUrl) : null;
        
        body.innerHTML = `
            <div class="ticket-details">
                <div class="detail-row">
                    <strong>Статус:</strong>
                    <select class="select" id="modalStatus">
                        <option value="OPEN" ${ticket.status === 'OPEN' ? 'selected' : ''}>Открыт</option>
                        <option value="IN_PROGRESS" ${ticket.status === 'IN_PROGRESS' ? 'selected' : ''}>В работе</option>
                        <option value="CLOSED" ${ticket.status === 'CLOSED' ? 'selected' : ''}>Закрыт</option>
                    </select>
                </div>
                
                <div class="detail-row">
                    <strong>Приоритет:</strong>
                    <span class="badge badge-${ticket.priority.toLowerCase()}">${ticket.priority}</span>
                </div>
                
                <div class="detail-row">
                    <strong>Настроение:</strong>
                    <span class="badge badge-${ticket.sentiment.toLowerCase()}">${this.getSentimentIcon(ticket.sentiment)} ${ticket.sentiment}</span>
                </div>
                
                <div class="detail-row">
                    <strong>Создан:</strong>
                    <span>${this.formatDate(ticket.createdAt)}</span>
                </div>
                
                <div class="detail-section">
                    <strong>Текст обращения:</strong>
                    <p>${this.escapeHtml(ticket.originalText || 'Нет текста')}</p>
                </div>
                
                ${ticket.transcribedText ? `
                    <div class="detail-section">
                        <strong>🎤 Транскрипция аудио:</strong>
                        <p style="background: #f1f5f9; padding: 12px; border-radius: 6px; font-style: italic;">${this.escapeHtml(ticket.transcribedText)}</p>
                    </div>
                ` : ''}
                
                ${ticket.aiSummary ? `
                    <div class="detail-section">
                        <strong>AI Резюме:</strong>
                        <p>${this.escapeHtml(ticket.aiSummary)}</p>
                    </div>
                ` : ''}
                
                ${ticket.suggestedAnswer ? `
                    <div class="detail-section">
                        <strong>Предложенный ответ:</strong>
                        <p>${this.escapeHtml(ticket.suggestedAnswer)}</p>
                    </div>
                ` : ''}
                
                ${imageUrl ? `
                    <div class="detail-section">
                        <strong>Изображение:</strong><br>
                        <div class="image-container">
                            <img src="${imageUrl}" alt="Attachment" style="max-width: 100%; border-radius: 8px; margin-top: 8px;" 
                                 onerror="this.style.display='none'; this.nextElementSibling.style.display='block';">
                            <p style="display:none; color:#ef4444; margin-top:8px;">❌ Изображение недоступно</p>
                        </div>
                        <br><a href="${imageUrl}" target="_blank" style="font-size: 12px; color: #667eea;">🔗 Открыть в новой вкладке</a>
                    </div>
                ` : ''}
                
                ${audioUrl ? `
                    <div class="detail-section">
                        <strong>Аудио:</strong><br>
                        <div class="audio-container">
                            <audio controls src="${audioUrl}" style="width: 100%; margin-top: 8px;"
                                   onerror="this.style.display='none'; this.nextElementSibling.style.display='block';"></audio>
                            <p style="display:none; color:#ef4444; margin-top:8px;">❌ Аудио недоступно</p>
                        </div>
                        <br><a href="${audioUrl}" target="_blank" style="font-size: 12px; color: #667eea;">🔗 Открыть в новой вкладке</a>
                    </div>
                ` : ''}
                
                <div class="detail-actions">
                    <button class="btn btn-primary" onclick="dashboard.updateTicketStatus()">💾 Сохранить изменения</button>
                </div>
            </div>
        `;

        modal.classList.add('active');
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

    async deleteCurrentTicket() {
        if (!confirm(`Удалить тикет #${this.currentTicket.id}?`)) return;

        try {
            const response = await fetch(`${this.apiUrl}/admin/tickets/${this.currentTicket.id}`, {
                method: 'DELETE'
            });

            if (response.ok) {
                this.tickets = this.tickets.filter(t => t.id !== this.currentTicket.id);
                this.applyFilters();
                this.updateTicketsCount();
                this.showNotification('Успех', 'Тикет удален');
                this.closeModal();
            }
        } catch (error) {
            console.error('Error deleting ticket:', error);
            this.showError('Ошибка удаления тикета');
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
                <div class="kb-card">
                    <h3>${this.escapeHtml(article.title)}</h3>
                    <p>${this.escapeHtml(article.content.substring(0, 200))}...</p>
                    <div class="kb-footer">
                        <span>${this.formatDate(article.createdAt)}</span>
                        <button class="btn btn-sm btn-danger" onclick="dashboard.deleteKbArticle(${article.id})">🗑️</button>
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
        deleteBtn.style.display = 'none';
        
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
}

// Initialize dashboard
let dashboard;
window.onload = () => {
    dashboard = new AdminDashboard();
};
