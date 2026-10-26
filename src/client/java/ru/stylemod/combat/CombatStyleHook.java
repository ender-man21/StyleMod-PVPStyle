package ru.stylemod.combat;

import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import ru.stylemod.config.StyleConfig;
import ru.stylemod.style.StyleManager;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/** Scores combat actions only after the server reports actual damage. */
public final class CombatStyleHook {
    private static final long ATTACK_ATTEMPT_LIFETIME_MS = 5000L;
    private static final long ACTION_STREAK_WINDOW_MS = 2200L;
    private static final int MAX_ATTEMPTS_PER_ENTITY = 8;
    private static final float CLOSE_CALL_HEALTH = 4.0f;
    private static final long SHIELD_EVENT_DEDUPE_MS = 900L;
    private static final long SHIELD_ATTRIBUTION_WINDOW_MS = 2500L;
    private static final long LUNGE_MACE_WINDOW_MS = 3000L;

    private static final Map<Integer, ArrayDeque<AttackAttempt>> attackAttempts = new HashMap<>();
    private static final Map<Integer, Long> confirmedLocalHits = new HashMap<>();
    private static final Map<Integer, Long> shieldEventTimes = new HashMap<>();
    private static UUID lastTargetId;
    private static long lastHitMs;
    private static boolean killCounted;
    private static boolean deathHandled;
    private static GameType lastGameMode;
    private static ClientLevel lastLevel;

    // A PvP sprint combo follows one target and ends when we are damaged, switch
    // targets, stop sprint-hitting, or let the short continuation window expire.
    private static UUID sprintComboTargetId;
    private static int sprintComboStreak;
    private static long lastSprintComboHitMs;
    private static boolean closeCallAwarded;
    private static int lungeStreak;
    private static long lastLungeMs;
    private static long lungeCooldownMs;
    private static long lungeBonusExpiresAtMs;

    private CombatStyleHook() {}

    public static void register() {
        AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            Minecraft client = Minecraft.getInstance();
            if (client.player == null || player != client.player || !(entity instanceof LivingEntity target)) {
                return InteractionResult.PASS;
            }

            // Save attack details only; points are awarded after the server confirms damage.
            long now = System.currentTimeMillis();
            ArrayDeque<AttackAttempt> attempts = attackAttempts.computeIfAbsent(target.getId(), ignored -> new ArrayDeque<>());
            pruneAttempts(attempts, now);
            if (attempts.size() == MAX_ATTEMPTS_PER_ENTITY) attempts.removeFirst();
            attempts.addLast(AttackAttempt.capture(player, now));
            return InteractionResult.PASS;
        });
    }

    /** Called from ClientPacketListenerMixin for a server-confirmed damage packet. */
    public static void onConfirmedDamage(int targetEntityId, int causingEntityId, int directEntityId,
                                         boolean knockbackDamage) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null) return;

        Player player = client.player;
        if (targetEntityId == player.getId()) {
            StyleManager.get().onDamageTaken(1.0f);
            onPlayerDamaged();
            // Environmental damage has no attacker id and cannot produce a
            // player-combat jump reset. The damage penalty still applies above.
            if (knockbackDamage && causingEntityId >= 0 && causingEntityId != player.getId()) {
                JumpResetTracker.onCombatHit(1.0f);
            }
            return;
        }

        if (causingEntityId != player.getId()) return;
        long confirmedAt = System.currentTimeMillis();
        confirmedLocalHits.put(targetEntityId, confirmedAt);
        Entity entity = client.level.getEntity(targetEntityId);
        if (!(entity instanceof LivingEntity target)) return;

        awardCloseCall(player);

        lastTargetId = target.getUUID();
        lastHitMs = System.currentTimeMillis();
        killCounted = false;

        if (directEntityId != player.getId()) {
            // A projectile must not consume a queued melee-click candidate.
            StyleManager manager = StyleManager.get();
            manager.addStackedStyle("ranged_hit", "RANGED", 50, actionStreakWindowMs());
            manager.addStackedStyle("hit", "HIT", 25, actionStreakWindowMs());
            resetSprintCombo();
            return;
        }

        AttackAttempt attempt = consumeAttempt(targetEntityId, System.currentTimeMillis());
        if (attempt == null) {
            // Covers confirmed melee hits where another mod bypassed the local callback.
            StyleManager manager = StyleManager.get();
            manager.addStackedStyle("hit", "HIT", 25, actionStreakWindowMs());
            if (!hasAttackDamageModifier(player.getMainHandItem())) {
                manager.addStackedStyle("disrespect", "DISRESPECT", 75, actionStreakWindowMs());
            }
            updateSprintCombo(target, player.isSprinting());
            return;
        }

        awardConfirmedMeleeHit(target, attempt);
    }

    public static void tick() {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null) {
            if (lastLevel != null) {
                resetTracking();
                lastLevel = null;
                lastGameMode = null;
            }
            return;
        }

        if (lastLevel != client.level) {
            if (lastLevel != null) resetTracking();
            lastLevel = client.level;
        }

        if (client.gameMode != null) {
            GameType mode = client.gameMode.getPlayerMode();
            if (lastGameMode != null && mode != lastGameMode) {
                resetMechanics();
                if (StyleConfig.get().resetOnGameModeChange) StyleManager.get().reset();
            }
            lastGameMode = mode;
        }

        if (client.player.isDeadOrDying() || client.player.getHealth() <= 0.0f) {
            if (!deathHandled) {
                deathHandled = true;
                StyleManager.get().onDeath();
                cleanup();
            }
            return;
        }
        deathHandled = false;
        long now = System.currentTimeMillis();
        confirmedLocalHits.values().removeIf(timestamp -> now - timestamp > SHIELD_ATTRIBUTION_WINDOW_MS);
        shieldEventTimes.values().removeIf(timestamp -> now - timestamp > 5000L);
        if (client.player.getHealth() > CLOSE_CALL_HEALTH) closeCallAwarded = false;
        pruneAttempts(now);
        if (!client.player.isSprinting()) resetSprintCombo();
        checkKill(client);
        if (sprintComboStreak > 0 && now - lastSprintComboHitMs > actionStreakWindowMs()) {
            resetSprintCombo();
        }
    }

    public static void onPlayerKilled(UUID victimId) {
        if (victimId == null || !victimId.equals(lastTargetId) || killCounted
                || System.currentTimeMillis() - lastHitMs > 2500L) return;
        doKill();
    }

    public static void onPlayerDamaged() {
        resetSprintCombo();
        resetLungeChain();
    }

    /** Called when the local player's Lunge sound event is played. */
    public static void onLunge(long jabCooldownMs) {
        long now = System.currentTimeMillis();
        if (lungeStreak > 0 && now - lastLungeMs > lungeCooldownMs) lungeStreak = 0;
        lungeStreak = Math.min(8, lungeStreak + 1);
        lastLungeMs = now;
        lungeCooldownMs = Math.max(1L, jabCooldownMs);
        // Keep the accumulated bonus through the spear-to-mace swap and follow-up hit.
        lungeBonusExpiresAtMs = now + LUNGE_MACE_WINDOW_MS;
    }

    public static int getLungeStreak() {
        expireLungeChain();
        return lungeStreak;
    }

    public static float getLungeMaceMultiplier() {
        expireLungeChain();
        return lungeStreak == 0 ? 1.0f : Math.min(2.0f, 1.0f + 0.15f * lungeStreak);
    }

    private static float consumeLungeMaceMultiplier() {
        float multiplier = getLungeMaceMultiplier();
        resetLungeChain();
        return multiplier;
    }

    /** Called only for the server-confirmed entity-status event 30 (shield disabled). */
    public static void onShieldDisabled(int targetEntityId) {
        long now = System.currentTimeMillis();
        confirmedLocalHits.values().removeIf(timestamp -> now - timestamp > SHIELD_ATTRIBUTION_WINDOW_MS);
        shieldEventTimes.values().removeIf(timestamp -> now - timestamp > 5000L);

        // Prefer any pending local attack on this entity (axe/mace click), then a confirmed hit.
        ArrayDeque<AttackAttempt> attempts = attackAttempts.get(targetEntityId);
        boolean recentAttackAttempt = false;
        if (attempts != null) {
            pruneAttempts(attempts, now);
            if (!attempts.isEmpty()) {
                long age = now - attempts.peekLast().timestamp;
                recentAttackAttempt = age <= SHIELD_ATTRIBUTION_WINDOW_MS;
            }
            if (attempts.isEmpty()) attackAttempts.remove(targetEntityId);
        }
        Long confirmedAt = confirmedLocalHits.get(targetEntityId);
        boolean recentConfirmedHit = confirmedAt != null
                && now - confirmedAt <= SHIELD_ATTRIBUTION_WINDOW_MS;

        // Also accept a very recent local attack on *any* entity if the target just lost shield
        // (covers the case where entity-id mapping is delayed or the damage packet arrives later).
        boolean anyRecentLocalAttack = false;
        if (!recentAttackAttempt && !recentConfirmedHit) {
            for (ArrayDeque<AttackAttempt> queue : attackAttempts.values()) {
                if (queue.isEmpty()) continue;
                if (now - queue.peekLast().timestamp <= 800L) {
                    anyRecentLocalAttack = true;
                    break;
                }
            }
        }

        if (!recentAttackAttempt && !recentConfirmedHit && !anyRecentLocalAttack) return;

        // A single shield-disable can produce duplicate status notifications.
        Long previousShieldEvent = shieldEventTimes.get(targetEntityId);
        if (previousShieldEvent != null && now - previousShieldEvent < SHIELD_EVENT_DEDUPE_MS) return;
        shieldEventTimes.put(targetEntityId, now);
        StyleManager.get().addStackedStyle("shield_break", "SHIELD BREAK", 100,
                actionStreakWindowMs());
    }

    private static void awardConfirmedMeleeHit(LivingEntity target, AttackAttempt attempt) {
        StyleManager manager = StyleManager.get();
        manager.addStackedStyle("hit", "HIT", 25, actionStreakWindowMs());
        updateSprintCombo(target, attempt.sprinting);

        if (attempt.mace) {
            float lungeMultiplier = consumeLungeMaceMultiplier();
            int macePoints = Math.round(attempt.macePoints * lungeMultiplier);
            manager.addStackedStyle("mace_hit", "MACE HIT", macePoints, actionStreakWindowMs());
        }
        if (attempt.critical) manager.addStackedStyle("critical_hit", "CRITICAL", 50, actionStreakWindowMs());

        if (attempt.disrespect) {
            manager.addStackedStyle("disrespect", "DISRESPECT", 75, actionStreakWindowMs());
        }
    }

    private static void updateSprintCombo(LivingEntity target, boolean sprinting) {
        if (!sprinting) {
            resetSprintCombo();
            return;
        }

        long now = System.currentTimeMillis();
        UUID targetId = target.getUUID();
        if (targetId.equals(sprintComboTargetId) && now - lastSprintComboHitMs <= actionStreakWindowMs()) {
            sprintComboStreak++;
        } else {
            sprintComboTargetId = targetId;
            sprintComboStreak = 1;
        }
        lastSprintComboHitMs = now;
        StyleManager.get().addStackedStyle("sprint_hit", "SPRINT HIT", 100, sprintComboStreak);
    }

    private static void awardCloseCall(Player player) {
        if (closeCallAwarded || player.getHealth() > CLOSE_CALL_HEALTH) return;
        closeCallAwarded = true;
        StyleManager.get().addStackedStyle("close_call", "CLOSE CALL", 100, actionStreakWindowMs());
    }

    private static void checkKill(Minecraft client) {
        if (lastTargetId == null || killCounted || System.currentTimeMillis() - lastHitMs > 2500L) return;
        Entity entity = findEntity(client, lastTargetId);
        if (entity instanceof Player target && (target.isDeadOrDying() || target.getHealth() <= 0.0f)) {
            onPlayerKilled(target.getUUID());
        }
    }

    private static void doKill() {
        if (killCounted) return;
        killCounted = true;
        StyleManager manager = StyleManager.get();
        manager.trigger("kill");
        manager.onKill();
        lastTargetId = null;
        resetSprintCombo();
    }

    private static AttackAttempt consumeAttempt(int entityId, long now) {
        ArrayDeque<AttackAttempt> attempts = attackAttempts.get(entityId);
        if (attempts == null) return null;
        pruneAttempts(attempts, now);
        // Server-confirmed hits arrive in attack order, so pair them with the
        // oldest pending click rather than a newer click sent while awaiting the reply.
        AttackAttempt result = attempts.pollFirst();
        if (attempts.isEmpty()) attackAttempts.remove(entityId);
        return result;
    }

    private static void pruneAttempts(long now) {
        Iterator<Map.Entry<Integer, ArrayDeque<AttackAttempt>>> iterator = attackAttempts.entrySet().iterator();
        while (iterator.hasNext()) {
            ArrayDeque<AttackAttempt> attempts = iterator.next().getValue();
            pruneAttempts(attempts, now);
            if (attempts.isEmpty()) iterator.remove();
        }
    }

    private static void pruneAttempts(ArrayDeque<AttackAttempt> attempts, long now) {
        while (!attempts.isEmpty() && now - attempts.peekFirst().timestamp > ATTACK_ATTEMPT_LIFETIME_MS) {
            attempts.removeFirst();
        }
    }

    private static void resetMechanics() {
        attackAttempts.clear();
        confirmedLocalHits.clear();
        shieldEventTimes.clear();
        closeCallAwarded = false;
        resetSprintCombo();
        resetLungeChain();
        lastTargetId = null;
        lastHitMs = 0L;
        killCounted = false;
    }

    /** Clears pending combat attribution after a manual style reset. */
    public static void resetTracking() {
        resetMechanics();
        lastTargetId = null;
        lastHitMs = 0L;
        killCounted = false;
        deathHandled = false;
    }

    private static void cleanup() {
        lastTargetId = null;
        killCounted = false;
        closeCallAwarded = false;
        resetMechanics();
    }

    private static void resetSprintCombo() {
        sprintComboTargetId = null;
        sprintComboStreak = 0;
        lastSprintComboHitMs = 0;
    }

    private static long actionStreakWindowMs() {
        return StyleConfig.get().difficulty.actionWindowMs(ACTION_STREAK_WINDOW_MS);
    }

    private static void expireLungeChain() {
        if (lungeStreak > 0 && System.currentTimeMillis() > lungeBonusExpiresAtMs) resetLungeChain();
    }

    private static void resetLungeChain() {
        lungeStreak = 0;
        lastLungeMs = 0L;
        lungeCooldownMs = 0L;
        lungeBonusExpiresAtMs = 0L;
    }

    private static Entity findEntity(Minecraft client, UUID id) {
        if (client.level == null) return null;
        for (Entity entity : client.level.entitiesForRendering()) {
            if (id.equals(entity.getUUID())) return entity;
        }
        return null;
    }

    private record AttackAttempt(long timestamp, boolean sprinting, boolean mace,
                                 int macePoints, boolean critical, boolean disrespect) {
        private static AttackAttempt capture(Player player, long now) {
            ItemStack mainHand = player.getMainHandItem();
            boolean mace = mainHand.is(Items.MACE);
            float fall = (float) player.fallDistance;
            int macePoints = Math.round(300.0f * (1.0f + Math.min(1.0f, fall / 8.0f)));
            return new AttackAttempt(now, player.isSprinting(), mace, macePoints,
                    isCritical(player), !hasAttackDamageModifier(mainHand));
        }

        private static boolean isCritical(Player player) {
            return player.fallDistance > 0.0f && !player.onGround() && !player.isInWater()
                    && !player.onClimbable() && !player.isPassenger()
                    && !player.hasEffect(MobEffects.BLINDNESS)
                    && !player.hasEffect(MobEffects.SLOW_FALLING) && !player.isSprinting();
        }
    }

    private static boolean hasAttackDamageModifier(ItemStack stack) {
        ItemAttributeModifiers modifiers = stack.get(DataComponents.ATTRIBUTE_MODIFIERS);
        if (modifiers == null) return false;
        return modifiers.modifiers().stream().anyMatch(entry ->
                entry.attribute().equals(Attributes.ATTACK_DAMAGE)
                        && entry.slot().test(EquipmentSlot.MAINHAND));
    }

}
