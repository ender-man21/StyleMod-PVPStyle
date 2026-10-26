package ru.stylemod.style;

/**
 * Один бонус, который только что дали.
 * Показывается в HUD несколько секунд.
 */
public class StyleBonus {
    public String name;
    public int basePoints;   // сколько ушло в комбо (без коэфф.)
    public int scorePoints;  // сколько ушло в постоянный стиль (с коэфф.)
    public float coefficient;
    public long timestamp;
    final String stackKey;
    private final String baseName;
    private int stackCount;
    private long lastStackTimestamp;

    public StyleBonus(String name, int basePoints, int scorePoints, float coefficient) {
        this(name, basePoints, scorePoints, coefficient, null, 0);
    }

    public StyleBonus(String name, int basePoints, int scorePoints, float coefficient,
                      String stackKey, int stackCount) {
        this.baseName = name;
        this.stackKey = stackKey;
        this.basePoints = basePoints;
        this.scorePoints = scorePoints;
        this.coefficient = coefficient;
        this.timestamp = System.currentTimeMillis();
        this.stackCount = stackCount;
        this.lastStackTimestamp = stackKey == null ? 0L : this.timestamp;
        this.name = stackKey == null ? name : stackedName(stackCount);
    }

    public void updateStackCount(int stackCount, long now) {
        if (stackKey == null) return;
        this.stackCount = stackCount;
        this.name = stackedName(stackCount);
        this.timestamp = now;
        this.lastStackTimestamp = now;
    }

    /** Updates display points for a stacked action so the HUD shows the latest award. */
    public void updateAward(int basePoints, int scorePoints, float coefficient, int stackCount, long now) {
        if (stackKey == null) return;
        this.basePoints = basePoints;
        this.scorePoints = scorePoints;
        this.coefficient = coefficient;
        this.stackCount = stackCount;
        this.name = stackedName(stackCount);
        this.timestamp = now;
        this.lastStackTimestamp = now;
    }

    public int getStackCount() {
        return stackCount;
    }

    public long getLastStackTimestamp() {
        return lastStackTimestamp;
    }

    public void resetStackWindow() {
        lastStackTimestamp = 0L;
    }

    private String stackedName(int stackCount) {
        return baseName + " x" + stackCount;
    }

    public boolean isExpired(long now, long lifetimeMs) {
        return now - timestamp > lifetimeMs;
    }
}
