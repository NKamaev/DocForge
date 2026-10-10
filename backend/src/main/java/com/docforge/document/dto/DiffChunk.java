package com.docforge.document.dto;

public record DiffChunk(
        DiffChunkType type,
        String text
) {
    public enum DiffChunkType {
        INSERT,
        DELETE,
        EQUAL
    }
}
