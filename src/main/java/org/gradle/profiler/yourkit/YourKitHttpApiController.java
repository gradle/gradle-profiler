package org.gradle.profiler.yourkit;

import org.gradle.profiler.InstrumentingProfiler;
import org.gradle.profiler.Logging;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
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
    private final SSLContext sslContext;
    private final HostnameVerifier hostnameVerifier;
    private boolean agentReady;

    public YourKitHttpApiController(YourKitConfig options, int port) {
        this.options = options;
        this.port = port;
        this.sslContext = createTrustAllSslContext();
        this.hostnameVerifier = (hostname, session) -> true;
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

    private String postApiCall(String endpoint, String jsonBody) throws IOException {
        String url = "https://localhost:" + port + API_BASE + endpoint;
        Logging.detailed().println("YourKit HTTP API v2: POST " + url);

        HttpsURLConnection connection = (HttpsURLConnection) URI.create(url).toURL().openConnection();
        try {
            connection.setSSLSocketFactory(sslContext.getSocketFactory());
            connection.setHostnameVerifier(hostnameVerifier);
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setDoOutput(true);

            try (OutputStream os = connection.getOutputStream()) {
                os.write(jsonBody.getBytes(StandardCharsets.UTF_8));
            }

            int statusCode = connection.getResponseCode();
            String responseBody;
            try (InputStream is = (statusCode >= 400) ? connection.getErrorStream() : connection.getInputStream()) {
                responseBody = is != null ? new String(is.readAllBytes(), StandardCharsets.UTF_8) : "";
            }

            if (statusCode != 200) {
                throw new IOException("YourKit HTTP API v2 call to " + endpoint + " failed with status " + statusCode + ": " + responseBody);
            }
            Logging.detailed().println("YourKit HTTP API v2: " + endpoint + " -> " + statusCode);
            return responseBody;
        } finally {
            connection.disconnect();
        }
    }

    private static SSLContext createTrustAllSslContext() {
        try {
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
            return sslContext;
        } catch (NoSuchAlgorithmException | KeyManagementException e) {
            throw new RuntimeException("Failed to create trust-all SSL context", e);
        }
    }
}
