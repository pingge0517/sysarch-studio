package com.example.sysarch.view.api;

/** VSM {@code autoExpand[]}: follow {@code feature} from the diagram root, up to {@code maxDepth} levels. */
public record AutoExpand(String feature, int maxDepth) {
}
