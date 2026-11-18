package com.example.chronorelic.mixin;

import com.example.chronorelic.item.ChronoWatchItem;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {

    @Inject(method = "setHealth", at = @At("HEAD"), cancellable = true)
    private void onFatalHealthChange(float health, CallbackInfo ci) {
        if (health <= 0.0F && (Object) this instanceof ServerPlayer player) {
            if (ChronoWatchItem.tryTriggerDeathSave(player)) {
                ci.cancel();
            }
        }
    }
}