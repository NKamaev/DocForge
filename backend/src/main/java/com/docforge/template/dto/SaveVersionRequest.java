package com.docforge.template.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record SaveVersionRequest(
        @NotBlank @Pattern(regexp = "MAJOR|MINOR") String bumpType,
        String comment
) {}
