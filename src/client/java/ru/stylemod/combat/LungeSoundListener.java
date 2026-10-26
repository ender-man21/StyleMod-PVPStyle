package ru.stylemod.combat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.EntityBoundSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEventListener;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import ru.stylemod.mixin.EntityBoundSoundInstanceAccessor;

/** Counts Lunge activations from their dedicated vanilla sound events, including misses. */
public final class LungeSoundListener implements SoundEventListener {
    public static final LungeSoundListener INSTANCE = new LungeSoundListener();

    private LungeSoundListener() {}

    @Override
    public void onPlaySound(SoundInstance sound, WeighedSoundEvents soundEvent, float range) {
        String id = sound.getIdentifier().toString();
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null) return;

        if (id.equals("minecraft:item.shield.break")) {
            Player target = null;
            if (sound instanceof EntityBoundSoundInstance) {
                Entity source = ((EntityBoundSoundInstanceAccessor) sound).styleMod$getEntity();
                if (source instanceof Player player) target = player;
            }
            if (target == null) {
                // Sound origin can be slightly offset from the entity; 5 blocks is still tight enough.
                double nearestDistance = 25.0;
                for (Player candidate : client.level.players()) {
                    if (candidate == client.player) continue;
                    double distance = candidate.distanceToSqr(sound.getX(), sound.getY(), sound.getZ());
                    if (distance < nearestDistance) {
                        nearestDistance = distance;
                        target = candidate;
                    }
                }
            }
            if (target != null && target != client.player) {
                CombatStyleHook.onShieldDisabled(target.getId());
            }
            return;
        }

        if (!id.equals("minecraft:item.spear.lunge_1")
                && !id.equals("minecraft:item.spear.lunge_2")
                && !id.equals("minecraft:item.spear.lunge_3")) {
            return;
        }

        boolean localSound = false;
        if (sound instanceof EntityBoundSoundInstance) {
            Entity source = ((EntityBoundSoundInstanceAccessor) sound).styleMod$getEntity();
            localSound = source == client.player;
        } else {
            // Positional sounds have no owner; use their proximity to the local player.
            // Do not inspect the held item: attribute swaps can leave another weapon equipped.
            localSound = client.player.distanceToSqr(sound.getX(), sound.getY(), sound.getZ()) <= 36.0;
        }
        if (!localSound) return;

        CombatStyleHook.onLunge(getJabCooldownMs(client));
    }

    private static long getJabCooldownMs(Minecraft client) {
        double attackSpeed = client.player.getAttributeValue(Attributes.ATTACK_SPEED);
        return attackSpeed > 0.0 ? Math.max(1L, Math.round(1000.0 / attackSpeed)) : 1000L;
    }

}
