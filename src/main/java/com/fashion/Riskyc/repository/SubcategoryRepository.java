package com.fashion.Riskyc.repository;

import com.fashion.Riskyc.entity.Subcategory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SubcategoryRepository extends JpaRepository<Subcategory, UUID> {
    List<Subcategory> findByCategoryId(UUID categoryId);
}
