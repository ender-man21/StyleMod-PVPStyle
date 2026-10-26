package ru.stylemod.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * Конфиг: config/style-mod.json
 */
public class StyleConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger("style-mod");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve("style-mod.json");

    private static StyleConfig INSTANCE = new StyleConfig();

    public static StyleConfig get() {
        return INSTANCE;
    }

    // --- HUD position ---
    public int hudOffsetX = 12;
    public int hudOffsetY = 12;

    // --- HUD visibility ---
    public boolean showRank = true;
    public boolean showScore = true;
    public boolean showMultiplier = true;
    public boolean showComboBar = true;
    public boolean showBonuses = true;
    public boolean showJumpReset = true;
    public boolean showKillBanner = true;
    public boolean showLungeCounter = true;

    // --- Reset rules ---
    public boolean resetOnGameModeChange = true;
    public boolean resetOnKill = true;
    public boolean resetOnDeath = true;

    // --- Features ---
    public boolean jumpResetEnabled = true;
    public StyleDifficulty difficulty = StyleDifficulty.VIOLENT;

    public static void load() {
        if (!Files.exists(PATH)) {
            INSTANCE = new StyleConfig();
            save();
            LOGGER.info("Created default config at {}", PATH);
            return;
        }
        try (Reader reader = Files.newBufferedReader(PATH)) {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = GSON.fromJson(reader, Map.class);
            StyleConfig cfg = new StyleConfig();
            if (map != null) {
                applyInt(map, "hudOffsetX", v -> cfg.hudOffsetX = v);
                applyInt(map, "hudOffsetY", v -> cfg.hudOffsetY = v);
                applyBool(map, "showRank", v -> cfg.showRank = v);
                applyBool(map, "showScore", v -> cfg.showScore = v);
                applyBool(map, "showMultiplier", v -> cfg.showMultiplier = v);
                applyBool(map, "showComboBar", v -> cfg.showComboBar = v);
                applyBool(map, "showBonuses", v -> cfg.showBonuses = v);
                applyBool(map, "showJumpReset", v -> cfg.showJumpReset = v);
                applyBool(map, "showKillBanner", v -> cfg.showKillBanner = v);
                applyBool(map, "showLungeCounter", v -> cfg.showLungeCounter = v);
                applyBool(map, "resetOnGameModeChange", v -> cfg.resetOnGameModeChange = v);
                applyBool(map, "resetOnKill", v -> cfg.resetOnKill = v);
                applyBool(map, "resetOnDeath", v -> cfg.resetOnDeath = v);
                applyBool(map, "jumpResetEnabled", v -> cfg.jumpResetEnabled = v);
                if (map.get("difficulty") instanceof String value) {
                    StyleDifficulty parsed = StyleDifficulty.fromId(value);
                    if (parsed != null) cfg.difficulty = parsed;
                }
            }
            INSTANCE = cfg;
            save();
            LOGGER.info("Loaded config from {}", PATH);
        } catch (Exception e) {
            LOGGER.error("Failed to load config, using defaults", e);
            INSTANCE = new StyleConfig();
        }
    }

    private static void applyInt(Map<String, Object> map, String key, java.util.function.IntConsumer c) {
        if (map.containsKey(key) && map.get(key) instanceof Number n) {
            c.accept(n.intValue());
        }
    }

    private static void applyBool(Map<String, Object> map, String key, java.util.function.Consumer<Boolean> c) {
        if (map.containsKey(key) && map.get(key) instanceof Boolean b) {
            c.accept(b);
        }
    }

    public static void save() {
        try {
            Files.createDirectories(PATH.getParent());
            try (Writer writer = Files.newBufferedWriter(PATH)) {
                GSON.toJson(INSTANCE, writer);
            }
        } catch (IOException e) {
            LOGGER.error("Failed to save config", e);
        }
    }

    public void setHudOffsetX(int x) {
        this.hudOffsetX = Math.max(0, x);
        save();
    }

    public void setHudOffsetY(int y) {
        this.hudOffsetY = Math.max(0, y);
        save();
    }

    public void setResetOnGameModeChange(boolean v) {
        this.resetOnGameModeChange = v;
        save();
    }

    public void setResetOnKill(boolean v) {
        this.resetOnKill = v;
        save();
    }

    public void setResetOnDeath(boolean v) {
        this.resetOnDeath = v;
        save();
    }

    public void setJumpResetEnabled(boolean v) {
        this.jumpResetEnabled = v;
        save();
    }

    public void setDifficulty(StyleDifficulty difficulty) {
        if (difficulty == null) return;
        this.difficulty = difficulty;
        save();
    }

    public void setShow(String part, boolean v) {
        switch (part) {
            case "rank" -> showRank = v;
            case "score" -> showScore = v;
            case "multiplier" -> showMultiplier = v;
            case "combo_bar" -> showComboBar = v;
            case "bonuses" -> showBonuses = v;
            case "jump_reset" -> showJumpReset = v;
            case "kill_banner" -> showKillBanner = v;
            case "lunge_counter" -> showLungeCounter = v;
            default -> {
                return;
            }
        }
        save();
    }
}
