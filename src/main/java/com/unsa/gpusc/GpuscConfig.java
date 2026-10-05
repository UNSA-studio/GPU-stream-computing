package com.unsa.gpusc;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class GpuscConfig {
    public String role = "coordinator";
    public int port = 8765;
    public String token = "change-me";
    public int minCores = 4;
    public int minRamMb = 8000;
    public int minDiskGb = 20;
    public int centerX = 0;
    public int centerZ = 0;
    public int radius = 3000;
    public String coordinatorUrl = "";
    public String workerName = "";

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static Path path() { return Paths.get("config", "gpusc.json"); }

    public static GpuscConfig load() {
        try {
            Path p = path();
            if (Files.exists(p)) {
                GpuscConfig c = GSON.fromJson(Files.readString(p), GpuscConfig.class);
                if (c != null) { c.save(); return c; }
            }
        } catch (Exception ex) {
            GpuStreamComputing.LOGGER.warn("[gpusc] config load failed: {}", ex.toString());
        }
        GpuscConfig c = new GpuscConfig();
        c.save();
        return c;
    }

    public void save() {
        try {
            Path p = path();
            Files.createDirectories(p.getParent());
            Files.writeString(p, GSON.toJson(this));
        } catch (IOException ex) {
            GpuStreamComputing.LOGGER.warn("[gpusc] config save failed: {}", ex.toString());
        }
    }
}
