package com.iflytek.skillhub.dto.publication;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record PublicationSaveRequest(@PositiveOrZero long revision, @NotNull @Valid PublicationDraft draft) {}
