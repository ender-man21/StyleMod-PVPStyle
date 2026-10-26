package ru.stylemod.config;

import com.google.gson.annotations.SerializedName;

/** Style gain and damage-loss presets inspired by ULTRAKILL's difficulty names. */
public enum StyleDifficulty {
    @SerializedName("harmless") HARMLESS("harmless", 1.50f, 0.50f, 0.55f, 1.75f),
    @SerializedName("lenient") LENIENT("lenient", 1.25f, 0.75f, 0.75f, 1.35f),
    @SerializedName("standard") STANDARD("standard", 1.10f, 0.90f, 0.90f, 1.15f),
    @SerializedName("violent") VIOLENT("violent", 1.00f, 1.00f, 1.00f, 1.00f),
    @SerializedName("brutal") BRUTAL("brutal", 0.80f, 1.25f, 1.65f, 0.70f);

    private final String id;
    private final float gainMultiplier;
    private final float lossMultiplier;
    private final float comboDecayMultiplier;
    private final float actionWindowMultiplier;

    StyleDifficulty(String id, float gainMultiplier, float lossMultiplier,
                    float comboDecayMultiplier, float actionWindowMultiplier) {
        this.id = id;
        this.gainMultiplier = gainMultiplier;
        this.lossMultiplier = lossMultiplier;
        this.comboDecayMultiplier = comboDecayMultiplier;
        this.actionWindowMultiplier = actionWindowMultiplier;
    }

    public String id() {
        return id;
    }

    public float gainMultiplier() {
        return gainMultiplier;
    }

    public float lossMultiplier() {
        return lossMultiplier;
    }

    public float comboDecayMultiplier() {
        return comboDecayMultiplier;
    }

    public float effectiveComboDecayMultiplier() {
        return lossMultiplier * comboDecayMultiplier;
    }

    public float actionWindowMultiplier() {
        return actionWindowMultiplier;
    }

    public long actionWindowMs(long baseMs) {
        return Math.max(1L, Math.round(baseMs * actionWindowMultiplier));
    }

    public static StyleDifficulty fromId(String id) {
        if (id == null) return null;
        for (StyleDifficulty difficulty : values()) {
            if (difficulty.id.equalsIgnoreCase(id)) return difficulty;
        }
        return null;
    }
}
