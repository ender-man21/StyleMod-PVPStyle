package ru.stylemod.mixin;

import net.minecraft.client.resources.sounds.EntityBoundSoundInstance;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Exposes the entity that originated an entity-bound sound to the client sound listener. */
@Mixin(EntityBoundSoundInstance.class)
public interface EntityBoundSoundInstanceAccessor {
    @Accessor("entity")
    Entity styleMod$getEntity();
}
