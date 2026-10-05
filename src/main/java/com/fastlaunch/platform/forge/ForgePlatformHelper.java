package com.fastlaunch.platform.forge;

import com.fastlaunch.platform.IPlatformHelper;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLLoader;
import net.minecraftforge.fml.loading.FMLPaths;

import java.nio.file.Path;

/**
 * Minecraft Forge 向け PlatformHelper 実装。
 */
public class ForgePlatformHelper implements IPlatformHelper {

    @Override
    public String getPlatformName() {
        return "Forge";
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
            return java.nio.file.Paths.get(".");
        }
    }

    @Override
    public Path getConfigDir() {
        try {
            return FMLPaths.CONFIGDIR.get();
        } catch (Throwable ignored) {
            return java.nio.file.Paths.get("./config");
        }
    }

    @Override
    public boolean isClient() {
        try {
            return net.minecraftforge.fml.loading.FMLEnvironment.dist.isClient();
        } catch (Throwable ignored) {
            return true;
        }
    }
}
