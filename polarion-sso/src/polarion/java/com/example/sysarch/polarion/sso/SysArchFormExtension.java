package com.example.sysarch.polarion.sso;

import com.polarion.alm.tracker.model.IWorkItem;
import com.polarion.alm.ui.server.forms.extensions.IFormExtension;
import com.polarion.platform.persistence.model.IPObject;

import java.util.Map;

/**
 * Work Item form extension {@value #ID}: the "SysArch 架构" panel ({@link WorkItemPanel}). Add it to a Work Item form
 * layout with {@code <extension id="sysarchArchitecture" label="SysArch 架构"/>}. Compiled against Polarion's own jars
 * (source set {@code polarion}), so it is built only where a Polarion installation is found.
 */
public class SysArchFormExtension implements IFormExtension {

    public static final String ID = "sysarchArchitecture";

    @Override
    public String render(IPObject object, Map<String, String> attributes) {
        if (!(object instanceof IWorkItem workItem)) {
            return "";
        }
        return WorkItemPanel.html(workItem.getProjectId(), workItem.getId(), workItem.isPersisted());
    }

    @Override
    public String getLabel(IPObject object, Map<String, String> attributes) {
        return "SysArch 架构";
    }
}
