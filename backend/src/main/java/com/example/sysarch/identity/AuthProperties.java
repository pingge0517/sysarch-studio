package com.example.sysarch.identity;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code sysarch.auth} (P2 T8). {@code mode=polarion} (default): a user is who their Polarion token says, verified
 * against Polarion; nothing the client claims is believed. {@code mode=dev}: a typed name (the {@code sysarch_user}
 * cookie or {@code X-Sysarch-User}, Polarion roles in {@code X-Sysarch-Roles}) for local and load tests; never in
 * production.
 */
@ConfigurationProperties("sysarch.auth")
public class AuthProperties {

    public enum Mode {
        POLARION, DEV
    }

    private Mode mode = Mode.POLARION;

    /** HMAC key of the session cookie; unset = a random key per start (every session ends with a restart). */
    private String sessionSecret = "";

    private Duration sessionTtl = Duration.ofHours(8);

    /** The cookie's Secure flag (behind HTTPS). */
    private boolean secureCookie = false;

    /**
     * The cookie's Domain (e.g. {@code sysarch.example.com}) when the shell and Sirius Web are served on different
     * hosts of one domain; empty = the host that answered (enough when only ports differ, as locally).
     */
    private String cookieDomain = "";

    public String getCookieDomain() {
        return this.cookieDomain;
    }

    public void setCookieDomain(String cookieDomain) {
        this.cookieDomain = cookieDomain;
    }

    /** How long a token's user and roles are reused for API calls that send the token instead of the cookie. */
    private Duration tokenCacheTtl = Duration.ofMinutes(5);

    public Mode getMode() {
        return this.mode;
    }

    public void setMode(Mode mode) {
        this.mode = mode;
    }

    public String getSessionSecret() {
        return this.sessionSecret;
    }

    public void setSessionSecret(String sessionSecret) {
        this.sessionSecret = sessionSecret;
    }

    public Duration getSessionTtl() {
        return this.sessionTtl;
    }

    public void setSessionTtl(Duration sessionTtl) {
        this.sessionTtl = sessionTtl;
    }

    public boolean isSecureCookie() {
        return this.secureCookie;
    }

    public void setSecureCookie(boolean secureCookie) {
        this.secureCookie = secureCookie;
    }

    public Duration getTokenCacheTtl() {
        return this.tokenCacheTtl;
    }

    public void setTokenCacheTtl(Duration tokenCacheTtl) {
        this.tokenCacheTtl = tokenCacheTtl;
    }

    public boolean dev() {
        return this.mode == Mode.DEV;
    }

    /**
     * Polarion single sign-on (embedding in Polarion): the HMAC key shared with the SysArch extension installed in
     * Polarion, which signs who is logged in there. Unset = no single sign-on (503); the token login still works.
     */
    private String polarionSsoSecret = "";

    /** The longest an assertion may live (the extension issues them for 60 s). */
    private Duration polarionSsoMaxAge = Duration.ofMinutes(2);

    /**
     * The session cookie's SameSite: {@code Lax} (default; enough when Polarion and SysArch are the same site, e.g.
     * two hosts of one company domain) or {@code None} (Polarion on another site; needs {@code secure-cookie} and a
     * browser that allows third-party cookies in the frame).
     */
    private String cookieSameSite = "Lax";

    /**
     * Who may show our pages in a frame: the CSP {@code frame-ancestors} sources besides {@code 'self'}, e.g. the
     * Polarion origin. Empty = the origin of {@code sysarch.polarion.base-url}.
     */
    private String frameAncestors = "";

    public String getPolarionSsoSecret() {
        return this.polarionSsoSecret;
    }

    public void setPolarionSsoSecret(String polarionSsoSecret) {
        this.polarionSsoSecret = polarionSsoSecret;
    }

    public Duration getPolarionSsoMaxAge() {
        return this.polarionSsoMaxAge;
    }

    public void setPolarionSsoMaxAge(Duration polarionSsoMaxAge) {
        this.polarionSsoMaxAge = polarionSsoMaxAge;
    }

    public String getCookieSameSite() {
        return this.cookieSameSite;
    }

    public void setCookieSameSite(String cookieSameSite) {
        this.cookieSameSite = cookieSameSite;
    }

    public String getFrameAncestors() {
        return this.frameAncestors;
    }

    public void setFrameAncestors(String frameAncestors) {
        this.frameAncestors = frameAncestors;
    }
}
