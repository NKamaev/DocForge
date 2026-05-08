package com.docforge.directory;

import com.docforge.directory.model.DirectorySchemaField;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface DirectorySchemaFieldRepository extends JpaRepository<DirectorySchemaField, UUID> {
    List<DirectorySchemaField> findByDirectoryIdOrderByDisplayOrderAsc(UUID directoryId);
    void deleteByDirectoryId(UUID directoryId);
}
