package ru.stylemod.hud;

import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import ru.stylemod.combat.CombatStyleHook;
import ru.stylemod.combat.JumpResetTracker;
import ru.stylemod.config.StyleConfig;
import ru.stylemod.style.StyleBonus;
import ru.stylemod.style.StyleManager;
import ru.stylemod.style.StyleRank;

public final class StyleHud {
    private static float animatedComboFill;
    private static long lastBarRenderMs;

    private StyleHud() {}

    public static void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.options.hideGui) return;

        StyleManager style = StyleManager.get();
        StyleConfig cfg = StyleConfig.get();
        Font font = client.font;
        int screenWidth = client.getWindow().getGuiScaledWidth();
        int screenHeight = client.getWindow().getGuiScaledHeight();

        if (cfg.showKillBanner && style.isShowingKillScore()) {
            int pts = style.getLastKillStyle();
            String killText = pts > 0 ? ("+" + pts + " STYLE") : "KILL";
            int color = 0xFFFFD700;
            if (pts >= 2000) color = 0xFFFF55FF;
            else if (pts >= 1000) color = 0xFFFF5555;
            else if (pts >= 400) color = 0xFFFFAA00;

            Component killComp = Component.literal(killText).withStyle(ChatFormatting.BOLD);
            float scale = 2.0f;
            graphics.pose().pushMatrix();
            graphics.pose().scale(scale, scale);
            int tw = font.width(killComp);
            int x = (int) ((screenWidth / scale - tw) / 2);
            int y = (int) ((screenHeight / scale) / 2 - 10);
            graphics.drawString(font, killComp, x, y, color, true);
            graphics.pose().popMatrix();
        }

        int score = style.getScore();
        int combo = style.getCombo();
        int lungeStreak = CombatStyleHook.getLungeStreak();
        boolean hasStyle = score > 0 || combo > 0 || !style.getRecentBonuses().isEmpty();
        boolean hasVisibleStyleHud = hasStyle && (cfg.showRank || cfg.showScore || cfg.showMultiplier
                || cfg.showComboBar || (cfg.showBonuses && !style.getRecentBonuses().isEmpty()));
        boolean jrWait = cfg.showJumpReset && JumpResetTracker.isWaiting();
        // Show lunge counter even without other style so consecutive lunges stay visible.
        boolean showLunge = cfg.showLungeCounter && lungeStreak > 0;
        boolean showDamage = style.isShowingDamagePenalty() && style.getLastDamagePenalty() > 0;

        if (!hasStyle) {
            animatedComboFill = 0f;
            lastBarRenderMs = System.currentTimeMillis();
        }
        if (!hasVisibleStyleHud && !jrWait && !showLunge && !showDamage) return;

        StyleRank rank = style.getRank();
        int rankColor = rank.color | 0xFF000000;

        int right = screenWidth - cfg.hudOffsetX;
        int y = cfg.hudOffsetY;

        if (cfg.showRank && hasStyle) {
            Component rankComp = Component.literal(rank.displayName).withStyle(ChatFormatting.BOLD);
            graphics.drawString(font, rankComp, right - font.width(rankComp), y, rankColor, true);
            y += 12;
        }

        if (cfg.showScore && hasStyle) {
            Component scoreComp = Component.literal(String.valueOf(score)).withStyle(ChatFormatting.BOLD);
            graphics.drawString(font, scoreComp, right - font.width(scoreComp), y, 0xFFFFFFFF, true);
            y += 11;
        }

        if (cfg.showMultiplier && hasStyle) {
            String multStr = String.format("x%.2f (s%.2f)", style.getCoefficient(), style.getSpeedCoefficient());
            graphics.drawString(font, multStr, right - font.width(multStr), y, 0xFFCCCCCC, true);
            y += 12;
        }

        if (cfg.showComboBar && hasStyle) {
            int barW = 80;
            int barX = right - barW;
            long now = System.currentTimeMillis();
            float targetFill = Math.max(0f, Math.min(1f, combo / (float) StyleRank.cap()));
            float frameMs = lastBarRenderMs == 0 ? 100f : Math.min(100f, now - lastBarRenderMs);
            float smoothing = 1f - (float) Math.exp(-frameMs / 70f);
            animatedComboFill += (targetFill - animatedComboFill) * smoothing;
            lastBarRenderMs = now;

            long sinceGain = now - style.getLastPositiveStyleTimestamp();
            float pulse = sinceGain >= 0 && sinceGain < 380
                    ? 1f - sinceGain / 380f : 0f;
            long idleMs = Math.max(0L, sinceGain - 450L);
            float fade = Math.max(0.28f, 1f - idleMs / 1800f);
            long sinceDamage = now - style.getLastDamageTimestamp();
            float damagePulse = sinceDamage >= 0 && sinceDamage < 520
                    ? 1f - sinceDamage / 520f : 0f;
            int shake = damagePulse > 0f ? Math.round(2f * damagePulse * (float) Math.sin(now * 0.09)) : 0;
            int barY = y - (pulse > 0f ? 1 : 0);
            int fillW = Math.round(barW * animatedComboFill);
            int fadedRankColor = withAlpha(rankColor, fade);

            if (pulse > 0f) {
                int glowAlpha = Math.round(70f * pulse) << 24;
                int glowColor = glowAlpha | (rank.color & 0x00FFFFFF);
                graphics.fill(barX - 1, barY - 1, barX + barW + 1, barY + 6, glowColor);
            }
            if (damagePulse > 0f) {
                int damageAlpha = Math.round(125f * damagePulse) << 24;
                graphics.fill(barX - 2 + shake, barY - 2, barX + barW + 2 + shake, barY + 7,
                        damageAlpha | 0x00FF3030);
            }
            graphics.fill(barX + shake, barY, barX + barW + shake, barY + 4,
                    withAlpha(0x88000000, fade));
            if (fillW > 0) {
                graphics.fill(barX + shake, barY, barX + fillW + shake, barY + 4,
                        damagePulse > 0f
                                ? blendRgb(fadedRankColor, 0xFFFF3333, damagePulse * 0.8f)
                                : fadedRankColor);
                if (pulse > 0f) {
                    int edgeAlpha = Math.round(180f * pulse) << 24;
                    int edgeColor = edgeAlpha | (rank.color & 0x00FFFFFF);
                    graphics.fill(barX + fillW - 1 + shake, barY - 1,
                            barX + fillW + 1 + shake, barY + 5, edgeColor);
                }
            }
            y += 8;
        }

        if (showLunge) {
            String lungeCount = "×" + lungeStreak;
            int countX = right - font.width(lungeCount);
            drawSpearIcon(graphics, countX - 11, y);
            graphics.drawString(font, lungeCount, countX, y, 0xFFFFC15A, true);
            y += 11;
        }

        if (jrWait) {
            y = renderJumpReset(graphics, font, right, y);
        }

        // Dedicated damage line — does not occupy the event feed.
        if (showDamage) {
            long age = System.currentTimeMillis() - style.getLastDamageTimestamp();
            float alpha = 1f - (age / 1800f);
            if (alpha < 0.2f) alpha = 0.2f;
            int a = ((int) (alpha * 255)) << 24;
            int color = 0x00FF5555 | a;
            String dmgText = "-" + style.getLastDamagePenalty() + " DAMAGED";
            Component dmgComp = Component.literal(dmgText).withStyle(ChatFormatting.BOLD);
            graphics.drawString(font, dmgComp, right - font.width(dmgComp), y, color, true);
            y += 11;
        }

        if (cfg.showBonuses) {
            for (StyleBonus bonus : style.getRecentBonuses()) {
                // Skip any leftover DAMAGED entries from older sessions.
                if ("DAMAGED".equals(bonus.name)) continue;
                long age = System.currentTimeMillis() - bonus.timestamp;
                float alpha = 1f - (age / 2500f);
                if (alpha < 0.15f) alpha = 0.15f;
                int a = ((int) (alpha * 255)) << 24;
                int rgb = bonusColor(bonus.name) & 0x00FFFFFF;
                int color = rgb | a;

                String pts = (bonus.scorePoints >= 0 ? "+" : "") + bonus.scorePoints + " ";
                MutableComponent line = Component.literal(pts)
                        .append(Component.literal(bonus.name).withStyle(ChatFormatting.BOLD));
                graphics.drawString(font, line, right - font.width(line), y, color, true);
                y += 10;
            }
        }
    }

    private static void drawSpearIcon(GuiGraphics graphics, int x, int y) {
        // Tiny diagonal pixel spear: bronze shaft, bright tip.
        int shaft = 0xFFB9824B;
        int edge = 0xFF70472F;
        int tip = 0xFFFFD27A;
        graphics.fill(x, y + 7, x + 2, y + 9, edge);
        graphics.fill(x + 2, y + 5, x + 4, y + 7, shaft);
        graphics.fill(x + 4, y + 3, x + 6, y + 5, shaft);
        graphics.fill(x + 6, y + 1, x + 8, y + 3, tip);
        graphics.fill(x + 7, y, x + 9, y + 2, tip);
    }

    private static int renderJumpReset(GuiGraphics graphics, Font font, int right, int y) {
        int barW = 90;

        if (JumpResetTracker.isWaiting()) {
            float progress = JumpResetTracker.getWaitProgress();
            float[] z = JumpResetTracker.getZoneFractions();
            int barX = right - barW;
            int barY = y;

            graphics.fill(barX, barY, barX + barW, barY + 4, 0xAA000000);

            int x0 = barX;
            int x1 = barX + (int) (barW * z[0]);
            int x2 = barX + (int) (barW * z[1]);
            int x3 = barX + (int) (barW * z[2]);

            if (x1 > x0) graphics.fill(x0, barY, x1, barY + 4, 0xCC55FF55);
            if (x2 > x1) graphics.fill(x1, barY, x2, barY + 4, 0xCCFFD700);
            if (x3 > x2) graphics.fill(x2, barY, x3, barY + 4, 0xCCFFAA00);

            int cursor = barX + (int) (barW * progress);
            if (cursor < barX) cursor = barX;
            if (cursor > barX + barW) cursor = barX + barW;
            graphics.fill(cursor - 1, barY - 2, cursor + 1, barY + 6, 0xFFFFFFFF);

            y += 8;
            String hint = "JUMP RESET";
            graphics.drawString(font, hint, right - font.width(hint), y, 0xFFFFFFFF, true);
            y += 11;
        }

        return y;
    }

    private static int bonusColor(String name) {
        if (name == null) return 0xFFFFFF;
        return switch (name) {
            case "KILL" -> 0xFFD700;
            case "DAMAGED" -> 0xFF55FF;
            case "DEBUG" -> 0xFFFF55;
            case "CLOSE CALL" -> 0xFF5555;
            default -> name.startsWith("HIT") ? 0xC0C0C0
                    : name.startsWith("CRITICAL") ? 0xFF5555
                    : name.startsWith("SHIELD BREAK") ? 0x55FFFF
                    : name.startsWith("DISRESPECT") ? 0xFF55FF
                    : name.startsWith("RANGED") ? 0x5555FF
                    : name.startsWith("SPRINT HIT") ? 0x55FF55
                    : name.startsWith("MACE HIT") ? 0xFFAA00
                    : 0xFFFFFF;
        };
    }

    private static int withAlpha(int color, float multiplier) {
        int alpha = (color >>> 24) & 0xFF;
        return (Math.round(alpha * multiplier) << 24) | (color & 0x00FFFFFF);
    }

    private static int blendRgb(int from, int to, float amount) {
        float t = Math.max(0f, Math.min(1f, amount));
        int r = Math.round(((from >>> 16) & 0xFF) * (1f - t) + ((to >>> 16) & 0xFF) * t);
        int g = Math.round(((from >>> 8) & 0xFF) * (1f - t) + ((to >>> 8) & 0xFF) * t);
        int b = Math.round((from & 0xFF) * (1f - t) + (to & 0xFF) * t);
        return (from & 0xFF000000) | (r << 16) | (g << 8) | b;
    }
}
