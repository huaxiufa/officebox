package com.officebox.common.conversion.model;

public sealed interface PageBlock permits TextBlock, ImageBlock, TableBlock {
  BoundingBox bounds();
}
