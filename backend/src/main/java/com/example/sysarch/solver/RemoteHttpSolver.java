package com.example.sysarch.solver;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;

/**
 * {@code remote}: an HTTP solver service (P3 T4). Contract = the Python script's: {@code POST} the same JSON
 * (expression, inputs, targets, options), get the same JSON back. {@code solver/http_server.py} is such a service; the
 * Helm chart runs it in its own pod without network egress and with a read-only root file system (spec §3.4's container).
 */
public class RemoteHttpSolver implements ConstraintSolver {

    private final String url;

    private final Duration timeout;

    private final ObjectMapper json;

    private final HttpClient http;

    public RemoteHttpSolver(String url, Duration timeout, ObjectMapper json) {
        this.url = url == null ? "" : url.strip();
        this.timeout = timeout;
        this.json = json;
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    }

    @Override
    public String id() {
        return "remote";
    }

    @Override
    public boolean available() {
        return !this.url.isEmpty();
    }

    @Override
    public SolveResult solve(SolveRequest request) {
        try {
            HttpRequest post = HttpRequest.newBuilder(URI.create(this.url)).timeout(this.timeout).header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofByteArray(this.json.writeValueAsBytes(PythonSolver.requestJson(this.json, request)))).build();
            HttpResponse<String> response = this.http.send(post, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 504) {
                throw new SolverTimeout("远程求解服务超时（504），未写回任何值");
            }
            if (response.statusCode() / 100 != 2) {
                throw new SolverFailure("远程求解服务出错：HTTP " + response.statusCode() + " " + abbreviate(response.body()));
            }
            return PythonSolver.result(this.json.readTree(response.body()));
        } catch (HttpTimeoutException e) {
            throw new SolverTimeout("远程求解服务 " + this.timeout.toSeconds() + " 秒内没有结果，未写回任何值");
        } catch (IOException e) {
            throw new SolverFailure("无法连接远程求解服务：" + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SolverFailure("求解被中断");
        }
    }

    private static String abbreviate(String text) {
        return text == null ? "" : text.length() > 300 ? text.substring(0, 300) + "…" : text;
    }
}
