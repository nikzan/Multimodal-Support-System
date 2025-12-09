-- Изменение размерности вектора эмбеддингов с 1536 (OpenAI) на 768 (nomic-embed-text)
ALTER TABLE knowledge_base 
ALTER COLUMN embedding TYPE vector(768);

-- Пересоздаем индекс для новой размерности
DROP INDEX IF EXISTS idx_knowledge_base_embedding;
CREATE INDEX idx_knowledge_base_embedding ON knowledge_base 
USING hnsw (embedding vector_cosine_ops);
