package com.example.sysarch.view.api;

/** VSM {@code nodeStyles[].shape}. */
public enum Shape {
    RECTANGLE, ROUNDED_RECTANGLE, SQUARE,
    /** P3 T5: a filled disc (UML initial pseudo-state); Sirius Web's ellipse node with the style's colour as fill. */
    FILLED_CIRCLE,
    /** P3 T5: a ring around a disc (UML final state); an image node (served SVG). */
    DOUBLE_CIRCLE
}
