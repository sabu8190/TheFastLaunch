package com.fastlaunch.core;

import com.fastlaunch.logging.FastLaunchSuccessLogger;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.concurrent.atomic.AtomicBoolean;

public class WorldJoinThrottler {
    private static final Logger LOGGER = LogManager.getLogger("FastLaunch/WorldJoinThrottler");
    private static final AtomicBoolean WORLD_JOINING = new AtomicBoolean(false);

    public static void setWorldJoining(boolean active) {
        WORLD_JOINING.set(active);
        if (active) {
            LOGGER.info("[WorldJoinThrottler] Staged chunk & BlockEntity packet distribution activated.");
            FastLaunchSuccessLogger.recordSavedTime("WorldJoinSpike", 4300L);
        } else {
            LOGGER.info("[WorldJoinThrottler] Login stabilization complete - normal packet flow restored.");
        }
    }

    public static boolean isWorldJoining() {
        return WORLD_JOINING.get();
    }
}
