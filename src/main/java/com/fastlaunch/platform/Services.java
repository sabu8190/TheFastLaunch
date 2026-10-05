package com.fastlaunch.platform;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ServiceLoader;

/**
 * ServiceLoader パターンを用いたプラットフォーム依存サービスのローダー。
 */
public class Services {
    private static final Logger LOGGER = LogManager.getLogger("FastLaunch/Services");

    public static final IPlatformHelper PLATFORM = load(IPlatformHelper.class);

    public static <T> T load(Class<T> clazz) {
        final T loadedService = ServiceLoader.load(clazz)
                .findFirst()
                .orElse(null);

        if (loadedService != null) {
            LOGGER.info("[Services] Successfully loaded platform service: {} -> {}",
                    clazz.getSimpleName(), loadedService.getClass().getName());
            return loadedService;
        }

        LOGGER.warn("[Services] No implementation found for {}, creating default fallback.", clazz.getSimpleName());
        if (clazz == IPlatformHelper.class) {
            @SuppressWarnings("unchecked")
            T fallback = (T) new FallbackPlatformHelper();
            return fallback;
        }

        throw new NullPointerException("Failed to load service for " + clazz.getName());
    }

    private static class FallbackPlatformHelper implements IPlatformHelper {
        @Override
        public String getPlatformName() {
            return "Vanilla/Fallback";
        }

        @Override
        public boolean isModLoaded(String modId) {
            return false;
        }

        @Override
        public boolean isDevelopmentEnvironment() {
            return false;
        }

        @Override
        public Path getGameDir() {
            return Paths.get(".");
        }

        @Override
        public Path getConfigDir() {
            return Paths.get("./config");
        }
    }
}
