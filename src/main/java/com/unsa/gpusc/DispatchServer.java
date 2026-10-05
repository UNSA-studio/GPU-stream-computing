package com.unsa.gpusc;

import com.google.gson.Gson;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import net.minecraft.server.MinecraftServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;

public class DispatchServer {
    private static final Gson GSON = new Gson();

    private final MinecraftServer mc;
    private final JobManager jobs;
    private final GpuscConfig cfg;
    private HttpServer http;

    public DispatchServer(MinecraftServer mc, JobManager jobs, GpuscConfig cfg) {
        this.mc = mc;
        this.jobs = jobs;
        this.cfg = cfg;
    }

    public void start() {
        try {
            http = HttpServer.create(new InetSocketAddress(cfg.port), 0);
            http.setExecutor(Executors.newFixedThreadPool(4));
            http.createContext("/register", ex -> register(ex));
            http.createContext("/claim", ex -> claim(ex));
            http.createContext("/submit", ex -> submit(ex));
            http.createContext("/status", ex -> status(ex));
            http.start();
            GpuStreamComputing.LOGGER.info("[gpusc] dispatch server on :{}", cfg.port);
        } catch (IOException ex) {
            GpuStreamComputing.LOGGER.warn("[gpusc] dispatch server failed: {}", ex.toString());
        }
    }

    public void stop() {
        if (http != null) http.stop(0);
        http = null;
    }

    private boolean auth(HttpExchange ex) {
        String t = ex.getRequestHeaders().getFirst("X-Gpusc-Token");
        if (t == null) {
            String q = ex.getRequestURI().getQuery();
            if (q != null && q.contains("token=")) t = q.split("token=")[1].split("&")[0];
        }
        return t != null && t.equals(cfg.token);
    }

    private void reply(HttpExchange ex, String body) throws IOException {
        byte[] b = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
        ex.sendResponseHeaders(200, b.length);
        try (OutputStream os = ex.getResponseBody()) { os.write(b); }
    }

    private Map<String, String> query(HttpExchange ex) {
        Map<String, String> m = new HashMap<>();
        String q = ex.getRequestURI().getQuery();
        if (q == null) return m;
        for (String kv : q.split("&")) {
            int i = kv.indexOf('=');
            if (i > 0) m.put(kv.substring(0, i), kv.substring(i + 1));
        }
        return m;
    }

    private void register(HttpExchange ex) throws IOException {
        if (!auth(ex)) { reply(ex, GSON.toJson(Map.of("error", "bad token"))); return; }
        Map<String, String> q = query(ex);
        JobManager.WorkerInfo w = new JobManager.WorkerInfo();
        w.name = q.getOrDefault("name", "unnamed");
        try { w.cores = Integer.parseInt(q.getOrDefault("cores", "0")); } catch (NumberFormatException ignored) { }
        try { w.ramMb = Long.parseLong(q.getOrDefault("ram", "0")); } catch (NumberFormatException ignored) { }
        try { w.diskGb = Long.parseLong(q.getOrDefault("disk", "0")); } catch (NumberFormatException ignored) { }
        w.javaVersion = q.getOrDefault("java", "");
        w.detail = Capability.gate(w.cores, w.ramMb, w.diskGb, cfg);
        w.score = Capability.score(w.cores, w.ramMb, w.diskGb);
        w.tier = w.detail.isEmpty() ? "strong" : "weak";
        w.lastSeen = System.currentTimeMillis() / 1000L;
        jobs.state.workers.put(w.name, w);
        jobs.save();
        Map<String, Object> res = new HashMap<>();
        res.put("accepted", "strong".equals(w.tier));
        res.put("tier", w.tier);
        res.put("score", w.score);
        res.put("detail", w.detail);
        res.put("msg", "strong".equals(w.tier) ? "已接入，可以领任务" : "性能不足，不会给你分配任务");
        reply(ex, GSON.toJson(res));
    }

    private void claim(HttpExchange ex) throws IOException {
        if (!auth(ex)) { reply(ex, GSON.toJson(Map.of("error", "bad token"))); return; }
        String name = query(ex).getOrDefault("name", "");
        JobManager.WorkerInfo w = jobs.state.workers.get(name);
        if (w == null) { reply(ex, GSON.toJson(Map.of("job", "", "reason", "未注册"))); return; }
        String bad = Capability.gate(w.cores, w.ramMb, w.diskGb, cfg);
        if (!bad.isEmpty()) {
            reply(ex, GSON.toJson(Map.of("job", "", "reason", "性能不足，不分配任务", "detail", bad)));
            return;
        }
        JobManager.Job j = jobs.claim(name);
        if (j == null) { reply(ex, GSON.toJson(Map.of("job", "", "reason", "暂无待办任务"))); return; }
        Map<String, Object> res = new HashMap<>();
        res.put("job", j.id);
        res.put("rx", j.rx);
        res.put("rz", j.rz);
        res.put("x1", j.rx * JobManager.REGION);
        res.put("z1", j.rz * JobManager.REGION);
        res.put("x2", j.rx * JobManager.REGION + JobManager.REGION - 1);
        res.put("z2", j.rz * JobManager.REGION + JobManager.REGION - 1);
        reply(ex, GSON.toJson(res));
    }

    private void submit(HttpExchange ex) throws IOException {
        if (!auth(ex)) { reply(ex, GSON.toJson(Map.of("error", "bad token"))); return; }
        String id = ex.getRequestHeaders().getFirst("X-Gpusc-Job");
        if (id == null) id = query(ex).getOrDefault("id", "");
        byte[] data;
        try (InputStream in = ex.getRequestBody()) { data = in.readAllBytes(); }
        boolean ok = jobs.submit(id, data);
        reply(ex, GSON.toJson(Map.of("ok", ok, "id", id)));
    }

    private void status(HttpExchange ex) throws IOException {
        if (!auth(ex)) { reply(ex, GSON.toJson(Map.of("error", "bad token"))); return; }
        Map<String, Object> res = new HashMap<>();
        res.put("counts", jobs.counts());
        res.put("workers", jobs.state.workers.values());
        reply(ex, GSON.toJson(res));
    }
}
