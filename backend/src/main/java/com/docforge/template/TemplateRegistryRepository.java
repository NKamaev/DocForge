package com.docforge.template;

import com.docforge.template.model.TemplateRegistry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.UUID;

public interface TemplateRegistryRepository
        extends JpaRepository<TemplateRegistry, UUID>, JpaSpecificationExecutor<TemplateRegistry> {
}
