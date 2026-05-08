package com.docforge.directory.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "directory_schema_field")
@Getter @Setter
public class DirectorySchemaField {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "directory_id", nullable = false)
    private UUID directoryId;

    /** Snake_case key used as parameterCode suffix: bank__<fieldKey> */
    @Column(name = "field_key", nullable = false)
    private String fieldKey;

    @Column(nullable = false)
    private String label;

    /** TEXT | NUMBER | DATE | FIO | BOOLEAN | SELECT */
    @Column(name = "field_type", nullable = false)
    private String fieldType = "TEXT";

    /** Options for SELECT type (stored as JSON array) */
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(name = "field_options", columnDefinition = "jsonb")
    private java.util.List<String> fieldOptions;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;
}
