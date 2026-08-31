package com.officebox.common.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "officebox.storage")
public record StorageProperties(String root, long maxRetentionHours) {
}
