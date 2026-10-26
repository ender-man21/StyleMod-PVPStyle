package ru.stylemod.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.stylemod.combat.CombatStyleHook;

/**
 * Ловим смерть LivingEntity.
 * Если это игрок и убийца — мы, то считаем килл.
 */
@Mixin(LivingEntity.class)
public class LivingEntityMixin {

    @Inject(method = "die", at = @At("HEAD"))
    private void onDie(DamageSource damageSource, CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (!(self instanceof Player)) return;

        Minecraft client = Minecraft.getInstance();
        if (client.player == null) return;

        if (damageSource.getEntity() == client.player) {
            CombatStyleHook.onPlayerKilled(self.getUUID());
        }
    }
}
