package ru.stylemod.style;

/**
 * Ранги по падающему комбо-метру.
 * ULTRAKILL достигается до заполнения шкалы (cap > порога ULTRAKILL).
 */
public enum StyleRank {
    NONE(0, "—", 0xAAAAAA),
    DESTRUCTIVE(150, "D", 0x55AAFF),
    CHAOTIC(280, "C", 0x55FF55),
    BRUTAL(420, "B", 0xFFFF55),
    ANARCHIC(580, "A", 0xFFAA00),
    SUPREME(780, "S", 0xFF5555),
    SADISTIC(1000, "SS", 0xFF55FF),
    SHITSTORM(1250, "SSS", 0xFF00AA),
    ULTRAKILL(1500, "ULTRAKILL", 0xFFD700);

    /** Максимум шкалы комбо — выше порога ULTRAKILL, чтобы ранг появлялся не на 100%. */
    public static final int METER_CAP = 2000;

    public final int threshold;
    public final String displayName;
    public final int color;

    StyleRank(int threshold, String displayName, int color) {
        this.threshold = threshold;
        this.displayName = displayName;
        this.color = color;
    }

    public static StyleRank fromPoints(int points) {
        StyleRank result = NONE;
        for (StyleRank rank : values()) {
            if (points >= rank.threshold) {
                result = rank;
            }
        }
        return result;
    }

    public StyleRank previous() {
        int idx = ordinal();
        return idx == 0 ? this : values()[idx - 1];
    }

    public static int cap() {
        return METER_CAP;
    }

    public float decayMultiplier() {
        return switch (this) {
            case NONE -> 1.0f;
            case DESTRUCTIVE -> 1.0f;
            case CHAOTIC -> 1.25f;
            case BRUTAL -> 1.5f;
            case ANARCHIC -> 2.0f;
            case SUPREME -> 3.0f;
            case SADISTIC -> 4.0f;
            case SHITSTORM -> 6.0f;
            case ULTRAKILL -> 8.0f;
        };
    }
}
