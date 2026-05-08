package com.docforge.directory.dto;

import java.util.List;

public record CreateDirectoryRequest(String name, List<DirectoryFieldDto> fields) {}
