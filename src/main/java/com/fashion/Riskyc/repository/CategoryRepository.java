package com.fashion.Riskyc.repository;

import com.fashion.Riskyc.entity.Category;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CategoryRepository extends JpaRepository<Category, UUID> {

    @EntityGraph(attributePaths = "subcategories")
    List<Category> findAllByOrderByNameAsc();

    @EntityGraph(attributePaths = "subcategories")
    Optional<Category> findBySlug(String slug);

    boolean existsBySlug(String slug);
}
