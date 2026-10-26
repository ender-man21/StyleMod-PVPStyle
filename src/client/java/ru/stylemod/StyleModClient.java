package ru.stylemod;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.stylemod.action.StyleActions;
import ru.stylemod.combat.CombatStyleHook;
import ru.stylemod.combat.JumpResetTracker;
import ru.stylemod.combat.LungeSoundListener;
import ru.stylemod.command.StyleCommand;
import ru.stylemod.config.StyleConfig;
import ru.stylemod.hud.StyleHud;
import ru.stylemod.style.StyleManager;

public class StyleModClient implements ClientModInitializer {
    public static final String MOD_ID = "style-mod";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }

    @Override
    public void onInitializeClient() {
        LOGGER.info("Style Mod initializing for 1.21.11...");

        StyleConfig.load();
        StyleActions.registerDefaults();
        CombatStyleHook.register();

        HudElementRegistry.attachElementBefore(
                VanillaHudElements.CHAT,
                id("style_hud"),
                StyleHud::render
        );

        boolean[] lungeListenerRegistered = {false};
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (!lungeListenerRegistered[0]) {
                var soundManager = client.getSoundManager();
                if (soundManager != null) {
                    soundManager.addListener(LungeSoundListener.INSTANCE);
                    lungeListenerRegistered[0] = true;
                }
            }

            CombatStyleHook.tick();
            JumpResetTracker.tick();
            if (client.player != null) {
                StyleManager.get().tick();
            }
        });

        ClientCommandRegistrationCallback.EVENT.register(
                (dispatcher, registryAccess) -> StyleCommand.register(dispatcher)
        );

        LOGGER.info("Style Mod loaded. Actions: {}", StyleManager.get().getRegistry().size());
    }
}
