package ru.stylemod.style;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Реестр всех действий, за которые дают стиль.
 * Удобно добавлять новые в одном месте.
 */
public class StyleActionRegistry {
    private final Map<String, StyleAction> actions = new HashMap<>();

    public void register(StyleAction action) {
        actions.put(action.getId(), action);
    }

    public StyleAction get(String id) {
        return actions.get(id);
    }

    public boolean has(String id) {
        return actions.containsKey(id);
    }

    public Collection<StyleAction> all() {
        return Collections.unmodifiableCollection(actions.values());
    }

    public int size() {
        return actions.size();
    }
}
