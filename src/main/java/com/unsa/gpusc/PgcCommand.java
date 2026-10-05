package com.unsa.gpusc;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.Map;

@EventBusSubscriber(modid = GpuStreamComputing.MODID)
public class PgcCommand {

    @SubscribeEvent
    public static void onRegister(RegisterCommandsEvent event) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("pgc")
                .requires(s -> s.hasPermission(2))
                .then(Commands.literal("status").executes(ctx -> {
                    GpuscConfig c = GpuStreamComputing.CONFIG;
                    JobManager jm = GpuStreamComputing.JOBS;
                    Map<String, Integer> cnt = jm.counts();
                    send(ctx.getSource(), "§b[GPU-SC] §f角色=" + c.role + " 端口=" + c.port
                            + " 任务: todo=" + cnt.get("todo") + " claimed=" + cnt.get("claimed") + " done=" + cnt.get("done"));
                    return 1;
                }))
                .then(Commands.literal("workers").executes(ctx -> {
                    JobManager jm = GpuStreamComputing.JOBS;
                    if (jm.state.workers.isEmpty()) send(ctx.getSource(), "§7没有已注册的机器");
                    for (JobManager.WorkerInfo w : jm.state.workers.values()) {
                        String color = "strong".equals(w.tier) ? "§a" : "§c";
                        send(ctx.getSource(), color + w.name + " §7| 核心=" + w.cores + " 内存=" + w.ramMb + "MB 磁盘=" + w.diskGb
                                + "GB 评分=" + w.score + " §7| " + (w.detail.isEmpty() ? "可分配" : "拒绝: " + w.detail));
                    }
                    return 1;
                }))
                .then(Commands.literal("scan").executes(ctx -> {
                    int n = GpuStreamComputing.JOBS.scan(GpuStreamComputing.CONFIG);
                    send(ctx.getSource(), "§b[GPU-SC] §f扫描完成，新增任务 " + n + " 个");
                    return n;
                }))
                .then(Commands.literal("jobs").executes(ctx -> {
                    JobManager jm = GpuStreamComputing.JOBS;
                    Map<String, Integer> cnt = jm.counts();
                    send(ctx.getSource(), "§b[GPU-SC] §f任务统计: todo=" + cnt.get("todo") + " claimed=" + cnt.get("claimed") + " done=" + cnt.get("done"));
                    int shown = 0;
                    for (JobManager.Job j : jm.state.jobs.values()) {
                        if (shown++ >= 10) { send(ctx.getSource(), "§7..."); break; }
                        send(ctx.getSource(), "§7- " + j.id + " [" + j.state + "] " + (j.worker.isEmpty() ? "" : "by " + j.worker));
                    }
                    return 1;
                }));
        event.getDispatcher().register(root);
    }

    private static void send(CommandSourceStack src, String msg) {
        src.sendSuccess(() -> Component.literal(msg), false);
    }
}
