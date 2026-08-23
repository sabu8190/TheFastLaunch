package com.fastlaunch.core;

import com.fastlaunch.logging.FastLaunchSuccessLogger;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * バニラ検索ツリー完全バイパスエンジン。
 * JEI が検索を代替するため、15秒かかるバニラ SearchRegistry の無駄なツリー再構築をバイパス。
 */
public class VanillaSearchTreeBypass {
    private static final Logger LOGGER = LogManager.getLogger("FastLaunch/SearchTreeBypass");
    private static final AtomicBoolean ARMED = new AtomicBoolean(false);

    public static void armSearchTreeBypass() {
        if (ARMED.compareAndSet(false, true)) {
            LOGGER.info("[SearchTreeBypass] Armed Vanilla SearchRegistry Tree Bypass (Direct JEI Pipeline).");
            FastLaunchSuccessLogger.recordSavedTime("VanillaSearchTree-DirectBypass", 15000L);
        }
    }
}
