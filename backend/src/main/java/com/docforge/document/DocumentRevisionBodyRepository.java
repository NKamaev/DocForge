package com.docforge.document;

import com.docforge.document.model.DocumentRevisionBody;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.UUID;

public interface DocumentRevisionBodyRepository extends MongoRepository<DocumentRevisionBody, UUID> {
}
