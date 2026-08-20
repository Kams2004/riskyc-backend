package com.fashion.Riskyc.repository;

import com.fashion.Riskyc.entity.Role;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface RoleRepository extends JpaRepository<Role, UUID> {
    boolean existsByNameIgnoreCase(String name);
}
