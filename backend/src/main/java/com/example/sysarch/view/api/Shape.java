package com.example.sysarch.view.api;

/** VSM {@code nodeStyles[].shape}. */
public enum Shape {
    RECTANGLE, ROUNDED_RECTANGLE, SQUARE,
    /** P3 T5: a filled disc (UML initial pseudo-state); Sirius Web's ellipse node with the style's colour as fill. */
    FILLED_CIRCLE,
    /** P3 T5: a ring around a disc (UML final state); an image node (served SVG). */
    DOUBLE_CIRCLE,
    /** P3 T6: an ellipse with the label inside (use case); Sirius Web's ellipse node. */
    ELLIPSE,
    /** P3 T6: a diamond (decision); an image node (served SVG), label outside. */
    DIAMOND,
    /** P3 T6: a stick figure (actor); an image node (served SVG), label outside. */
    STICK_FIGURE
}
