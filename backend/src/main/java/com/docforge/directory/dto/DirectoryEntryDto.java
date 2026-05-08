package com.docforge.directory.dto;

import java.util.Map;

public record DirectoryEntryDto(String id, Map<String, Object> values) {}
