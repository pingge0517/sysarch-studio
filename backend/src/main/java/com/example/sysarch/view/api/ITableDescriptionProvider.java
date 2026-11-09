package com.example.sysarch.view.api;

/** Implemented by the providers vsm-compiler generates for VSM tables (P1, {@code kind: table}). */
public interface ITableDescriptionProvider {

    TableDescription build();
}
