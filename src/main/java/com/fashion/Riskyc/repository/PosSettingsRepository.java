package com.fashion.Riskyc.repository;

import com.fashion.Riskyc.entity.PosSettings;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PosSettingsRepository extends JpaRepository<PosSettings, UUID> {
    /** There's only ever one row — see PosSettings' own javadoc. */
    default PosSettings findTheOnlyOne() {
        List<PosSettings> all = findAll();
        return all.isEmpty() ? null : all.get(0);
    }
}
