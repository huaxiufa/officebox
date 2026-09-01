package com.officebox.common.conversion.model;

/** Coordinates in PDF points, measured from the top-left corner of the page. */
public record BoundingBox(double x, double y, double width, double height) {
  public double right() { return x + width; }
  public double bottom() { return y + height; }

  public boolean contains(BoundingBox other) {
    return other.x() >= x && other.y() >= y
        && other.right() <= right() && other.bottom() <= bottom();
  }
}
