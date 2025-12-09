-- Изменяем тип embedding с vector на TEXT для совместимости с Hibernate
-- Будем использовать CAST в запросах для преобразования

-- Сначала удаляем индексы
DROP INDEX IF EXISTS idx_kb_embedding;
DROP INDEX IF EXISTS idx_knowledge_base_embedding;

-- Теперь меняем тип
ALTER TABLE knowledge_base 
ALTER COLUMN embedding TYPE TEXT;

-- Создадим индекс позже после того как начнем заполнять данные
-- CREATE INDEX idx_kb_embedding ON knowledge_base 
-- USING hnsw ((embedding::vector(768)) vector_cosine_ops);
