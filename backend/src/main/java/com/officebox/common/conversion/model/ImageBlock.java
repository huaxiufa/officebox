package com.officebox.common.conversion.model;

/** A raster image extracted from the PDF content stream. */
public record ImageBlock(BoundingBox bounds, String mimeType, byte[] data) implements PageBlock {
  public ImageBlock {
    data = data.clone();
  }

  @Override
  public byte[] data() {
    return data.clone();
  }
}
