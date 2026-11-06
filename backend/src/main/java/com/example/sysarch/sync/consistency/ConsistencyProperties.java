package com.example.sysarch.sync.consistency;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code sysarch.consistency}: the R2 repair strategy, fixed per project (spec §7: "该选择按项目固定").
 *
 * <p>{@code strategy} ({@code CONSISTENCY_STRATEGY}) is the default; {@code strategies.<polarion project>} overrides
 * it for one project. A repair request with another strategy is refused, so nobody can switch it per issue.
 */
@ConfigurationProperties("sysarch.consistency")
public class ConsistencyProperties {

    public static final String MODEL_WINS = "MODEL_WINS";

    public static final String POLARION_WINS = "POLARION_WINS";

    private String strategy = "";

    private Map<String, String> strategies = new LinkedHashMap<>();

    /** The configured strategy of a project, or null if none is configured. */
    public String strategyOf(String polarionProjectId) {
        String configured = this.strategies.getOrDefault(polarionProjectId, this.strategy);
        return configured == null || configured.isBlank() ? null : configured.trim();
    }

    public String getStrategy() {
        return this.strategy;
    }

    public void setStrategy(String strategy) {
        this.strategy = strategy;
    }

    public Map<String, String> getStrategies() {
        return this.strategies;
    }

    public void setStrategies(Map<String, String> strategies) {
        this.strategies = strategies;
    }
}
