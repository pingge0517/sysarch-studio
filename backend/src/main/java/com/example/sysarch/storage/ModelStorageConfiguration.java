package com.example.sysarch.storage;

import com.example.sysarch.access.ProjectReadAccess;
import com.example.sysarch.project.ProjectModels;
import com.example.sysarch.storage.local.LocalModelRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Opt-in read adapters. The detached read controller shares this switch; remote mutation remains disabled. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "sysarch.polarion-store", name = "enabled", havingValue = "true")
public class ModelStorageConfiguration {
    @Bean
    ModelCodec modelCodec() { return new ModelCodec(); }

    @Bean
    LocalModelRepository localModelRepository(ProjectModels projects, ProjectReadAccess access, ModelCodec codec, ModelStorageRegistry registry) {
        return new LocalModelRepository(projects, access, codec, registry);
    }

    @Bean
    ModelRepositoryRouter modelRepositoryRouter(ModelStorageRegistry registry, LocalModelRepository local) {
        return new ModelRepositoryRouter(registry, local);
    }
}
