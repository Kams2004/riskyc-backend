package com.fashion.Riskyc.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "categories")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Category {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true)
    private String slug;

    @Column(nullable = false)
    private String name;

    /** Emoji or icon token shown next to the category in the storefront nav. */
    private String icon;

    /** MinIO object key for the cover photo shown on storefront category cards — null until an admin uploads one. */
    private String imageStorageKey;

    /** Which admin created this category — a name snapshot, not a live FK. */
    private String createdByName;

    @OneToMany(mappedBy = "category", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<Subcategory> subcategories = new ArrayList<>();
}
