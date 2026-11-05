package com.example.sysarch.config;

import com.example.sysarch.validation.ConstraintValidator;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** One OCL-backed constraint validator for the application; it compiles the Ecore invariants once. */
@Configuration
public class ValidationConfiguration {

    @Bean(destroyMethod = "close")
    public ConstraintValidator constraintValidator() {
        return new ConstraintValidator();
    }
}
