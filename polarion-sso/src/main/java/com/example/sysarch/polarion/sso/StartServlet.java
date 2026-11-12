package com.example.sysarch.polarion.sso;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * {@code GET /polarion/sysarch-sso/start?return=<path in SysArch>}: runs behind Polarion's own login (web.xml), so
 * {@link HttpServletRequest#getRemoteUser()} is the logged-in Polarion user. Answers a page that posts a signed,
 * 60-second assertion of that user to SysArch's {@code /api/v1/auth/polarion-sso}, which sets its session cookie and
 * goes on to {@code return}. A Polarion page embeds SysArch by framing this URL.
 *
 * <p>Configuration (Polarion's {@code polarion.properties}, read as system properties, or environment variables):
 * {@code sysarch.sso.secretFile} (a file holding the key; see {@link #secret()}) / {@code SYSARCH_SSO_SECRET}, the key shared with SysArch's
 * {@code SYSARCH_POLARION_SSO_SECRET}; {@code sysarch.sso.url} / {@code SYSARCH_SSO_URL}, SysArch's address as the
 * browser reaches it (e.g. {@code http://localhost:3000}). Nothing is stored; no password or token is read.
 */
public class StartServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setHeader("Cache-Control", "no-store");
        response.setContentType("text/html;charset=UTF-8");
        String user = request.getRemoteUser();
        String secret = secret();
        String url = setting("sysarch.sso.url", "SYSARCH_SSO_URL");
        if (user == null || user.isBlank()) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }
        if (secret == null || url == null) {
            response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            response.getWriter().write(page("SysArch", "<p>SysArch 单点登录未配置：请在 polarion.properties 中设置 sysarch.sso.secretFile（存放密钥的文件）和 sysarch.sso.url。</p>"));
            return;
        }
        String assertion = Assertions.issue(user, secret.getBytes(StandardCharsets.UTF_8), System.currentTimeMillis() / 1000);
        String action = url.replaceAll("/+$", "") + "/api/v1/auth/polarion-sso";
        String body = "<form id=\"sso\" method=\"post\" action=\"" + html(action) + "\">"
                + "<input type=\"hidden\" name=\"assertion\" value=\"" + html(assertion) + "\">"
                + "<input type=\"hidden\" name=\"return\" value=\"" + html(localPath(request.getParameter("return"))) + "\">"
                + "<noscript><button type=\"submit\">打开 SysArch</button></noscript></form>"
                + "<script>document.getElementById('sso').submit();</script>";
        PrintWriter out = response.getWriter();
        out.write(page("正在打开 SysArch…", body));
    }

    /**
     * The shared key: environment variable {@code SYSARCH_SSO_SECRET}, else the first line of the file
     * {@code sysarch.sso.secretFile} names. Never a property value: Polarion writes every {@code polarion.properties}
     * entry to its startup log (found when installing), so a key there would be in every log.
     */
    static String secret() {
        String value = System.getenv("SYSARCH_SSO_SECRET");
        if (value != null && !value.isBlank()) {
            return value.strip();
        }
        return readKey(System.getProperty("sysarch.sso.secretFile"));
    }

    static String readKey(String file) {
        if (file == null || file.isBlank()) {
            return null;
        }
        try {
            String text = java.nio.file.Files.readString(java.nio.file.Path.of(file.strip()), StandardCharsets.UTF_8).strip();
            String first = text.lines().findFirst().orElse("").strip();
            return first.isEmpty() ? null : first;
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    /**
     * After Polarion's form login the container replays the original request as the login's POST (found when
     * installing: the first visit ended on 405), so POST does the same as GET.
     */
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        this.doGet(request, response);
    }

    static String setting(String property, String env) {
        String value = System.getProperty(property);
        if (value == null || value.isBlank()) {
            value = System.getenv(env);
        }
        return value == null || value.isBlank() ? null : value.strip();
    }

    /** A path in SysArch only (the same rule as SysArch's own check). */
    static String localPath(String target) {
        if (target == null || !target.startsWith("/") || target.startsWith("//") || target.contains("\\") || target.chars().anyMatch(c -> c < 0x20)) {
            return "/";
        }
        return target;
    }

    static String html(String value) {
        return value.replace("&", "&amp;").replace("\"", "&quot;").replace("<", "&lt;").replace(">", "&gt;").replace("'", "&#39;");
    }

    private static String page(String title, String body) {
        return "<!DOCTYPE html><html lang=\"zh\"><head><meta charset=\"utf-8\"><title>" + html(title)
                + "</title></head><body style=\"font-family:sans-serif;color:#555\">" + body + "</body></html>";
    }
}
