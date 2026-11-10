package com.example.sysarch.session;

import java.util.OptionalInt;

/** A project whose limit of concurrent editors is not the default (P2 T3: a branch workspace allows one writer). */
public interface ProjectLimits {

    /** The limit for this project, or empty for the default. */
    OptionalInt limit(String projectId);
}
