package com.nova.support.repository;

import com.nova.support.domain.entity.KnowledgeBase;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Репозиторий для работы с базой знаний
 */
@Repository
public interface KnowledgeBaseRepository extends JpaRepository<KnowledgeBase, Long> {
    
    /**
     * Найти все записи базы знаний проекта с пагинацией
     * @param projectId ID проекта
     * @param pageable параметры пагинации
     * @return страница записей
     */
    Page<KnowledgeBase> findByProjectId(Long projectId, Pageable pageable);
    
    /**
     * Найти записи базы знаний по типу источника
     * @param projectId ID проекта
     * @param sourceType тип источника
     * @param pageable параметры пагинации
     * @return страница записей
     */
    Page<KnowledgeBase> findByProjectIdAndSourceType(Long projectId, String sourceType, Pageable pageable);
    
    /**
     * Поиск по содержимому (полнотекстовый поиск)
     * @param projectId ID проекта
     * @param keyword ключевое слово
     * @param pageable параметры пагинации
     * @return страница записей
     */
    @Query("SELECT kb FROM KnowledgeBase kb WHERE kb.project.id = :projectId " +
           "AND (LOWER(kb.title) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           "OR LOWER(kb.content) LIKE LOWER(CONCAT('%', :keyword, '%')))")
    Page<KnowledgeBase> searchByKeyword(@Param("projectId") Long projectId, 
                                         @Param("keyword") String keyword, 
                                         Pageable pageable);
    
    /**
     * Семантический поиск по вектору (для RAG)
     * Найти топ-K наиболее похожих записей используя cosine similarity
     * @param projectId ID проекта
     * @param queryEmbedding вектор запроса
     * @param limit количество результатов
     * @return список записей отсортированных по релевантности
     */
    @Query(value = "SELECT * FROM knowledge_base " +
                   "WHERE project_id = :projectId " +
                   "ORDER BY embedding <=> CAST(:queryEmbedding AS vector) " +
                   "LIMIT :limit", 
           nativeQuery = true)
    List<KnowledgeBase> findSimilarByEmbedding(@Param("projectId") Long projectId,
                                                @Param("queryEmbedding") float[] queryEmbedding,
                                                @Param("limit") int limit);
}
