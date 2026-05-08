package com.docforge.directory;

import com.docforge.directory.model.DirectoryRegistry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface DirectoryRegistryRepository extends JpaRepository<DirectoryRegistry, UUID> {
    List<DirectoryRegistry> findAllByOrderByCreatedAtDesc();
}
