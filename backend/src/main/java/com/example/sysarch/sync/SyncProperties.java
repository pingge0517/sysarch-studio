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
