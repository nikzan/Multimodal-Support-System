package com.nova.support.repository;

import com.nova.support.domain.entity.Project;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Репозиторий для работы с проектами (tenants)
 */
@Repository
public interface ProjectRepository extends JpaRepository<Project, Long> {
    
    /**
     * Найти проект по API ключу
     * @param apiKey API ключ проекта
     * @return проект или пустой Optional
     */
    Optional<Project> findByApiKey(String apiKey);
    
    /**
     * Проверить существование проекта по API ключу
     * @param apiKey API ключ
     * @return true если проект существует
     */
    boolean existsByApiKey(String apiKey);
}
