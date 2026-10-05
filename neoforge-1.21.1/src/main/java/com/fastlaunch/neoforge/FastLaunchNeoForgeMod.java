package com.fastlaunch.neoforge;

import com.fastlaunch.core.FastLaunchCpuAffinityEngine;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * NeoForge 1.21.1 向け FastLaunch メイン Mod クラス。
 */
@Mod("fastlaunch")
public class FastLaunchNeoForgeMod {
    private static final Logger LOGGER = LogManager.getLogger("FastLaunch/NeoForge");

    public FastLaunchNeoForgeMod(IEventBus modEventBus) {
        // Windows P-Core Affinity 最適化: E コア遅延ブレを根絶
        FastLaunchCpuAffinityEngine.applyPcoreAffinity();

        int cores = Math.max(4, Runtime.getRuntime().availableProcessors());
        LOGGER.info("=======================================================================");
        LOGGER.info(">>> [TheFastLaunch] NEOFORGE 1.21.1 INITIALIZATION ACTIVE!            <<<");
        LOGGER.info(">>> [TheFastLaunch] Windows P-Core Affinity: ACTIVE!                  <<<");
        LOGGER.info(">>> [TheFastLaunch] Available Processors: {} Cores                    <<<", cores);
        LOGGER.info("=======================================================================");
    }
}
