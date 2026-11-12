package com.example.sysarch.polarion.sso;

import java.io.IOException;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/** {@code GET /polarion/sysarch-sso/page?project=}: the whole SysArch page of a Polarion project ({@link ProjectPage}). */
public class PageServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setHeader("Cache-Control", "no-store");
        response.setContentType("text/html;charset=UTF-8");
        String project = request.getParameter("project");
        if (!ProjectPage.validProject(project)) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        }
        response.getWriter().write(ProjectPage.html(project));
    }

    /** As the start page: Polarion's form login replays the original request as POST. */
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        this.doGet(request, response);
    }
}
