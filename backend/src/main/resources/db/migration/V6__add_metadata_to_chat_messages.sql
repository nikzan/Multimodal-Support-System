-- Add metadata column to chat_messages for storing transcriptions and image descriptions
ALTER TABLE chat_messages
ADD COLUMN metadata JSONB;

-- Create index for faster metadata queries
CREATE INDEX idx_chat_messages_metadata ON chat_messages USING GIN (metadata);

-- Add comment
COMMENT ON COLUMN chat_messages.metadata IS 'Stores transcription for audio, description for images, and other metadata';
