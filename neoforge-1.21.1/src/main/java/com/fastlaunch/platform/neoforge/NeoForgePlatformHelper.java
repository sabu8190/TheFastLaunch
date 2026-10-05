package com.fastlaunch.platform.neoforge;

import com.fastlaunch.platform.IPlatformHelper;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.fml.loading.FMLEnvironment;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * NeoForge 1.21.1 向け PlatformHelper 実装。
 */
public class NeoForgePlatformHelper implements IPlatformHelper {

    @Override
    public String getPlatformName() {
        return "NeoForge";
    }

    @Override
    public boolean isModLoaded(String modId) {
        try {
            ModList list = ModList.get();
            return list != null && list.isLoaded(modId);
        } catch (Throwable ignored) {
            return false;
        }
    }

    @Override
    public boolean isDevelopmentEnvironment() {
        try {
            return !FMLLoader.isProduction();
        } catch (Throwable ignored) {
            return false;
        }
    }

    @Override
    public Path getGameDir() {
        try {
            return FMLPaths.GAMEDIR.get();
        } catch (Throwable ignored) {
            return Paths.get(".");
        }
    }

    @Override
    public Path getConfigDir() {
        try {
            return FMLPaths.CONFIGDIR.get();
        } catch (Throwable ignored) {
            return Paths.get("./config");
        }
    }

    @Override
    public boolean isClient() {
        try {
            return FMLEnvironment.dist.isClient();
        } catch (Throwable ignored) {
            return true;
        }
    }
}
