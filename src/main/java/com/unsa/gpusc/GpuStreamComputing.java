package com.unsa.gpusc;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(GpuStreamComputing.MODID)
public class GpuStreamComputing {
    public static final String MODID = "gpusc";
    public static final Logger LOGGER = LoggerFactory.getLogger("gpusc");

    public static GpuscConfig CONFIG;
    public static JobManager JOBS;
    public static DispatchServer SERVER;

    public GpuStreamComputing(IEventBus modBus, ModContainer container) {
        CONFIG = GpuscConfig.load();
        NeoForge.EVENT_BUS.addListener(this::onServerStarted);
        NeoForge.EVENT_BUS.addListener(this::onServerStopping);
        LOGGER.info("[gpusc] loaded, role={} port={}", CONFIG.role, CONFIG.port);
    }

    private void onServerStarted(ServerStartedEvent e) {
        JOBS = new JobManager(e.getServer());
        JOBS.load();
        if ("coordinator".equalsIgnoreCase(CONFIG.role)) {
            SERVER = new DispatchServer(e.getServer(), JOBS, CONFIG);
            SERVER.start();
        }
        LOGGER.info("[gpusc] server started; jobs={}", JOBS.state.jobs.size());
    }

    private void onServerStopping(ServerStoppingEvent e) {
        if (SERVER != null) SERVER.stop();
        if (JOBS != null) JOBS.save();
    }
}
