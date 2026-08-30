package com.fastlaunch.mixin;

import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 数百万個生成される ResourceLocation の namespace / path 文字列を
 * 内部キャッシュで共有化 (Flyweight化) し、ヒープ消費を削減する Mixin。
 */
@Mixin(value = ResourceLocation.class, remap = false)
public abstract class FastLaunchResourceLocationDeduplicatorMixin {
    private static final ConcurrentHashMap<String, String> STRING_POOL = new ConcurrentHashMap<>(4096);

    @ModifyVariable(method = "<init>(Ljava/lang/String;Ljava/lang/String;)V", at = @At("HEAD"), ordinal = 0, argsOnly = true, require = 0, remap = false)
    private static String internNamespace(String namespace) {
        if (namespace == null) return null;
        if (namespace.length() <= 32) {
            String pooled = STRING_POOL.putIfAbsent(namespace, namespace);
            return pooled != null ? pooled : namespace;
        }
        return namespace;
    }
}
