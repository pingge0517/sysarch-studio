package com.example.sysarch.polarion.sso;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * The "SysArch 架构" panel on a Work Item form (the form extension {@code sysarchArchitecture}): SysArch's embed page
 * for this Work Item, framed through the single sign-on start page, so the element bound to the Work Item is one click
 * away. Plain JDK, so it is tested without Polarion; the form extension only passes the Work Item's project and id.
 */
public final class WorkItemPanel {

    /** Where the extension's web app is served (plugin.xml). */
    public static final String START = "/polarion/sysarch-sso/start";

    private WorkItemPanel() {
    }

    /** The start URL that signs in and lands on the embed page of this Work Item. */
    public static String startUrl(String projectId, String workItemId) {
        String target = "/?embed=1&polarionProject=" + encode(projectId) + (workItemId == null || workItemId.isBlank() ? "" : "&workItem=" + encode(workItemId));
        return START + "?return=" + encode(target);
    }

    /** The panel's HTML: a frame, and a link that opens the same page in a new tab; nothing for an unsaved Work Item. */
    public static String html(String projectId, String workItemId, boolean persisted) {
        if (!persisted || projectId == null || projectId.isBlank() || workItemId == null || workItemId.isBlank()) {
            return "<div style=\"color:#777\">保存工作项后可在 SysArch 中查看对应的模型元素。</div>";
        }
        String url = StartServlet.html(startUrl(projectId, workItemId));
        return "<div class=\"sysarch-panel\">"
                + "<iframe src=\"" + url + "\" title=\"SysArch 架构模型\" style=\"width:100%;height:260px;border:1px solid #d9e1dd\" loading=\"lazy\"></iframe>"
                + "<div style=\"margin-top:4px\"><a href=\"" + url + "\" target=\"_blank\" rel=\"noopener\">在新标签页中打开 SysArch</a></div>"
                + "</div>";
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
