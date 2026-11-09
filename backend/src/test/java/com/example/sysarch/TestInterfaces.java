package com.example.sysarch;

import com.example.sysarch.model.InterfaceDef;
import com.example.sysarch.model.Layer;
import com.example.sysarch.model.SysarchFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Interface entities for tests written when {@code Port.interfaceType} was a string (P0). Since metamodel v1.1 it
 * refers to an {@link InterfaceDef}; one shared entity per name keeps "same name = same interface", which is what the
 * P0 tests mean (different names are unrelated interfaces, which {@code PortInterfaceAssignable} rejects).
 */
public final class TestInterfaces {

    private static final Map<String, InterfaceDef> BY_NAME = new ConcurrentHashMap<>();

    private TestInterfaces() {
    }

    /** The interface named {@code name}, or null for a null name (a port without interface). */
    public static InterfaceDef named(String name) {
        if (name == null) {
            return null;
        }
        return BY_NAME.computeIfAbsent(name, n -> {
            InterfaceDef interfaceDef = SysarchFactory.eINSTANCE.createInterfaceDef();
            interfaceDef.setId("IF-" + n);
            interfaceDef.setName(n);
            interfaceDef.setProtocol(n);
            interfaceDef.setLayer(Layer.LOGICAL);
            return interfaceDef;
        });
    }
}
