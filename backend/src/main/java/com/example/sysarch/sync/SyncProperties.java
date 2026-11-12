package com.example.sysarch.sync;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code sysarch.polarion.*} (application.yml, spec §9.3).
 *
 * <ul>
 * <li>{@code projects}: Polarion project id to the Sirius Web project holding its model (the user's decision
 * "配置映射"). The value is a Sirius project id, or a project name.</li>
 * <li>{@code fields}: the Polarion custom fields that carry model-owned data (decision "自定义字段，可配置"). The Polarion
 * project must define them, read-only for users.</li>
 * <li>{@code work-item-types}: model element type to Polarion Work Item type, matching the OpenAPI Link enums.</li>
 * </ul>
 */
@ConfigurationProperties(prefix = "sysarch.polarion")
public class SyncProperties {

    /** Polarion server, e.g. {@code https://polarion.example.com/polarion}. */
    private String baseUrl = "";

    /** Calls per second to Polarion (spec §6.3). */
    private double rateLimitRps = 10;

    /** Retries after a 429 (spec §6.3). */
    private int maxRetries = 3;

    /** Shared secret for the HMAC-SHA256 signature of Polarion webhooks (X-Polarion-Signature); empty rejects every webhook. */
    private String webhookSecret = "";

    private Map<String, String> projects = new LinkedHashMap<>();

    private Fields fields = new Fields();

    /**
     * Token of a Polarion service account for the scheduled pull (P1 T6, user's decision), from POLARION_SYNC_TOKEN.
     * Configured by operations, read from the environment only: never stored in the database or logged. Empty: the
     * scheduled pull does not run. Every user-triggered call still uses the caller's own token.
     */
    private String syncToken = "";

    /** Polarion link roles for the model-owned trace (P1 T6): element Work Item {role} requirement Work Item. */
    private LinkRoles linkRoles = new LinkRoles();

    private Schedule schedule = new Schedule();

    public static class LinkRoles {

        private String satisfiedBy = "implements";

        private String verifiedBy = "verifies";

        public String getSatisfiedBy() {
            return this.satisfiedBy;
        }

        public void setSatisfiedBy(String satisfiedBy) {
            this.satisfiedBy = satisfiedBy;
        }

        public String getVerifiedBy() {
            return this.verifiedBy;
        }

        public void setVerifiedBy(String verifiedBy) {
            this.verifiedBy = verifiedBy;
        }
    }

    /** The scheduled incremental pull (P1 T6 step 2). */
    public static class Schedule {

        /**
         * false = no scheduled pull even with a service token (the token may be there only for Polarion single sign-on,
         * which reads users' roles with it). Env SYSARCH_SCHEDULED_PULL.
         */
        private boolean enabled = true;

        public boolean isEnabled() {
            return this.enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        private java.time.Duration interval = java.time.Duration.ofMinutes(15);

        /** After a failure, one retry after this delay. */
        private java.time.Duration retryDelay = java.time.Duration.ofMinutes(2);

        /** Consecutive failures that pause the scheduled pull and raise the alert. */
        private int maxFailures = 3;

        /** How often the scheduler checks whether a pull is due. */
        private java.time.Duration tick = java.time.Duration.ofSeconds(30);

        public java.time.Duration getInterval() {
            return this.interval;
        }

        public void setInterval(java.time.Duration interval) {
            this.interval = interval;
        }

        public java.time.Duration getRetryDelay() {
            return this.retryDelay;
        }

        public void setRetryDelay(java.time.Duration retryDelay) {
            this.retryDelay = retryDelay;
        }

        public int getMaxFailures() {
            return this.maxFailures;
        }

        public void setMaxFailures(int maxFailures) {
            this.maxFailures = maxFailures;
        }

        public java.time.Duration getTick() {
            return this.tick;
        }

        public void setTick(java.time.Duration tick) {
            this.tick = tick;
        }
    }

    public String getSyncToken() {
        return this.syncToken;
    }

    public void setSyncToken(String syncToken) {
        this.syncToken = syncToken;
    }

    public LinkRoles getLinkRoles() {
        return this.linkRoles;
    }

    public void setLinkRoles(LinkRoles linkRoles) {
        this.linkRoles = linkRoles;
    }

    public Schedule getSchedule() {
        return this.schedule;
    }

    public void setSchedule(Schedule schedule) {
        this.schedule = schedule;
    }

    /** Ordered, so the Polarion queries built from it are the same on every run. */
    private Map<String, String> workItemTypes = defaultWorkItemTypes();

    /** Polarion custom field ids for model-owned values, and the standard fields Polarion owns. */
    public static class Fields {

        private String elementId = "sysarchElementId";

        private String name = "sysarchName";

        private String layer = "sysarchLayer";

        private String stereotypes = "sysarchStereotypes";

        private String source = "sysarchSource";

        private String target = "sysarchTarget";

        public String getElementId() {
            return this.elementId;
        }

        public void setElementId(String elementId) {
            this.elementId = elementId;
        }

        public String getName() {
            return this.name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getLayer() {
            return this.layer;
        }

        public void setLayer(String layer) {
            this.layer = layer;
        }

        public String getStereotypes() {
            return this.stereotypes;
        }

        public void setStereotypes(String stereotypes) {
            this.stereotypes = stereotypes;
        }

        public String getSource() {
            return this.source;
        }

        public void setSource(String source) {
            this.source = source;
        }

        public String getTarget() {
            return this.target;
        }

        public void setTarget(String target) {
            this.target = target;
        }
    }

    private static Map<String, String> defaultWorkItemTypes() {
        Map<String, String> types = new LinkedHashMap<>();
        types.put("PartDefinition", "architecture_element");
        types.put("Part", "architecture_element");
        types.put("Port", "architecture_element");
        types.put("Connection", "architecture_connection");
        types.put("Requirement", "requirement");
        return types;
    }

    public String getWebhookSecret() {
        return this.webhookSecret;
    }

    public void setWebhookSecret(String webhookSecret) {
        this.webhookSecret = webhookSecret;
    }

    public String getBaseUrl() {
        return this.baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public double getRateLimitRps() {
        return this.rateLimitRps;
    }

    public void setRateLimitRps(double rateLimitRps) {
        this.rateLimitRps = rateLimitRps;
    }

    public int getMaxRetries() {
        return this.maxRetries;
    }

    public void setMaxRetries(int maxRetries) {
        this.maxRetries = maxRetries;
    }

    public Map<String, String> getProjects() {
        return this.projects;
    }

    public void setProjects(Map<String, String> projects) {
        this.projects = projects;
    }

    public Fields getFields() {
        return this.fields;
    }

    public void setFields(Fields fields) {
        this.fields = fields;
    }

    public Map<String, String> getWorkItemTypes() {
        return this.workItemTypes;
    }

    public void setWorkItemTypes(Map<String, String> workItemTypes) {
        this.workItemTypes = workItemTypes;
    }
}
