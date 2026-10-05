package com.fastlaunch.fabric;

import com.fastlaunch.core.FastLaunchCpuAffinityEngine;
import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Fabric PreLaunch エントリポイント。
 * 最も早い段階で Windows P-Core アフィニティを適用し、スレッドプールを準備する。
 */
public class FastLaunchFabricPreLaunch implements PreLaunchEntrypoint {
    private static final Logger LOGGER = LogManager.getLogger("FastLaunch/FabricPreLaunch");

    @Override
    public void onPreLaunch() {
        // Windows P-Core Affinity 最適化: E コア遅延ブレを根絶
        FastLaunchCpuAffinityEngine.applyPcoreAffinity();

        int cores = Math.max(4, Runtime.getRuntime().availableProcessors());
        LOGGER.info("=======================================================================");
        LOGGER.info(">>> [TheFastLaunch] FABRIC PRE-LAUNCH INITIALIZATION ACTIVE!        <<<");
        LOGGER.info(">>> [TheFastLaunch] Windows P-Core Affinity: ACTIVE!                  <<<");
        LOGGER.info(">>> [TheFastLaunch] Available Processors: {} Cores                    <<<", cores);
        LOGGER.info("=======================================================================");
    }
}
