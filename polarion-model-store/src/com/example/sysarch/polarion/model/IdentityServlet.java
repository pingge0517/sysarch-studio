package com.example.sysarch.polarion.model;

import com.polarion.platform.core.PlatformContext;
import com.polarion.platform.security.ISecurityService;
import com.polarion.platform.security.login.AccessToken;
import com.polarion.alm.tracker.ITrackerService;
import com.polarion.platform.ITransactionService;
import java.io.IOException;
import java.security.PrivilegedExceptionAction;
import javax.security.auth.Subject;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/** Read-only production identity bridge. No model mutation, user-directory lookup or cookie fallback. */
public final class IdentityServlet extends HttpServlet {
    private ISecurityService security;
    private ITrackerService tracker;
    private ITransactionService transactions;
    private record Result(int status,String body) { }
    @Override public void init() {
        var platform=PlatformContext.getPlatform(); this.security=platform.lookupService(ISecurityService.class);
        this.tracker=platform.lookupService(ITrackerService.class); this.transactions=platform.lookupService(ITransactionService.class);
    }
    @Override protected void service(HttpServletRequest request,HttpServletResponse response) throws IOException {
        response.setHeader("Cache-Control","no-store"); response.setHeader("X-Content-Type-Options","nosniff");
        response.setContentType("application/json;charset=UTF-8");
        if(!"GET".equals(request.getMethod())) { response.setHeader("Allow","GET"); reject(response,405); return; }
        String header=request.getHeader("Authorization");
        if(header==null || !header.startsWith("Bearer ") || header.length()<=7 || header.length()>8192) { reject(response,401); return; }
        Subject subject;
        try {
            subject=this.security.login().from("sysarch-model-read")
                .authenticator(AccessToken.id()).with(AccessToken.token(header.substring(7))).perform();
        } catch(Exception failure) { reject(response,401); return; }
        try {
            Result result=this.security.doAsUser(subject,(PrivilegedExceptionAction<Result>)()->{
                String actor=this.security.getCurrentUser();
                if(actor==null || actor.isBlank()) return failure(401);
                String common="\"protocol\":\"sysarch-model-read/1\",\"authentication\":\"sdk-access-token\",\"actor\":\""+escape(actor)+"\"";
                if("/identity".equals(request.getServletPath())) return new Result(200,"{"+common+"}");
                if(!"/revision".equals(request.getServletPath())) return failure(404);
                String project=request.getParameter("project"),id=request.getParameter("workItem");
                if(!identifier(project) || !identifier(id)) return failure(400);
                return this.transactions.execute().callable(()->{
                    var context=this.tracker.getTrackerProject(project);
                    if(context.isUnresolvable() || !context.can().read()) return failure(403);
                    var item=this.tracker.getWorkItem(project,id);
                    if(item.isUnresolvable() || !item.can().read()) return failure(403);
                    String revision=String.valueOf(item.getLastRevision());
                    if(!revision.matches("[0-9]{1,20}")) return failure(503);
                    return new Result(200,"{"+common+",\"project\":\""+escape(project)+"\",\"workItem\":\""+escape(id)+"\",\"revision\":\""+revision+"\"}");
                });
            });
            response.setStatus(result.status()); response.getWriter().write(result.body());
        } catch(Exception failure) { reject(response,503); }
        finally { try { this.security.logout(subject); } catch(Exception ignored) { } }
    }
    private static void reject(HttpServletResponse response,int status) throws IOException {
        response.setStatus(status); response.getWriter().write("{\"error\":\"identity-unavailable\"}");
    }
    private static Result failure(int status) { return new Result(status,"{\"error\":\"read-unavailable\"}"); }
    private static boolean identifier(String value) { return value!=null && value.matches("[A-Za-z0-9_-]{1,128}"); }
    private static String escape(String value) {
        StringBuilder result=new StringBuilder();
        for(char c:value.toCharArray()) {
            if(c=='"' || c=='\\') result.append('\\').append(c);
            else if(c<32) result.append(String.format("\\u%04x",(int)c));
            else result.append(c);
        }
        return result.toString();
    }
}
