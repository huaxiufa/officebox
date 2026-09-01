package com.officebox.common.task;

public record TaskProgress(int percent, String message) {
  public TaskProgress {
    if (percent < 0 || percent > 100) {
      throw new IllegalArgumentException("percent must be between 0 and 100");
    }
  }
}
