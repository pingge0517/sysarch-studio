package com.example.sysarch.view.api;

/**
 * The direction a diagram is arranged in (VSM {@code layoutDirection}): Sirius' arrange-all lays the diagram out with ELK's
 * layered algorithm in this direction, and the single interface runs it once when a new diagram first opens.
 */
public enum LayoutDirection {
    RIGHT, DOWN, LEFT, UP
}
