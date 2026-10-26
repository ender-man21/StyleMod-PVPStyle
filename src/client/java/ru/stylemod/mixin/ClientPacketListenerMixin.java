package ru.stylemod.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundDamageEventPacket;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.stylemod.combat.CombatStyleHook;

/** Reads server-confirmed damage events; target health is not synchronized for all entities. */
@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerMixin {
    @Inject(method = "handleDamageEvent", at = @At("TAIL"))
    private void styleMod$onDamageEvent(ClientboundDamageEventPacket packet, CallbackInfo ci) {
        CombatStyleHook.onConfirmedDamage(
                packet.entityId(),
                packet.sourceCauseId(),
                packet.sourceDirectId(),
                (packet.sourceType().is(DamageTypeTags.IS_PLAYER_ATTACK)
                        || packet.sourceType().is(DamageTypeTags.IS_PROJECTILE)
                        || packet.sourceType().is(DamageTypeTags.IS_EXPLOSION))
                        && !packet.sourceType().is(DamageTypeTags.NO_KNOCKBACK)
        );
    }

    @Inject(method = "handleEntityEvent", at = @At("HEAD"))
    private void styleMod$onEntityEvent(ClientboundEntityEventPacket packet, CallbackInfo ci) {
        if (packet.getEventId() != 30) return;
        Minecraft client = Minecraft.getInstance();
        if (client.level == null) return;
        Entity target = packet.getEntity(client.level);
        if (target instanceof net.minecraft.world.entity.player.Player) {
            CombatStyleHook.onShieldDisabled(target.getId());
        }
    }
}
