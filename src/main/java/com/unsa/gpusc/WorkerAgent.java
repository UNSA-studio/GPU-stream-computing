package com.unsa.gpusc;

import com.google.gson.Gson;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.storage.LevelResource;

import java.io.File;
import java.net.InetAddress;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Worker agent: the network channel to the coordinator.
 * Registers local hardware, claims region jobs, generates them with the server's
 * own chunk generator, then uploads the region file back to the coordinator.
 */
public class WorkerAgent {
    private static final Gson GSON = new Gson();

    public static class RegResp {
        public boolean accepted;
        public String tier = "";
        public String msg = "";
        public String detail = "";
        public double score;
    }

    public static class ClaimResp {
        public String job = "";
        public int rx;
        public int rz;
        public String reason = "";
        public String detail = "";
    }

    private final MinecraftServer mc;
    private final GpuscConfig cfg;
    private final HttpClient http;
    private volatile boolean running = true;
    private boolean accepted = false;

    public WorkerAgent(MinecraftServer mc, GpuscConfig cfg) {
        this.mc = mc;
        this.cfg = cfg;
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();
    }

    public void start() {
        Thread t = new Thread(this::loop, "gpusc-worker");
        t.setDaemon(true);
        t.start();
        GpuStreamComputing.LOGGER.info("[gpusc] worker agent started -> {}", cfg.coordinatorUrl);
    }

    public void stop() {
        running = false;
    }

    public boolean isAccepted() {
        return accepted;
    }

    private String name() {
        if (cfg.workerName != null && !cfg.workerName.isEmpty()) {
            return cfg.workerName;
        }
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            return "worker";
        }
    }

    private String base() {
        String u = cfg.coordinatorUrl == null ? "" : cfg.coordinatorUrl;
        return u.endsWith("/") ? u.substring(0, u.length() - 1) : u;
    }

    private String enc(String s) {
        return URLEncoder.encode(s == null ? "" : s, StandardCharsets.UTF_8);
    }

    private void loop() {
        while (running) {
            try {
                if (!accepted) {
                    register();
                    if (!accepted) {
                        Thread.sleep(120000L);
                        continue;
                    }
                }
                ClaimResp job = claim();
                if (job == null) {
                    Thread.sleep(15000L);
                    continue;
                }
                long t0 = System.currentTimeMillis();
                generateRegion(job.rx, job.rz);
                boolean ok = upload(job);
                long secs = (System.currentTimeMillis() - t0) / 1000L;
                GpuStreamComputing.LOGGER.info("[gpusc] job {} {} ({}s)", job.job, ok ? "uploaded" : "UPLOAD-FAILED", secs);
            } catch (InterruptedException ie) {
                return;
            } catch (Exception ex) {
                GpuStreamComputing.LOGGER.warn("[gpusc] worker loop error: {}", ex.toString());
                try {
                    Thread.sleep(20000L);
                } catch (InterruptedException ignored) {
                    return;
                }
            }
        }
    }

    private void register() throws Exception {
        int cores = Capability.cores();
        long ram = Capability.ramMb();
        long disk = Capability.diskGb(new File("."));
        String jv = Capability.javaVersion();
        String url = base() + "/register?name=" + enc(name()) + "&cores=" + cores + "&ram=" + ram
                + "&disk=" + disk + "&java=" + enc(jv) + "&token=" + enc(cfg.token);
        HttpResponse<String> r = http.send(HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(20)).GET().build(), HttpResponse.BodyHandlers.ofString());
        RegResp resp = GSON.fromJson(r.body(), RegResp.class);
        if (resp == null) {
            return;
        }
        accepted = resp.accepted;
        GpuStreamComputing.LOGGER.info("[gpusc] register cores={} ram={}MB disk={}GB java={} -> {} ({})",
                cores, ram, disk, jv, resp.tier, resp.msg);
        if (!accepted) {
            GpuStreamComputing.LOGGER.warn("[gpusc] 本机不达标，不会领取任务: {}", resp.detail);
        }
    }

    private ClaimResp claim() throws Exception {
        String url = base() + "/claim?name=" + enc(name()) + "&token=" + enc(cfg.token);
        HttpResponse<String> r = http.send(HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(20)).GET().build(), HttpResponse.BodyHandlers.ofString());
        ClaimResp resp = GSON.fromJson(r.body(), ClaimResp.class);
        if (resp != null && resp.job != null && !resp.job.isEmpty()) {
            GpuStreamComputing.LOGGER.info("[gpusc] claimed {} (region {} {})", resp.job, resp.rx, resp.rz);
            return resp;
        }
        return null;
    }

    /** generate all 1024 chunks of the assigned region, then flush that region to disk */
    private void generateRegion(int rx, int rz) throws Exception {
        ServerLevel level = mc.overworld();
        int cx0 = rx * 32;
        int cz0 = rz * 32;
        for (int cx = cx0; cx < cx0 + 32; cx++) {
            for (int cz = cz0; cz < cz0 + 32; cz++) {
                final int fx = cx;
                final int fz = cz;
                CompletableFuture<Void> f = new CompletableFuture<>();
                mc.execute(() -> {
                    try {
                        level.getChunk(fx, fz);
                    } catch (Throwable t) {
                        GpuStreamComputing.LOGGER.warn("[gpusc] chunk {} {} failed: {}", fx, fz, t.toString());
                    } finally {
                        f.complete(null);
                    }
                });
                f.get(60, TimeUnit.SECONDS);
            }
        }
        CompletableFuture<Void> saved = new CompletableFuture<>();
        mc.execute(() -> {
            try {
                level.save(null, true, false);
            } catch (Throwable t) {
                GpuStreamComputing.LOGGER.warn("[gpusc] save failed: {}", t.toString());
            } finally {
                saved.complete(null);
            }
        });
        saved.get(180, TimeUnit.SECONDS);
    }

    private boolean upload(ClaimResp job) throws Exception {
        Path region = mc.getWorldPath(LevelResource.ROOT).resolve("region").resolve(job.job + ".mca");
        if (!Files.exists(region)) {
            GpuStreamComputing.LOGGER.warn("[gpusc] region file missing: {}", region);
            return false;
        }
        byte[] data = Files.readAllBytes(region);
        String url = base() + "/submit?id=" + enc(job.job);
        HttpResponse<String> r = http.send(HttpRequest.newBuilder(URI.create(url))
                .header("X-Gpusc-Token", cfg.token)
                .timeout(Duration.ofMinutes(10))
                .POST(HttpRequest.BodyPublishers.ofByteArray(data)).build(),
                HttpResponse.BodyHandlers.ofString());
        GpuStreamComputing.LOGGER.info("[gpusc] upload {} -> HTTP {}", job.job, r.statusCode());
        return r.statusCode() == 200;
    }
}
