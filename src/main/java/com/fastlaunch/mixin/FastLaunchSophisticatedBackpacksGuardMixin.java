package com.fastlaunch.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.lang.reflect.Method;

/**
 * SophisticatedBackpacks の起動時形状計算 NPE を完全防止する安全ガード Mixin。
 * 起動・リロード中のモデルベイク直後、モデルが null または取得不能な場合でも
 * クラッシュせずにデフォルトのブロック形状へ安全にフォールバックさせる。
 */
@Pseudo
@Mixin(targets = "net.p3pp3rf1y.sophisticatedbackpacks.client.render.ClientBackpackShapeProvider", remap = false)
public abstract class FastLaunchSophisticatedBackpacksGuardMixin {
    private static final Logger LOGGER = LogManager.getLogger("FastLaunch/BackpackGuard");

    @Inject(method = "computeShapeFromLoadedModel", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private void onComputeShapeFromLoadedModel(Minecraft mc, BlockState state, CallbackInfoReturnable<VoxelShape> cir) {
        try {
            if (mc == null || mc.getBlockRenderer() == null) {
                cir.setReturnValue(Shapes.block());
                cir.cancel();
                return;
            }
            // BlockModel が null の場合を早期検知してフォールバック
            if (mc.getBlockRenderer().getBlockModel(state) == null) {
                LOGGER.warn("[BackpackGuard] BlockModel for backpack state {} was null during reload. Providing fallback shape safely.", state);
                VoxelShape fallback = getFallbackShape(state);
                cir.setReturnValue(fallback != null ? fallback : Shapes.block());
                cir.cancel();
            }
        } catch (Throwable t) {
            LOGGER.warn("[BackpackGuard] Error checking model in computeShapeFromLoadedModel: {}", t.getMessage());
            cir.setReturnValue(Shapes.block());
            cir.cancel();
        }
    }

    private static VoxelShape getFallbackShape(BlockState state) {
        try {
            Class<?> shapesClass = Class.forName("net.p3pp3rf1y.sophisticatedbackpacks.backpack.BackpackShapes");
            Method getDef = shapesClass.getMethod("getDefaultShapeProvider");
            Object provider = getDef.invoke(null);
            if (provider != null) {
                Method getShape = provider.getClass().getMethod("getShape", BlockState.class);
                return (VoxelShape) getShape.invoke(provider, state);
            }
        } catch (Throwable ignored) {}
        return Shapes.block();
    }
}
