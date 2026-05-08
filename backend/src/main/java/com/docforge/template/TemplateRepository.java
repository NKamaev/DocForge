package com.docforge.template;

import com.docforge.template.model.Template;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.UUID;

public interface TemplateRepository extends MongoRepository<Template, UUID> {
}
