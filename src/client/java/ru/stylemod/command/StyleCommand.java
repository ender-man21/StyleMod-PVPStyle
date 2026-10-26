package ru.stylemod.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.network.chat.Component;
import ru.stylemod.combat.CombatStyleHook;
import ru.stylemod.config.StyleConfig;
import ru.stylemod.config.StyleDifficulty;
import ru.stylemod.style.StyleManager;

public final class StyleCommand {
    private StyleCommand() {}

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(ClientCommandManager.literal("style")
                .then(ClientCommandManager.literal("add")
                        .then(ClientCommandManager.argument("points", IntegerArgumentType.integer(1))
                                .executes(ctx -> {
                                    int pts = IntegerArgumentType.getInteger(ctx, "points");
                                    StyleManager.get().addStyle("DEBUG", pts, false);
                                    ctx.getSource().sendFeedback(Component.literal("Added " + pts));
                                    return 1;
                                })))
                .then(ClientCommandManager.literal("trigger")
                        .then(ClientCommandManager.argument("id", StringArgumentType.string())
                                .executes(ctx -> {
                                    String id = StringArgumentType.getString(ctx, "id");
                                    StyleManager.get().trigger(id);
                                    ctx.getSource().sendFeedback(Component.literal("Triggered: " + id));
                                    return 1;
                                })))
                .then(ClientCommandManager.literal("reset")
                        .executes(ctx -> {
                            StyleManager.get().reset();
                            CombatStyleHook.resetTracking();
                            ctx.getSource().sendFeedback(Component.literal("Style reset"));
                            return 1;
                        }))
                .then(ClientCommandManager.literal("jr_test")
                        .executes(ctx -> {
                            ru.stylemod.combat.JumpResetTracker.onCombatHit(2.0f);
                            return 1;
                        }))
                .then(ClientCommandManager.literal("info")
                        .executes(ctx -> {
                            var s = StyleManager.get();
                            var c = StyleConfig.get();
                            ctx.getSource().sendFeedback(Component.literal(
                                    "Score: " + s.getScore() +
                                    " | Combo: " + s.getCombo() +
                                    " | Rank: " + s.getRank().displayName +
                                    " | Difficulty: " + c.difficulty.id() +
                                    " | JR: " + c.jumpResetEnabled
                            ));
                            return 1;
                        }))
                .then(ClientCommandManager.literal("difficulty")
                        .executes(ctx -> {
                            StyleDifficulty difficulty = StyleConfig.get().difficulty;
                            ctx.getSource().sendFeedback(Component.literal(
                                    "Difficulty = " + difficulty.id() +
                                    " (gain x" + difficulty.gainMultiplier() +
                                    ", loss x" + difficulty.lossMultiplier() +
                                    ", combo decay x" + difficulty.effectiveComboDecayMultiplier() +
                                    ", chain window x" + difficulty.actionWindowMultiplier() + ")"));
                            return 1;
                        })
                        .then(ClientCommandManager.argument("value", StringArgumentType.word())
                                .suggests((ctx, builder) -> {
                                    for (StyleDifficulty difficulty : StyleDifficulty.values()) {
                                        builder.suggest(difficulty.id());
                                    }
                                    return builder.buildFuture();
                                })
                                .executes(ctx -> {
                                    String value = StringArgumentType.getString(ctx, "value");
                                    StyleDifficulty difficulty = StyleDifficulty.fromId(value);
                                    if (difficulty == null) {
                                        ctx.getSource().sendFeedback(Component.literal(
                                                "Choose harmless, lenient, standard, violent, or brutal"));
                                        return 0;
                                    }
                                    StyleConfig.get().setDifficulty(difficulty);
                                    ctx.getSource().sendFeedback(Component.literal(
                                            "Difficulty = " + difficulty.id() +
                                            " (gain x" + difficulty.gainMultiplier() +
                                            ", loss x" + difficulty.lossMultiplier() +
                                            ", combo decay x" + difficulty.effectiveComboDecayMultiplier() +
                                            ", chain window x" + difficulty.actionWindowMultiplier() + ")"));
                                    return 1;
                                })))
                .then(ClientCommandManager.literal("config")
                        .executes(ctx -> {
                            var c = StyleConfig.get();
                            ctx.getSource().sendFeedback(Component.literal(
                                    "HUD " + c.hudOffsetX + "," + c.hudOffsetY +
                                    " | show rank/score/mult/bar/bonuses/jr/kill=" +
                                    c.showRank + "/" + c.showScore + "/" + c.showMultiplier + "/" +
                                    c.showComboBar + "/" + c.showBonuses + "/" + c.showJumpReset + "/" +
                                    c.showKillBanner + " | lunge=" + c.showLungeCounter +
                                    " | reset gm/kill/death=" +
                                    c.resetOnGameModeChange + "/" + c.resetOnKill + "/" + c.resetOnDeath +
                                    " | jumpReset=" + c.jumpResetEnabled
                            ));
                            return 1;
                        })
                        .then(ClientCommandManager.literal("hudx")
                                .then(ClientCommandManager.argument("px", IntegerArgumentType.integer(0, 2000))
                                        .executes(ctx -> {
                                            int px = IntegerArgumentType.getInteger(ctx, "px");
                                            StyleConfig.get().setHudOffsetX(px);
                                            ctx.getSource().sendFeedback(Component.literal("offsetX=" + px));
                                            return 1;
                                        })))
                        .then(ClientCommandManager.literal("hudy")
                                .then(ClientCommandManager.argument("px", IntegerArgumentType.integer(0, 2000))
                                        .executes(ctx -> {
                                            int px = IntegerArgumentType.getInteger(ctx, "px");
                                            StyleConfig.get().setHudOffsetY(px);
                                            ctx.getSource().sendFeedback(Component.literal("offsetY=" + px));
                                            return 1;
                                        })))
                        .then(ClientCommandManager.literal("show")
                                .then(ClientCommandManager.argument("part", StringArgumentType.word())
                                        .then(ClientCommandManager.argument("value", BoolArgumentType.bool())
                                                .executes(ctx -> {
                                                    String part = StringArgumentType.getString(ctx, "part");
                                                    boolean v = BoolArgumentType.getBool(ctx, "value");
                                                    StyleConfig.get().setShow(part, v);
                                                    ctx.getSource().sendFeedback(Component.literal("show " + part + " = " + v));
                                                    return 1;
                                                }))))
                        .then(ClientCommandManager.literal("jump_reset")
                                .then(ClientCommandManager.argument("value", BoolArgumentType.bool())
                                        .executes(ctx -> {
                                            boolean v = BoolArgumentType.getBool(ctx, "value");
                                            StyleConfig.get().setJumpResetEnabled(v);
                                            ctx.getSource().sendFeedback(Component.literal("jumpResetEnabled = " + v));
                                            return 1;
                                        })))
                        .then(ClientCommandManager.literal("reset_on_gamemode")
                                .then(ClientCommandManager.argument("value", BoolArgumentType.bool())
                                        .executes(ctx -> {
                                            boolean v = BoolArgumentType.getBool(ctx, "value");
                                            StyleConfig.get().setResetOnGameModeChange(v);
                                            ctx.getSource().sendFeedback(Component.literal("resetOnGameModeChange = " + v));
                                            return 1;
                                        })))
                        .then(ClientCommandManager.literal("reset_on_kill")
                                .then(ClientCommandManager.argument("value", BoolArgumentType.bool())
                                        .executes(ctx -> {
                                            boolean v = BoolArgumentType.getBool(ctx, "value");
                                            StyleConfig.get().setResetOnKill(v);
                                            ctx.getSource().sendFeedback(Component.literal("resetOnKill = " + v));
                                            return 1;
                                        })))
                        .then(ClientCommandManager.literal("reset_on_death")
                                .then(ClientCommandManager.argument("value", BoolArgumentType.bool())
                                        .executes(ctx -> {
                                            boolean v = BoolArgumentType.getBool(ctx, "value");
                                            StyleConfig.get().setResetOnDeath(v);
                                            ctx.getSource().sendFeedback(Component.literal("resetOnDeath = " + v));
                                            return 1;
                                        })))
                        .then(ClientCommandManager.literal("reload")
                                .executes(ctx -> {
                                    StyleConfig.load();
                                    ctx.getSource().sendFeedback(Component.literal("Config reloaded"));
                                    return 1;
                                })))
        );
    }
}
