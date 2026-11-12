package com.example.sysarch.polarion.sso;

/**
 * {@code /polarion/sysarch-sso/page?project=<Polarion project>} (P4 T2): a whole page under Polarion's own address that
 * frames SysArch's embed page of the project through single sign-on. It can go into Polarion's navigation or a shortcut
 * as it is, so nobody has to build a LiveReport page by hand. Plain JDK, tested without Polarion.
 */
public final class ProjectPage {

    private ProjectPage() {
    }

    /** Polarion project ids: letters, digits, '_', '-', '.' (anything else is refused, not escaped into a URL). */
    public static boolean validProject(String project) {
        return project != null && project.matches("[A-Za-z0-9_.-]{1,100}");
    }

    public static String html(String project) {
        if (!validProject(project)) {
            return page("SysArch 架构", "<p style=\"padding:16px;color:#777\">请在地址中指定 Polarion 项目，例如 <code>?project=sysarch</code>。</p>");
        }
        String url = StartServlet.html(WorkItemPanel.startUrl(project, null));
        return page("SysArch 架构 – " + project,
                "<div style=\"display:flex;flex-direction:column;height:100vh;margin:0\">"
                        + "<div style=\"padding:6px 12px;font:13px sans-serif;color:#555;border-bottom:1px solid #d9e1dd\">SysArch 架构 · "
                        + StartServlet.html(project) + " · <a href=\"" + url + "\" target=\"_blank\" rel=\"noopener\">在新标签页中打开</a></div>"
                        + "<iframe src=\"" + url + "\" title=\"SysArch 架构模型\" style=\"flex:1;width:100%;border:0\"></iframe></div>");
    }

    private static String page(String title, String body) {
        return "<!DOCTYPE html><html lang=\"zh\"><head><meta charset=\"utf-8\"><title>" + StartServlet.html(title)
                + "</title></head><body style=\"margin:0\">" + body + "</body></html>";
    }
}
