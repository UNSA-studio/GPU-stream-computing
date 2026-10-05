package com.unsa.gpusc;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;

public class JobManager {
    public static final int REGION = 512;

    public static class Job {
        public String id;
        public int rx, rz;
        public String state = "todo";
        public String worker = "";
        public long claimedAt, finishedAt;
        public Job() {}
        public Job(String id, int rx, int rz) { this.id = id; this.rx = rx; this.rz = rz; }
    }

    public static class WorkerInfo {
        public String name = "";
        public int cores;
        public long ramMb, diskGb;
        public String javaVersion = "";
        public String tier = "weak";
        public String detail = "";
        public double score;
        public long lastSeen;
    }

    public static class State {
        public Map<String, Job> jobs = new LinkedHashMap<>();
        public Map<String, WorkerInfo> workers = new LinkedHashMap<>();
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public final MinecraftServer server;
    public final Path worldDir;
    public final Path regionDir;
    public final Path uploadDir;
    public final Path stateFile;
    public State state = new State();

    public JobManager(MinecraftServer server) {
        this.server = server;
        this.worldDir = server.getWorldPath(LevelResource.ROOT);
        this.regionDir = worldDir.resolve("region");
        this.uploadDir = worldDir.resolve("gpusc_uploads");
        this.stateFile = worldDir.resolve("gpusc_state.json");
    }

    public synchronized void load() {
        try {
            if (Files.exists(stateFile)) {
                State s = GSON.fromJson(Files.readString(stateFile), State.class);
                if (s != null) state = s;
            }
            Files.createDirectories(uploadDir);
        } catch (Exception ex) {
            GpuStreamComputing.LOGGER.warn("[gpusc] state load failed: {}", ex.toString());
        }
    }

    public synchronized void save() {
        try {
            Files.createDirectories(worldDir);
            Files.writeString(stateFile, GSON.toJson(state));
        } catch (IOException ex) {
            GpuStreamComputing.LOGGER.warn("[gpusc] state save failed: {}", ex.toString());
        }
    }

    public static String jobId(int rx, int rz) { return "r." + rx + "." + rz; }

    public synchronized int scan(GpuscConfig cfg) {
        int rx0 = Math.floorDiv(cfg.centerX - cfg.radius, REGION);
        int rx1 = Math.floorDiv(cfg.centerX + cfg.radius, REGION);
        int rz0 = Math.floorDiv(cfg.centerZ - cfg.radius, REGION);
        int rz1 = Math.floorDiv(cfg.centerZ + cfg.radius, REGION);
        int added = 0, skipped = 0;
        for (int rx = rx0; rx <= rx1; rx++) {
            for (int rz = rz0; rz <= rz1; rz++) {
                String id = jobId(rx, rz);
                if (state.jobs.containsKey(id)) continue;
                Path f = regionDir.resolve(id + ".mca");
                if (Files.exists(f)) {
                    try { if (Files.size(f) > 4096) { skipped++; continue; } } catch (IOException ignored) { }
                }
                state.jobs.put(id, new Job(id, rx, rz));
                added++;
            }
        }
        save();
        GpuStreamComputing.LOGGER.info("[gpusc] scan done: added={} skipped={}", added, skipped);
        return added;
    }

    public synchronized Job claim(String worker) {
        for (Job j : state.jobs.values()) {
            if ("todo".equals(j.state)) {
                j.state = "claimed";
                j.worker = worker;
                j.claimedAt = System.currentTimeMillis() / 1000L;
                save();
                return j;
            }
        }
        return null;
    }

    public synchronized boolean submit(String id, byte[] data) {
        Job j = state.jobs.get(id);
        if (j == null) return false;
        try {
            if (data != null && data.length > 0) {
                Files.createDirectories(uploadDir);
                Files.write(uploadDir.resolve(id + ".mca"), data);
                merge(id);
            }
            j.state = "done";
            j.finishedAt = System.currentTimeMillis() / 1000L;
            save();
            return true;
        } catch (IOException ex) {
            GpuStreamComputing.LOGGER.warn("[gpusc] submit failed: {}", ex.toString());
            return false;
        }
    }

    public synchronized boolean merge(String id) {
        try {
            Path from = uploadDir.resolve(id + ".mca");
            if (!Files.exists(from)) return false;
            Path to = regionDir.resolve(id + ".mca");
            Files.createDirectories(regionDir);
            Files.copy(from, to, StandardCopyOption.REPLACE_EXISTING);
            GpuStreamComputing.LOGGER.info("[gpusc] merged {}", id);
            return true;
        } catch (IOException ex) {
            GpuStreamComputing.LOGGER.warn("[gpusc] merge failed {}: {}", id, ex.toString());
            return false;
        }
    }

    public synchronized Map<String, Integer> counts() {
        int todo = 0, claimed = 0, done = 0;
        for (Job j : state.jobs.values()) {
            switch (j.state) {
                case "todo" -> todo++;
                case "claimed" -> claimed++;
                case "done" -> done++;
                default -> { }
            }
        }
        return Map.of("todo", todo, "claimed", claimed, "done", done);
    }
}
