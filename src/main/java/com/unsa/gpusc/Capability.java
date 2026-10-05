package com.unsa.gpusc;

import java.io.File;
import java.lang.management.ManagementFactory;

public class Capability {
    public static int cores() { return Runtime.getRuntime().availableProcessors(); }

    public static long ramMb() {
        try {
            java.lang.management.OperatingSystemMXBean b = ManagementFactory.getOperatingSystemMXBean();
            if (b instanceof com.sun.management.OperatingSystemMXBean os) {
                return os.getTotalMemorySize() / 1048576L;
            }
        } catch (Throwable ignored) { }
        return Runtime.getRuntime().maxMemory() / 1048576L;
    }

    public static long diskGb(File f) {
        try { return f.getUsableSpace() / (1024L * 1024L * 1024L); } catch (Throwable t) { return 0L; }
    }

    public static String javaVersion() { return System.getProperty("java.version", "?"); }

    public static String gate(int cores, long ramMb, long diskGb, GpuscConfig cfg) {
        StringBuilder bad = new StringBuilder();
        if (cores < cfg.minCores) bad.append("CPU核心:").append(cores).append("<").append(cfg.minCores).append(" ");
        if (ramMb < cfg.minRamMb) bad.append("内存:").append(ramMb).append("MB<").append(cfg.minRamMb).append("MB ");
        if (diskGb < cfg.minDiskGb) bad.append("磁盘:").append(diskGb).append("GB<").append(cfg.minDiskGb).append("GB ");
        return bad.toString();
    }

    public static double score(int cores, long ramMb, long diskGb) {
        return Math.round((cores * 2 + ramMb / 1000.0 + diskGb / 10.0) * 10.0) / 10.0;
    }
}
