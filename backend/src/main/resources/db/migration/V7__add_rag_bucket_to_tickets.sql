-- Add RAG bucket fields to tickets table
ALTER TABLE tickets
ADD COLUMN last_operator_response_at TIMESTAMP,
ADD COLUMN rag_bucket_message_ids TEXT;

-- Create index for faster queries
CREATE INDEX idx_tickets_last_operator_response ON tickets (last_operator_response_at);

-- Add comments
COMMENT ON COLUMN tickets.last_operator_response_at IS 'Timestamp of last operator message for RAG bucket logic';
COMMENT ON COLUMN tickets.rag_bucket_message_ids IS 'Comma-separated list of message IDs accumulated for RAG after last operator response';
