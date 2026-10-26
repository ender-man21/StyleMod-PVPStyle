package ru.stylemod.style;

import ru.stylemod.config.StyleConfig;
import ru.stylemod.config.StyleDifficulty;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * combo  — падающий метр (ранг).
 * score  — постоянный стиль.
 *
 * Итоговый множитель очков: comboCoeff * speedCoeff
 * Урон: −75 * speedCoeff (по score и combo).
 * Смерть: полный сброс.
 */
public class StyleManager {
    private static final StyleManager INSTANCE = new StyleManager();

    public static StyleManager get() {
        return INSTANCE;
    }

    private static final float COMBO_COEFF_MIN = 1.0f;
    private static final float COMBO_COEFF_MAX = 4.0f;
    private static final float SPEED_COEFF_MAX = 2.0f;

    private static final float BASE_DECAY_PER_TICK = 15.0f / 20.0f;
    private static final long BONUS_LIFETIME_MS = 4000;
    private static final long KILL_DISPLAY_MS = 2200;
    private static final long COMBO_LOCK_MS = 1000;

    private static final int DAMAGE_PENALTY = 75;

    private float combo = 0f;
    private int score = 0;
    private StyleRank rank = StyleRank.NONE;

    private final StyleActionRegistry registry = new StyleActionRegistry();
    private final List<StyleBonus> recentBonuses = new ArrayList<>();

    private int lastKillStyle = 0;
    private long lastKillTimestamp = 0;
    private long lastPositiveStyleTimestamp = 0;
    private long lastDamageTimestamp = 0;
    private int lastDamagePenalty = 0;
    private long comboLockedUntilMs = 0;

    private StyleManager() {}

    public StyleActionRegistry getRegistry() {
        return registry;
    }

    /** Коэффициент от комбо-метра (1.0 → 4.0). */
    public float getComboCoefficient() {
        float t = combo / (float) StyleRank.cap();
        if (t < 0f) t = 0f;
        if (t > 1f) t = 1f;
        return COMBO_COEFF_MIN + t * (COMBO_COEFF_MAX - COMBO_COEFF_MIN);
    }

    /** Коэффициент скорости горизонтального движения (1.0 → 2.0). */
    public float getSpeedCoefficient() {
        Minecraft client = Minecraft.getInstance();
        Player player = client.player;
        if (player == null) return 1.0f;

        Vec3 v = player.getDeltaMovement();
        double horizontal = Math.sqrt(v.x * v.x + v.z * v.z);
        // ~0 при стоянии, ~0.28 спринт — линейно до SPEED_COEFF_MAX
        float coeff = 1.0f + (float) (horizontal * 2.8);
        if (coeff > SPEED_COEFF_MAX) coeff = SPEED_COEFF_MAX;
        if (coeff < 1.0f) coeff = 1.0f;
        return coeff;
    }

    /** Общий множитель для начисления очков. */
    public float getCoefficient() {
        return getComboCoefficient() * getSpeedCoefficient();
    }

    public void trigger(String actionId) {
        StyleAction action = registry.get(actionId);
        if (action == null) return;
        addStyle(action.getDisplayName(), action.getPoints());
        if (action.locksCombo()) {
            comboLockedUntilMs = System.currentTimeMillis() + COMBO_LOCK_MS;
        }
    }

    public void addStyle(String displayName, int points, boolean ignored) {
        addStyle(displayName, points);
    }

    public void addStyle(String displayName, int points) {
        addStyle(displayName, points, null, 0);
    }

    private void addStyle(String displayName, int points, String stackKey, int stackCount) {
        if (points == 0) return;

        StyleDifficulty difficulty = StyleConfig.get().difficulty;
        float difficultyMultiplier = points > 0
                ? difficulty.gainMultiplier() : difficulty.lossMultiplier();
        float adjustedPoints = points * difficultyMultiplier;
        float coeff = getCoefficient();
        int awarded = Math.round(adjustedPoints * coeff);
        if (adjustedPoints > 0 && awarded < 1) awarded = 1;
        if (adjustedPoints < 0 && awarded > -1) awarded = -1;

        long updatedScore = (long) score + awarded;
        score = (int) Math.max(0L, Math.min(Integer.MAX_VALUE, updatedScore));
        if (awarded > 0) {
            combo = Math.min(StyleRank.cap(), combo + Math.abs(adjustedPoints));
            lastPositiveStyleTimestamp = System.currentTimeMillis();
        } else {
            combo = Math.max(0f, combo + adjustedPoints);
        }
        updateRank();

        long now = System.currentTimeMillis();
        if (stackKey == null) {
            recentBonuses.add(0, new StyleBonus(displayName, Math.round(adjustedPoints), awarded, coeff));
        } else {
            StyleBonus existing = findStackedBonus(stackKey, now);
            if (existing != null) {
                existing.updateAward(Math.round(adjustedPoints), awarded, coeff, stackCount, now);
            } else {
                recentBonuses.add(0, new StyleBonus(displayName, Math.round(adjustedPoints), awarded,
                        coeff, stackKey, stackCount));
            }
        }
        trimRecentBonuses();
    }

    /** Adds the normal action value and labels consecutive repetitions as x1, x2... */
    public int addStackedStyle(String actionId, String displayName, int points, long windowMs) {
        long now = System.currentTimeMillis();
        StyleBonus previous = findStackedBonus(actionId, now);
        int count = previous != null && now - previous.getLastStackTimestamp() <= windowMs
                ? previous.getStackCount() + 1 : 1;
        addStackedStyle(actionId, displayName, points, count);
        return count;
    }

    /** Awards points while updating the existing HUD row for this action. */
    public void addStackedStyle(String actionId, String displayName, int points, int displayCount) {
        addStyle(displayName, points, actionId, displayCount);
    }

    private StyleBonus findStackedBonus(String actionId, long now) {
        for (StyleBonus bonus : recentBonuses) {
            if (actionId.equals(bonus.stackKey) && !bonus.isExpired(now, BONUS_LIFETIME_MS)) {
                return bonus;
            }
        }
        return null;
    }

    private void resetStackWindows() {
        for (StyleBonus bonus : recentBonuses) bonus.resetStackWindow();
    }

    private void trimRecentBonuses() {
        while (recentBonuses.size() > 6) {
            recentBonuses.remove(recentBonuses.size() - 1);
        }
    }

    /** Получение урона: штраф по score и combo и сброс серий действий. */
    public void onDamageTaken(float damage) {
        if (damage <= 0.01f) return;
        lastDamageTimestamp = System.currentTimeMillis();

        resetStackWindows();
        comboLockedUntilMs = 0;

        float speed = getSpeedCoefficient();
        float difficultyLoss = StyleConfig.get().difficulty.lossMultiplier();
        int basePenalty = Math.max(1, Math.round(DAMAGE_PENALTY * difficultyLoss));
        int penalty = Math.max(1, Math.round(basePenalty * speed));

        score = Math.max(0, score - penalty);
        combo = Math.max(0f, combo - penalty);

        StyleRank dropped = rank.previous();
        if (dropped != rank && combo < dropped.threshold) {
            combo = dropped.threshold;
        }
        updateRank();

        lastDamagePenalty = penalty;
        // DAMAGED is shown in a dedicated HUD element, not in the event feed.
    }

    /** Своя смерть — тихий сброс (если включено в конфиге). */
    public void onDeath() {
        if (!StyleConfig.get().resetOnDeath) return;
        reset();
        lastKillStyle = 0;
        lastKillTimestamp = 0;
    }

    /** Килл врага: показать score; сброс — если resetOnKill. */
    public void onKill() {
        if (score > 0 || combo > 0) {
            lastKillStyle = score;
            lastKillTimestamp = System.currentTimeMillis();
        }
        resetStackWindows();
        comboLockedUntilMs = 0;
        if (StyleConfig.get().resetOnKill) {
            int bannerScore = lastKillStyle;
            long bannerTimestamp = lastKillTimestamp;
            reset();
            lastKillStyle = bannerScore;
            lastKillTimestamp = bannerTimestamp;
        }
    }

    public void reset() {
        combo = 0f;
        score = 0;
        rank = StyleRank.NONE;
        recentBonuses.clear();
        lastPositiveStyleTimestamp = 0;
        lastDamageTimestamp = 0;
        lastDamagePenalty = 0;
        lastKillStyle = 0;
        lastKillTimestamp = 0;
        comboLockedUntilMs = 0;
    }

    public int getCombo() {
        return Math.round(combo);
    }

    public float getComboExact() {
        return combo;
    }

    public int getScore() {
        return score;
    }

    public int getTotalStyle() {
        return score;
    }

    public int getCurrentStyle() {
        return getCombo();
    }

    public int getLockedStyle() {
        return score;
    }

    public StyleRank getRank() {
        return rank;
    }

    public List<StyleBonus> getRecentBonuses() {
        return recentBonuses;
    }

    public boolean isShowingKillScore() {
        return System.currentTimeMillis() - lastKillTimestamp < KILL_DISPLAY_MS;
    }

    public int getLastKillStyle() {
        return lastKillStyle;
    }

    public long getLastPositiveStyleTimestamp() {
        return lastPositiveStyleTimestamp;
    }

    public long getLastDamageTimestamp() {
        return lastDamageTimestamp;
    }

    public int getLastDamagePenalty() {
        return lastDamagePenalty;
    }

    public boolean isShowingDamagePenalty() {
        return System.currentTimeMillis() - lastDamageTimestamp < 1800L;
    }

    public void tick() {
        long now = System.currentTimeMillis();
        recentBonuses.removeIf(b -> b.isExpired(now, BONUS_LIFETIME_MS));

        if (combo > 0f && now >= comboLockedUntilMs) {
            float decay = BASE_DECAY_PER_TICK * rank.decayMultiplier()
                    * StyleConfig.get().difficulty.lossMultiplier()
                    * StyleConfig.get().difficulty.comboDecayMultiplier();
            combo = Math.max(0f, combo - decay);
            updateRank();
        }
    }

    private void updateRank() {
        rank = StyleRank.fromPoints(Math.round(combo));
    }
}
