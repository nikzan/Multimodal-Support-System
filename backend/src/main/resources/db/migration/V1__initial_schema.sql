-- Создание расширения pgvector для работы с векторами (RAG)
CREATE EXTENSION IF NOT EXISTS vector;

-- Таблица проектов (tenants) - сторонние бизнесы, которые используют наш сервис
CREATE TABLE projects (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    api_key VARCHAR(255) NOT NULL UNIQUE,
    website_url VARCHAR(500),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Индекс для быстрого поиска по API ключу
CREATE INDEX idx_projects_api_key ON projects(api_key);

-- Таблица тикетов
CREATE TABLE tickets (
    id BIGSERIAL PRIMARY KEY,
    project_id BIGINT NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    
    -- Статусы: NEW, PROCESSING, OPEN, CLOSED
    status VARCHAR(20) NOT NULL DEFAULT 'NEW',
    
    -- Данные клиента
    customer_email VARCHAR(255),
    customer_name VARCHAR(255),
    
    -- Оригинальный контент
    original_text TEXT,
    audio_url VARCHAR(500),
    image_url VARCHAR(500),
    
    -- Результаты AI обработки
    transcribed_text TEXT,  -- расшифровка аудио
    ai_summary TEXT,        -- краткое содержание
    sentiment VARCHAR(50),  -- POSITIVE, NEGATIVE, NEUTRAL
    sentiment_score DECIMAL(3,2), -- от -1.00 до 1.00
    priority VARCHAR(20),   -- LOW, MEDIUM, HIGH, CRITICAL
    suggested_answer TEXT,  -- предложенный ответ от RAG
    
    -- Ответ оператора
    operator_response TEXT,
    operator_id BIGINT,     -- можно добавить таблицу Users позже
    
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    closed_at TIMESTAMP
);

-- Индексы для оптимизации запросов
CREATE INDEX idx_tickets_project_id ON tickets(project_id);
CREATE INDEX idx_tickets_status ON tickets(status);
CREATE INDEX idx_tickets_created_at ON tickets(created_at DESC);
CREATE INDEX idx_tickets_priority ON tickets(priority);

-- Таблица базы знаний для RAG (Retrieval-Augmented Generation)
CREATE TABLE knowledge_base (
    id BIGSERIAL PRIMARY KEY,
    project_id BIGINT NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    
    -- Контент документа
    content TEXT NOT NULL,
    title VARCHAR(255),
    
    -- Векторное представление для семантического поиска
    -- Размерность 1536 для OpenAI embeddings (text-embedding-ada-002)
    embedding vector(1536),
    
    -- Метаданные
    source_type VARCHAR(50), -- PDF, TXT, URL, MANUAL
    source_url VARCHAR(500),
    
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Индексы для векторного поиска
CREATE INDEX idx_kb_project_id ON knowledge_base(project_id);
-- HNSW индекс для быстрого векторного поиска (требует pgvector)
CREATE INDEX idx_kb_embedding ON knowledge_base USING hnsw (embedding vector_cosine_ops);

-- Триггер для автоматического обновления updated_at
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ language 'plpgsql';

CREATE TRIGGER update_projects_updated_at BEFORE UPDATE ON projects
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_tickets_updated_at BEFORE UPDATE ON tickets
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_knowledge_base_updated_at BEFORE UPDATE ON knowledge_base
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
