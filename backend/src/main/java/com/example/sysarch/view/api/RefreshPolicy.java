package com.example.sysarch.view.api;

/**
 * VSM {@code refresh}. Only incremental refresh exists: redrawing the whole diagram is forbidden (spec §5.2).
 *
 * @param budgetMs time budget for one refresh, in milliseconds
 */
public record RefreshPolicy(int budgetMs) {

    public static RefreshPolicy incremental(int budgetMs) {
        return new RefreshPolicy(budgetMs);
    }
}
