package ru.stylemod.style;

/**
 * Описание одного действия, за которое дают стиль.
 *
 * Как добавлять новое:
 *
 * Или через билдер:
 *   StyleAction.builder("mace_hit")
 *       .displayName("MACE HIT")
 *       .points(300)
 *       .locksCombo(true)
 *       .register();
 */
public class StyleAction {
    private final String id;
    private final String displayName;
    private final int points;
    private final boolean locksCombo; // true = закрепляет текущий стиль (не даёт decay сбросить)

    public StyleAction(String id, String displayName, int points, boolean locksCombo) {
        this.id = id;
        this.displayName = displayName;
        this.points = points;
        this.locksCombo = locksCombo;
    }

    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public int getPoints() {
        return points;
    }

    public boolean locksCombo() {
        return locksCombo;
    }

    // --- Удобный билдер ---
    public static Builder builder(String id) {
        return new Builder(id);
    }

    public static class Builder {
        private final String id;
        private String displayName;
        private int points = 50;
        private boolean locksCombo = false;

        public Builder(String id) {
            this.id = id;
            this.displayName = id.toUpperCase().replace('_', ' ');
        }

        public Builder displayName(String name) {
            this.displayName = name;
            return this;
        }

        public Builder points(int points) {
            this.points = points;
            return this;
        }

        public Builder locksCombo(boolean locks) {
            this.locksCombo = locks;
            return this;
        }

        public StyleAction build() {
            return new StyleAction(id, displayName, points, locksCombo);
        }

        /** Сразу регистрирует в глобальный реестр */
        public StyleAction register() {
            StyleAction action = build();
            StyleManager.get().getRegistry().register(action);
            return action;
        }
    }
}
