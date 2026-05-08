package com.docforge.directory.dto;

import java.util.List;

public record DirectoryFieldDto(String key, String label, String type, List<String> options, int order) {}
