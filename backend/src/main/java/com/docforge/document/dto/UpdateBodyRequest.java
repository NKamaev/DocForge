package com.docforge.document.dto;

import jakarta.validation.constraints.NotNull;

public record UpdateBodyRequest(@NotNull Object currentBody) {}
