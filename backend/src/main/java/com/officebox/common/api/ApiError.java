package com.officebox.common.api;

import java.time.Instant;

public record ApiError(
    String code,
    String message,
    String path,
    Instant timestamp
) {}
