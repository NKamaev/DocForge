package com.docforge.directory.dto;

import java.util.List;

public record UpdateDirectoryRequest(String name, List<DirectoryFieldDto> fields) {}
