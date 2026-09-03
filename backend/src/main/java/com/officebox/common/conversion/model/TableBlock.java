package com.officebox.common.conversion.model;

import java.util.List;

/** A conservatively detected table whose cells remain editable in DOCX. */
public record TableBlock(BoundingBox bounds, List<List<TextBlock>> rows) implements PageBlock {
  public TableBlock {
    rows = rows.stream().map(List::copyOf).toList();
  }
}
