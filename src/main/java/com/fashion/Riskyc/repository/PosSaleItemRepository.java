package com.fashion.Riskyc.repository;

import com.fashion.Riskyc.entity.PosSaleItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface PosSaleItemRepository extends JpaRepository<PosSaleItem, UUID> {
}
