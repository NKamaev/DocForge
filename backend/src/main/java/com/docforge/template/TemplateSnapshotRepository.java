package com.docforge.template;

import com.docforge.template.model.TemplateSnapshot;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;
import java.util.UUID;

public interface TemplateSnapshotRepository extends MongoRepository<TemplateSnapshot, UUID> {

    Optional<TemplateSnapshot> findByTemplateIdAndVersionNumber(UUID templateId, int versionNumber);
}
