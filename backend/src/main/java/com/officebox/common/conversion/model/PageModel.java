package com.officebox.common.conversion.model;

import java.util.List;

public record PageModel(
    int pageNumber,
    double width,
    double height,
    List<PageBlock> blocks) {
  public PageModel {
    blocks = List.copyOf(blocks);
  }
}
