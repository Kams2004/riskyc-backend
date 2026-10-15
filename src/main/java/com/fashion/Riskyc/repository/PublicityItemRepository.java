package com.fashion.Riskyc.repository;

import com.fashion.Riskyc.entity.PublicityItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PublicityItemRepository extends JpaRepository<PublicityItem, UUID> {
    /** Customer-facing carousel — only slides the admin has left active. */
    List<PublicityItem> findAllByActiveTrueOrderByPositionAsc();

    /** Admin management list — everything, active or not. */
    List<PublicityItem> findAllByOrderByPositionAsc();
}
