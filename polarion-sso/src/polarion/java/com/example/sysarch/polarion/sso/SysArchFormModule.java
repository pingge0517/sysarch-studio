package com.example.sysarch.polarion.sso;

import com.google.inject.AbstractModule;
import com.google.inject.multibindings.Multibinder;
import com.polarion.alm.ui.server.forms.extensions.FormExtensionContribution;

/** Registers the form extension with Polarion (the bundle's {@code Guice-Modules} header names this class). */
public class SysArchFormModule extends AbstractModule {

    @Override
    protected void configure() {
        Multibinder.newSetBinder(binder(), FormExtensionContribution.class).addBinding()
                .toInstance(new FormExtensionContribution(SysArchFormExtension.class, SysArchFormExtension.ID));
    }
}
