package com.example.sysarch;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point of SysArch Studio.
 *
 * <p>Sirius Web is wired in through its own auto-configuration ({@code SiriusWebStarterConfiguration});
 * this application only adds our packages on top of it (spec §2.3: additive only, never patch Sirius Web).
 */
@SpringBootApplication
public class SysarchApplication {

    public static void main(String[] args) {
        SpringApplication.run(SysarchApplication.class, args);
    }
}
