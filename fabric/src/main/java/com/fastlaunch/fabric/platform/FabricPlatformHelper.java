package com.fastlaunch.fabric.platform;

import com.fastlaunch.platform.IPlatformHelper;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Path;

/**
 * Fabric 向け PlatformHelper 実装。
 */
public class FabricPlatformHelper implements IPlatformHelper {

    @Override
    public String getPlatformName() {
        return "Fabric";
    }

    @Override
    public boolean isModLoaded(String modId) {
        try {
            return FabricLoader.getInstance().isModLoaded(modId);
        } catch (Throwable ignored) {
            return false;
        }
    }

    @Override
    public boolean isDevelopmentEnvironment() {
        try {
            return FabricLoader.getInstance().isDevelopmentEnvironment();
        } catch (Throwable ignored) {
            return false;
        }
    }

    @Override
    public Path getGameDir() {
        try {
            return FabricLoader.getInstance().getGameDir();
        } catch (Throwable ignored) {
            return java.nio.file.Paths.get(".");
        }
    }

    @Override
    public Path getConfigDir() {
        try {
            return FabricLoader.getInstance().getConfigDir();
        } catch (Throwable ignored) {
            return java.nio.file.Paths.get("./config");
        }
    }

    @Override
    public boolean isClient() {
        try {
            return net.fabricmc.api.EnvType.CLIENT == FabricLoader.getInstance().getEnvironmentType();
        } catch (Throwable ignored) {
            return true;
        }
    }
}
