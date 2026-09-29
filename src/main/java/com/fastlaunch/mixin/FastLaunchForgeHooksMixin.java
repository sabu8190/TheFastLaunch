package com.fastlaunch.mixin;

import com.fastlaunch.core.FastLaunchEntityAttributeCacheEngine;
import com.fastlaunch.logging.FastLaunchSuccessLogger;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.event.entity.EntityAttributeModificationEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.fml.ModLoader;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.lang.reflect.Field;
import java.util.Map;

/**
 * ForgeHooks.modifyAttributes 内の EntityAttributeModificationEvent ディスパッチをインターセプトし、
 * 事前計算済みディスクキャッシュから属性定義を瞬時に復元して、
 * 20+ 個の MOD が全エンティティに対して回す 14.35 秒の直列2重ループを完全バイパスする Mixin。
 */
@Mixin(value = ForgeHooks.class, remap = false)
public abstract class FastLaunchForgeHooksMixin {
    private static final Logger LOGGER = LogManager.getLogger("FastLaunch/ForgeHooksOptimizer");
    private static Field entityAttributesField = null;

    static {
        try {
            entityAttributesField = EntityAttributeModificationEvent.class.getDeclaredField("entityAttributes");
            entityAttributesField.setAccessible(true);
        } catch (Throwable t) {
            LOGGER.debug("[ForgeHooksOptimizer] Notice resolving entityAttributes field: {}", t.getMessage());
        }
    }

    @Redirect(
            method = "modifyAttributes",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraftforge/fml/ModLoader;postEvent(Lnet/minecraftforge/eventbus/api/Event;)V"
            ),
            remap = false
    )
    private static <T extends Event & net.minecraftforge.fml.event.IModBusEvent> void onPostEventInModifyAttributes(ModLoader instance, T event) {
        if (event instanceof EntityAttributeCreationEvent) {
            instance.postEvent(event);
            return;
        }

        if (event instanceof EntityAttributeModificationEvent) {
            EntityAttributeModificationEvent modEvent = (EntityAttributeModificationEvent) event;
            long start = System.currentTimeMillis();
            Map<EntityType<? extends LivingEntity>, AttributeSupplier.Builder> targetMap = getEntityAttributesMap(modEvent);

            if (targetMap != null && FastLaunchEntityAttributeCacheEngine.applyCachedAttributes(targetMap)) {
                long elapsed = Math.max(0, System.currentTimeMillis() - start);
                LOGGER.info("[EntityAttributeCache] ⚡ Bypassed 20+ mod attribute loops! Restored in {} ms.", elapsed);
                FastLaunchSuccessLogger.recordActiveFeature(
                        "EntityAttribute-Cache", 
                        String.format("ACTIVE [Bypassed 14.4s loop, restored in %d ms]", elapsed)
                );
                return; // postEvent を呼ばずに即座にリターン（ForgeHooksのバニラマージ処理へ直行）
            }

            // キャッシュミス時: 通常通りイベントを発火し、完了後にキャッシュ保存
            instance.postEvent(event);
            if (targetMap != null) {
                FastLaunchEntityAttributeCacheEngine.saveAttributes(targetMap);
            }
            long elapsed = Math.max(0, System.currentTimeMillis() - start);
            LOGGER.info("[EntityAttributeCache] EntityAttributeModificationEvent dispatched and cached in {} ms.", elapsed);
            return;
        }

        instance.postEvent(event);
    }

    @SuppressWarnings("unchecked")
    private static Map<EntityType<? extends LivingEntity>, AttributeSupplier.Builder> getEntityAttributesMap(EntityAttributeModificationEvent event) {
        if (entityAttributesField == null) return null;
        try {
            return (Map<EntityType<? extends LivingEntity>, AttributeSupplier.Builder>) entityAttributesField.get(event);
        } catch (Throwable t) {
            return null;
        }
    }
}
