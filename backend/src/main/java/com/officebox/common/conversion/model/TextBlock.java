package com.officebox.common.conversion.model;

import java.util.List;

public record TextBlock(BoundingBox bounds, List<TextSpan> spans, boolean heading) implements PageBlock {
  public TextBlock {
    spans = List.copyOf(spans);
  }

  public String text() {
    return spans.stream().map(TextSpan::text).reduce("", String::concat);
  }
}
