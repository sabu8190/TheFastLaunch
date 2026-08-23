package com.fastlaunch.mixin;

import net.minecraft.server.packs.FilePackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.IoSupplier;
import net.minecraft.resources.ResourceLocation;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.concurrent.atomic.AtomicBoolean;

@Mixin(value = FilePackResources.class, priority = 500)
public abstract class FilePackResourcesDirectReadMixin {
    private static final Logger LOGGER = LogManager.getLogger("FastLaunch/DirectReadMixin");
    private static final File CACHE_BASE_DIR = new File(".fastlaunch_extracted_assets");
    private static final AtomicBoolean LOGGED = new AtomicBoolean(false);

    @Shadow
    private File file;

    @Inject(method = "getRootResource", at = @At("HEAD"), cancellable = true)
    private void onGetRootResource(String[] path, CallbackInfoReturnable<IoSupplier<InputStream>> cir) {
        if (CACHE_BASE_DIR.exists() && path.length > 0) {
            String relativePath = String.join("/", path);
            File cachedFile = new File(CACHE_BASE_DIR, relativePath);
            if (cachedFile.exists() && cachedFile.isFile()) {
                if (LOGGED.compareAndSet(false, true)) {
                    LOGGER.info("[DirectReadMixin] 🎯 CACHE HIT! Intercepted FilePackResources -> Streaming directly from .fastlaunch_extracted_assets/!");
                }
                cir.setReturnValue(() -> new FileInputStream(cachedFile));
            }
        }
    }

    @Inject(method = "getResource", at = @At("HEAD"), cancellable = true)
    private void onGetResource(PackType type, ResourceLocation location, CallbackInfoReturnable<IoSupplier<InputStream>> cir) {
        if (CACHE_BASE_DIR.exists()) {
            String path = type.getDirectory() + "/" + location.getNamespace() + "/" + location.getPath();
            File cachedFile = new File(CACHE_BASE_DIR, path);
            if (cachedFile.exists() && cachedFile.isFile()) {
                if (LOGGED.compareAndSet(false, true)) {
                    LOGGER.info("[DirectReadMixin] 🎯 CACHE HIT! Intercepted FilePackResources -> Streaming directly from .fastlaunch_extracted_assets/!");
                }
                cir.setReturnValue(() -> new FileInputStream(cachedFile));
            }
        }
    }
}
