package com.example.sysarch.view.sirius;

import java.util.List;

import org.eclipse.sirius.components.core.api.IImagePathService;
import org.springframework.stereotype.Service;

/**
 * P3 T5: lets Sirius Web's image controller serve our node pictures ({@code /api/images/sysarch-shapes/…}) from the
 * classpath folder {@code sysarch-shapes/}, e.g. the final state's ring around a disc ({@code double-circle}).
 * Additive: Sirius collects every {@link IImagePathService}.
 */
@Service
public class SysarchImages implements IImagePathService {

    static final String PATH = "/sysarch-shapes/";

    @Override
    public List<String> getPaths() {
        return List.of(PATH);
    }
}
