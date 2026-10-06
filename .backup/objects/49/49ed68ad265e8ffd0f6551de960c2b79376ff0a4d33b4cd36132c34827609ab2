package com.unsa.gpusc;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.ExplosionEvent;

/**
 * 全局爆炸防破坏（服务端生效）。
 *
 * 原理：NeoForge 的 ExplosionEvent.Detonate 在爆炸真正炸方块之前触发，
 * 我们把 "将被破坏的方块列表" 清空即可：
 *   - 所有爆炸（TNT / 苦力怕 / 恶魂 / 凋灵 / 末影水晶 / 床 / 重生锚 / SBW 等）
 *     都不再破坏方块与地形；
 *   - 对实体的伤害（玩家/生物被炸飞、掉血）保持不变。
 *
 * 该事件只在服务端有意义，客户端直接跳过。
 */
@EventBusSubscriber(modid = GpuStreamComputing.MODID)
public final class ExplosionGuard {

    private ExplosionGuard() {
    }

    @SubscribeEvent
    public static void onDetonate(ExplosionEvent.Detonate event) {
        try {
            if (event.getLevel().isClientSide()) {
                return;
            }
            // 清空"受影响方块"列表 → 什么都不炸掉
            event.getAffectedBlocks().clear();
        } catch (Throwable t) {
            GpuStreamComputing.LOGGER.warn("[gpusc] explosion guard error: {}", t.toString());
        }
    }
}