package com.example.sysarch.config;

import com.example.sysarch.model.SysarchPackage;
import com.example.sysarch.model.provider.SysarchItemProviderAdapterFactory;

import org.eclipse.emf.common.notify.AdapterFactory;
import org.eclipse.emf.ecore.EPackage;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers the sysarch metamodel with Sirius Web (spec T2 step 4).
 *
 * <p>Sirius Web builds its global {@code EPackage.Registry} from every {@link EPackage} bean and its
 * {@code ComposedAdapterFactory} from every {@link AdapterFactory} bean ({@code EMFConfiguration} in
 * sirius-components-emf). Because the generated Java EPackage can be registered directly, the
 * Ecore-to-JSON conversion ({@code compileEcoreToJson}) is not needed.
 */
@Configuration
public class SysarchEMFConfiguration {

    @Bean
    public EPackage sysarchEPackage() {
        return SysarchPackage.eINSTANCE;
    }

    @Bean
    public AdapterFactory sysarchAdapterFactory() {
        return new SysarchItemProviderAdapterFactory();
    }
}
