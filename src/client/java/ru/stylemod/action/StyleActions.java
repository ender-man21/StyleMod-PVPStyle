package ru.stylemod.action;

import ru.stylemod.style.StyleAction;

/**
 * Базовые значения очков (до коэффициентов combo × speed).
 */
public final class StyleActions {
    private StyleActions() {}

    public static void registerDefaults() {
        StyleAction.builder("hit")
                .displayName("HIT")
                .points(25)
                .register();

        StyleAction.builder("sprint_hit")
                .displayName("SPRINT HIT")
                .points(100)
                .register();

        StyleAction.builder("mace_hit")
                .displayName("MACE HIT")
                .points(300)
                .register();

        StyleAction.builder("ranged_hit")
                .displayName("RANGED")
                .points(50)
                .register();

        StyleAction.builder("critical_hit")
                .displayName("CRITICAL")
                .points(50)
                .register();

        StyleAction.builder("shield_break")
                .displayName("SHIELD BREAK")
                .points(100)
                .register();

        StyleAction.builder("kill")
                .displayName("KILL")
                .points(200)
                .register();
    }
}
