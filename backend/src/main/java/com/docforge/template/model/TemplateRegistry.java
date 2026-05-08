package com.docforge.template.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "template_registry")
@Getter
@Setter
@NoArgsConstructor
public class TemplateRegistry {

    @Id
    private UUID id;

    @Column(length = 100, unique = true)
    private String code;

    @Column(length = 500, nullable = false)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(length = 100)
    private String category;

    @Column(nullable = false)
    private int version = 1;

    @Column(name = "is_published", nullable = false)
    private boolean published = false;

    @Column(name = "owner_id")
    private UUID ownerId;

    @Column(name = "default_folder_id")
    private UUID defaultFolderId;

    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = OffsetDateTime.now();
    }
}
