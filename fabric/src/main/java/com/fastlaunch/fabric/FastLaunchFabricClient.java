package com.fastlaunch.fabric;

import com.fastlaunch.config.FastLaunchConfig;
import com.fastlaunch.platform.Services;
import net.fabricmc.api.ClientModInitializer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Fabric ClientModInitializer エントリポイント。
 */
public class FastLaunchFabricClient implements ClientModInitializer {
    private static final Logger LOGGER = LogManager.getLogger("FastLaunch/FabricClient");

    @Override
    public void onInitializeClient() {
        FastLaunchConfig.load();
        LOGGER.info("=======================================================================");
        LOGGER.info(">>> [TheFastLaunch] FABRIC CLIENT MOD INITIALIZER LOADED!           <<<");
        LOGGER.info(">>> [TheFastLaunch] Platform: {}                                    <<<", Services.PLATFORM.getPlatformName());
        LOGGER.info("=======================================================================");
    }
}
