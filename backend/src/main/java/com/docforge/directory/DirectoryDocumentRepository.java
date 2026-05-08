package com.docforge.directory;

import com.docforge.directory.model.DirectoryDocument;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface DirectoryDocumentRepository extends MongoRepository<DirectoryDocument, String> {
}
