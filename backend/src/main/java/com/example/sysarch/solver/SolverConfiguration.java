package com.example.sysarch.solver;

import com.example.sysarch.audit.AuditLog;
import com.example.sysarch.project.ProjectModels;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** P3 T4: the three solvers (each available only when configured) and the solve service. */
@Configuration
@EnableConfigurationProperties(SolverProperties.class)
public class SolverConfiguration {

    @Bean
    public SolveService solveService(ProjectModels models, SolverProperties properties, ObjectMapper json, AuditLog audit) {
        return new SolveService(models, List.of(new PythonSolver(properties.getPython(), properties.getTimeout(), json),
                new ModelicaSolver(properties.getOmc(), properties.getTimeout()), new RemoteHttpSolver(properties.getRemoteUrl(), properties.getTimeout(), json)),
                audit);
    }
}
