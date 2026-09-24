package com.fastlaunch.logging;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public class FastLaunchSuccessLogger {
    private static final Logger LOGGER = LogManager.getLogger("FastLaunch/Report");
    private static final Map<String, Long> SAVED_TIMES = new ConcurrentHashMap<>();
    private static final Map<String, String> ACTIVE_FEATURES = new ConcurrentHashMap<>();
    private static final AtomicBoolean REPORT_PRINTED = new AtomicBoolean(false);

    /**
     * 実測された削減時間（実測ベンチマーク差分）を正直に記録する。
     * ハードコードされた推測値や架空の数値を記録することは禁止。
     */
    public static void recordSavedTime(String feature, long ms) {
        if (ms > 0) {
            SAVED_TIMES.put(feature, ms);
        }
    }

    /**
     * 実際に稼働した最適化モジュールとその実測ステータスを動的に記録する。
     */
    public static void recordActiveFeature(String feature, String status) {
        ACTIVE_FEATURES.put(feature, status);
    }

    public static void printSuccessReport() {
        if (REPORT_PRINTED.compareAndSet(false, true)) {
            long totalSavedMs = SAVED_TIMES.values().stream().mapToLong(Long::longValue).sum();
            double totalSavedSec = totalSavedMs / 1000.0;

            LOGGER.info("=======================================================================");
            LOGGER.info("             ✨ THEFASTLAUNCH v1.8.2 OPTIMIZATION REPORT ✨              ");
            LOGGER.info("=======================================================================");
            LOGGER.info(" [Platform] Minecraft 1.20.1 (Forge 47.4.21 / UniMixin)");
            if (totalSavedMs > 0) {
                LOGGER.info(String.format(" [Status]   MEASURED ACCELERATION COMPLETED (Measured Savings: ~%.2fs)", totalSavedSec));
            } else {
                LOGGER.info(" [Status]   ALL ACTIVE OPTIMIZATION SHIELDS OPERATIONAL");
            }
            LOGGER.info("-----------------------------------------------------------------------");

            int index = 1;
            for (Map.Entry<String, String> entry : ACTIVE_FEATURES.entrySet()) {
                LOGGER.info(String.format("  %2d. %-36s : %s", index++, entry.getKey(), entry.getValue()));
            }

            if (!SAVED_TIMES.isEmpty()) {
                LOGGER.info("-----------------------------------------------------------------------");
                LOGGER.info(" [Measured Savings by Component]");
                for (Map.Entry<String, Long> entry : SAVED_TIMES.entrySet()) {
                    LOGGER.info(String.format("   - %-34s : %d ms", entry.getKey(), entry.getValue()));
                }
            }
            LOGGER.info("=======================================================================");
        }
    }
}
