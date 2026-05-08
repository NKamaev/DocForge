package com.docforge.document;

import com.docforge.document.model.Document;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.UUID;

public interface DocumentRepository extends MongoRepository<Document, UUID> {}
