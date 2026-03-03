package org.gradle.profiler.yourkit;

import org.gradle.profiler.InstrumentingProfiler;
import org.gradle.profiler.Logging;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;

/**
 * Controls the YourKit profiler agent via its HTTP API v2 (YourKit 2024.9+).
 * <p>
 * The agent exposes HTTPS endpoints at {@code https://localhost:{port}/yjp/api/v2/{endpoint}}.
 * The agent uses a self-signed certificate, so this controller trusts all certificates.
 *
 * @see <a href="https://www.yourkit.com/docs/java-profiler/latest/help/http_api.jsp">YourKit HTTP API v2 documentation</a>
 */
public class YourKitHttpApiController implements InstrumentingProfiler.SnapshotCapturingProfilerController {

    private static final String API_BASE = "/yjp/api/v2/";

    private final YourKitConfig options;
    private final int port;
    private final HttpClient httpClient;
    private boolean agentReady;

    public YourKitHttpApiController(YourKitConfig options, int port) {
        this.options = options;
        this.port = port;
        this.httpClient = createHttpClient();
    }

    @Override
    public void startSession() {
        YourKit.waitForPortAvailable(port);
    }

    @Override
    public void startRecording(String pid) throws IOException, InterruptedException {
        if (options.isMemorySnapshot()) {
            postApiCallWaitingForAgent("startAllocationProfiling", "{\"mode\":\"heapSampling\"}");
        } else if (options.isUseSampling()) {
            postApiCallWaitingForAgent("startSampling", "{}");
        } else {
            postApiCallWaitingForAgent("startTracing", "{}");
        }
    }

    @Override
    public void stopRecording(String pid) throws IOException, InterruptedException {
        if (options.isMemorySnapshot()) {
            postApiCallWaitingForAgent("stopAllocationProfiling", "{}");
        } else {
            postApiCallWaitingForAgent("stopCpuProfiling", "{}");
        }
    }

    @Override
    public void captureSnapshot(String pid) throws IOException, InterruptedException {
        if (options.isMemorySnapshot()) {
            postApiCallWaitingForAgent("captureMemorySnapshot", "{}");
        } else {
            postApiCallWaitingForAgent("capturePerformanceSnapshot", "{}");
        }
    }

    /**
     * Posts an API call, retrying on connection failure if this is the first call
     * to allow the agent's HTTPS listener time to start.
     */
    private String postApiCallWaitingForAgent(String endpoint, String jsonBody) throws IOException, InterruptedException {
        if (agentReady) {
            return postApiCall(endpoint, jsonBody);
        }
        long timeoutMs = 10_000;
        long pollIntervalMs = 200;
        long deadline = System.currentTimeMillis() + timeoutMs;
        IOException lastException = null;
        while (System.currentTimeMillis() < deadline) {
            try {
                String result = postApiCall(endpoint, jsonBody);
                agentReady = true;
                return result;
            } catch (IOException e) {
                lastException = e;
                Logging.detailed().println("YourKit HTTP API v2 not yet available, retrying: " + e.getMessage());
                Thread.sleep(pollIntervalMs);
            }
        }
        throw new IOException("YourKit HTTP API v2 not available after " + timeoutMs + "ms", lastException);
    }

    private String postApiCall(String endpoint, String jsonBody) throws IOException, InterruptedException {
        // Must be "localhost" — the YourKit agent's auto-generated self-signed certificate
        // has no Subject Alternative Names, so hostname verification requires the URL host
        // to match the certificate's CN. The agent defaults to listen=localhost.
        // See https://www.yourkit.com/docs/java-profiler/latest/help/agent-startup-options.jsp
        String url = "https://localhost:" + port + API_BASE + endpoint;

        Logging.detailed().println("YourKit HTTP API v2: POST " + url);

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8))
            .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        int statusCode = response.statusCode();
        if (statusCode != 200) {
            throw new IOException("YourKit HTTP API v2 call to " + endpoint
                + " failed with status " + statusCode + ": " + response.body());
        }
        Logging.detailed().println("YourKit HTTP API v2: " + endpoint + " -> " + statusCode);
        return response.body();
    }

    /**
     * Creates an HttpClient configured to work with the YourKit agent's self-signed certificate.
     */
    private static HttpClient createHttpClient() {
        try {
            // Trust all certificates — the YourKit agent generates a new self-signed cert on every start.
            TrustManager[] trustAllCerts = new TrustManager[]{
                new X509TrustManager() {
                    @Override
                    public void checkClientTrusted(X509Certificate[] chain, String authType) {
                    }

                    @Override
                    public void checkServerTrusted(X509Certificate[] chain, String authType) {
                    }

                    @Override
                    public X509Certificate[] getAcceptedIssuers() {
                        return new X509Certificate[0];
                    }
                }
            };
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, trustAllCerts, new SecureRandom());

            // Disable JSSE hostname verification.
            // HttpClient has no per-connection HostnameVerifier like HttpsURLConnection, this is the equivalent.
            SSLParameters sslParameters = new SSLParameters();
            sslParameters.setEndpointIdentificationAlgorithm("");

            return HttpClient.newBuilder()
                .sslContext(sslContext)
                .sslParameters(sslParameters)
                .build();
        } catch (NoSuchAlgorithmException | KeyManagementException e) {
            throw new RuntimeException("Failed to create trust-all HTTP client", e);
        }
    }
}
