package com.example.sysarch.solver;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code sysarch.solver} (P3 T4). Unset = that solver is not available (503 when asked for).
 * <ul>
 * <li>{@code python}: the Python with NumPy and SciPy that runs the spec's {@code solver/python_solver.py}
 * ({@code SYSARCH_SOLVER_PYTHON}; on this machine {@code C:\SysArchSolver\python\python.exe}).</li>
 * <li>{@code remote-url}: an HTTP solver service with the script's JSON contract ({@code SYSARCH_SOLVER_REMOTE_URL};
 * {@code solver/http_server.py} is one, the Helm chart runs it in an isolated pod).</li>
 * <li>{@code omc}: OpenModelica's compiler, for the Modelica solver ({@code SYSARCH_SOLVER_OMC}).</li>
 * <li>{@code timeout}: per solve, 30 s (spec); past it the process is killed, 504, nothing written.</li>
 * </ul>
 */
@ConfigurationProperties("sysarch.solver")
public class SolverProperties {

    private String python = "";

    private String remoteUrl = "";

    private String omc = "";

    private Duration timeout = Duration.ofSeconds(30);

    public String getPython() {
        return this.python;
    }

    public void setPython(String python) {
        this.python = python;
    }

    public String getRemoteUrl() {
        return this.remoteUrl;
    }

    public void setRemoteUrl(String remoteUrl) {
        this.remoteUrl = remoteUrl;
    }

    public String getOmc() {
        return this.omc;
    }

    public void setOmc(String omc) {
        this.omc = omc;
    }

    public Duration getTimeout() {
        return this.timeout;
    }

    public void setTimeout(Duration timeout) {
        this.timeout = timeout;
    }
}
