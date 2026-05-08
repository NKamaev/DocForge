package com.docforge.template;

import com.docforge.template.model.TemplateVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TemplateVersionRepository extends JpaRepository<TemplateVersion, UUID> {

    List<TemplateVersion> findByTemplateIdOrderByVersionNumberDesc(UUID templateId);

    Optional<TemplateVersion> findByTemplateIdAndVersionNumber(UUID templateId, int versionNumber);

    @Query("SELECT COALESCE(MAX(v.versionNumber), 0) FROM TemplateVersion v WHERE v.templateId = :templateId")
    int findMaxVersionNumber(UUID templateId);
}
