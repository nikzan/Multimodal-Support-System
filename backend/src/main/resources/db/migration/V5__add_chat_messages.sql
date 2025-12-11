-- Добавляем поля для чата в tickets
ALTER TABLE tickets ADD COLUMN IF NOT EXISTS session_id VARCHAR(255);
ALTER TABLE tickets ADD COLUMN IF NOT EXISTS is_closed BOOLEAN DEFAULT FALSE;

CREATE INDEX IF NOT EXISTS idx_tickets_session_id ON tickets(session_id);
CREATE INDEX IF NOT EXISTS idx_tickets_is_closed ON tickets(is_closed);

-- Таблица для истории сообщений в чате
CREATE TABLE IF NOT EXISTS chat_messages (
    id BIGSERIAL PRIMARY KEY,
    ticket_id BIGINT NOT NULL REFERENCES tickets(id) ON DELETE CASCADE,
    sender_type VARCHAR(50) NOT NULL, -- 'CLIENT' or 'OPERATOR'
    sender_name VARCHAR(255),
    message TEXT NOT NULL,
    image_url VARCHAR(500),
    audio_url VARCHAR(500),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT fk_chat_ticket FOREIGN KEY (ticket_id) REFERENCES tickets(id)
);

CREATE INDEX IF NOT EXISTS idx_chat_messages_ticket_id ON chat_messages(ticket_id);
CREATE INDEX IF NOT EXISTS idx_chat_messages_created_at ON chat_messages(created_at);
