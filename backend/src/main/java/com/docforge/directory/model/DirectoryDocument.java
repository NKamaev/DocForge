package com.docforge.directory.model;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * MongoDB document that stores all entries for a directory.
 * The {@code _id} matches the UUID of {@link DirectoryRegistry}.
 */
@Document(collection = "directories")
@Getter @Setter
public class DirectoryDocument {

    @Id
    private String id;   // UUID string, same as DirectoryRegistry.id

    private List<DirectoryEntry> entries = new ArrayList<>();

    @Getter @Setter
    public static class DirectoryEntry {
        private String id;                    // UUID string
        private Map<String, Object> values;   // fieldKey → value
    }
}
