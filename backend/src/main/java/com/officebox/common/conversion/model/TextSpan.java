package com.officebox.common.conversion.model;

public record TextSpan(
    String text,
    BoundingBox bounds,
    String fontName,
    double fontSize,
    int red,
    int green,
    int blue,
    boolean bold,
    boolean italic) {
}
