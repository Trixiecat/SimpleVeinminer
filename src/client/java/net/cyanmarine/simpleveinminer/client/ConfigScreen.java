package net.cyanmarine.simpleveinminer.client;

import net.cyanmarine.simpleveinminer.Constants;
import net.cyanmarine.simpleveinminer.SimpleVeinminer;
import net.cyanmarine.simpleveinminer.config.SimpleConfig;
import net.cyanmarine.simpleveinminer.config.SimpleConfigClient;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsSubScreen;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.IntConsumer;

/**
 * Settings screen built from vanilla widgets (replaces the old CompleteConfig / Cloth Config screen).
 * Opened from Mod Menu, the "Open settings" key, or /veinminingclient config.
 */
@Environment(EnvType.CLIENT)
public class ConfigScreen extends OptionsSubScreen {
    private static final String KEY = "config.simpleveinminer.";
    private static final int WIDGET_WIDTH = 150;
    private static final int WIDGET_HEIGHT = 20;

    private final SimpleConfigClient config;
    private final int initialClientRadius;

    public ConfigScreen(Screen parent) {
        super(parent, Minecraft.getInstance().options, Component.translatable("config.simpleveinminer"));
        this.config = SimpleVeinminerClient.getConfig();
        this.initialClientRadius = config.clientRadius;
    }

    // ---------- widget helpers ----------

    private static Component label(String key) {
        return Component.translatable(KEY + key);
    }

    private static Tooltip tooltip(String key) {
        String descriptionKey = KEY + key + ".description";
        return Tooltip.create(Component.translatableWithFallback(descriptionKey, Component.translatable(KEY + key).getString()));
    }

    private AbstractWidget toggle(String key, boolean value, Consumer<Boolean> setter) {
        CycleButton<Boolean> button = CycleButton.onOffBuilder(value)
                .create(0, 0, WIDGET_WIDTH, WIDGET_HEIGHT, label(key), (b, v) -> setter.accept(v));
        button.setTooltip(tooltip(key));
        return button;
    }

    private <E extends Enum<E>> AbstractWidget cycle(String key, E value, E[] values, Consumer<E> setter) {
        CycleButton<E> button = CycleButton.<E>builder(e -> Component.literal(prettyEnum(e)), value)
                .withValues(values)
                .create(0, 0, WIDGET_WIDTH, WIDGET_HEIGHT, label(key), (b, v) -> setter.accept(v));
        button.setTooltip(tooltip(key));
        return button;
    }

    private static String prettyEnum(Enum<?> e) {
        String s = e.name().replace('_', ' ').toLowerCase(Locale.ROOT);
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private AbstractWidget slider(String key, int min, int max, int value, String suffix, IntConsumer setter) {
        IntSlider slider = new IntSlider(label(key), min, max, value, suffix, setter);
        slider.setTooltip(tooltip(key));
        return slider;
    }

    private StringWidget textLabel(String key) {
        StringWidget widget = new StringWidget(0, 0, WIDGET_WIDTH, WIDGET_HEIGHT, label(key), this.font);
        widget.setTooltip(tooltip(key));
        return widget;
    }

    private EditBox textBox(String key, int width, String value, Consumer<String> onChange) {
        EditBox box = new EditBox(this.font, 0, 0, width, WIDGET_HEIGHT, label(key));
        box.setMaxLength(4096);
        box.setValue(value);
        box.setResponder(onChange);
        box.setTooltip(tooltip(key));
        return box;
    }

    private void intField(String key, int value, int min, int max, IntConsumer setter) {
        EditBox box = textBox(key, WIDGET_WIDTH, String.valueOf(value), s -> {
            try {
                setter.accept(Mth.clamp(Integer.parseInt(s.trim()), min, max));
            } catch (NumberFormatException ignored) {
                // keep the last valid value
            }
        });
        list.addSmall(textLabel(key), box);
    }

    private void doubleField(String key, double value, DoubleConsumer setter) {
        EditBox box = textBox(key, WIDGET_WIDTH, String.valueOf(value), s -> {
            try {
                setter.accept(Double.parseDouble(s.trim()));
            } catch (NumberFormatException ignored) {
            }
        });
        list.addSmall(textLabel(key), box);
    }

    private void listField(String key, List<String> value, Consumer<List<String>> setter) {
        list.addBig(textLabel(key));
        EditBox box = textBox(key, 310, String.join(", ", value), s -> {
            List<String> parsed = new ArrayList<>();
            for (String part : s.split(",")) {
                String trimmed = part.trim();
                if (!trimmed.isEmpty()) parsed.add(trimmed);
            }
            setter.accept(parsed);
        });
        list.addBig(box);
    }

    private void pair(AbstractWidget a, AbstractWidget b) {
        list.addSmall(a, b);
    }

    // ---------- contents ----------

    @Override
    protected void addOptions() {
        SimpleConfigClient.Highlight highlight = config.highlight;
        SimpleConfigClient.HudDisplay hud = config.hudDisplay;

        // Client settings
        list.addHeader(Component.translatable(KEY + "client"));
        pair(
                CycleButton.booleanBuilder(label("keybindToggles.value.true"), label("keybindToggles.value.false"), config.keybindToggles)
                        .create(0, 0, WIDGET_WIDTH, WIDGET_HEIGHT, label("keybindToggles"), (b, v) -> config.keybindToggles = v),
                slider("clientRadius", 1, 5, config.clientRadius, "", v -> config.clientRadius = v));
        pair(
                toggle("showMiningProgress", config.showMiningProgress, v -> config.showMiningProgress = v),
                toggle("showRestrictionMessages", config.showRestrictionMessages, v -> config.showRestrictionMessages = v));

        list.addHeader(label("highlight"));
        pair(
                toggle("highlight.doHighlight", highlight.doHighlight, v -> highlight.doHighlight = v),
                cycle("highlight.mode", highlight.mode, SimpleConfigClient.Highlight.MODES.values(), v -> highlight.mode = v));
        pair(
                slider("highlight.opacity", 0, 100, highlight.opacity, "%", v -> highlight.opacity = v),
                toggle("highlight.highlightAllSides", highlight.highlightAllSides, v -> highlight.highlightAllSides = v));
        pair(
                slider("highlight.updateRate", 0, 100, highlight.updateRate, "", v -> highlight.updateRate = v),
                textBox("highlight.color", WIDGET_WIDTH, String.format("#%06X", highlight.color & 0xFFFFFF), s -> {
                    String hex = s.trim().replace("#", "");
                    try {
                        if (hex.length() == 6) highlight.color = Integer.parseInt(hex, 16);
                    } catch (NumberFormatException ignored) {
                    }
                }));

        list.addHeader(label("hudDisplay"));
        pair(
                toggle("hudDisplay.showCount", hud.showCount, v -> hud.showCount = v),
                toggle("hudDisplay.showBlock", hud.showBlock, v -> hud.showBlock = v));
        pair(
                cycle("hudDisplay.horizontal_anchor", hud.horizontal_anchor, SimpleConfigClient.HudDisplay.HORIZONTAL_ANCHOR.values(), v -> hud.horizontal_anchor = v),
                cycle("hudDisplay.vertical_anchor", hud.vertical_anchor, SimpleConfigClient.HudDisplay.VERTICAL_ANCHOR.values(), v -> hud.vertical_anchor = v));
        intField("hudDisplay.x", hud.x, -10000, 10000, v -> hud.x = v);
        intField("hudDisplay.y", hud.y, -10000, 10000, v -> hud.y = v);
        intField("hudDisplay.blockNumberSpacing", hud.blockNumberSpacing, -1000, 1000, v -> hud.blockNumberSpacing = v);

        // Server / world settings
        list.addHeader(Component.translatable(KEY + "server"));
        list.addBig(new StringWidget(0, 0, 310, WIDGET_HEIGHT, Component.translatable(KEY + "server.note"), this.font));

        SimpleConfig.Limits limits = config.limits;
        list.addHeader(label("limits"));
        intField("limits.maxBlocks", limits.maxBlocks, 1, 100000, v -> limits.maxBlocks = v);
        intField("limits.radius", limits.radius, 1, 100, v -> limits.radius = v);
        pair(
                toggle("limits.materialBasedLimits", limits.materialBasedLimits, v -> limits.materialBasedLimits = v),
                cycle("limits.spreadAccuracy", limits.spreadAccuracy, SimpleConfig.Limits.SPREAD_ACCURACY.values(), v -> limits.spreadAccuracy = v));
        pair(
                toggle("placeInInventory", config.placeInInventory, v -> config.placeInInventory = v),
                toggle("debug", config.debug, v -> config.debug = v));

        SimpleConfig.Restrictions restrictions = config.restrictions;
        list.addHeader(label("restrictions"));
        pair(
                toggle("restrictions.canVeinmineHungry", restrictions.canVeinmineHungry, v -> restrictions.canVeinmineHungry = v),
                toggle("restrictions.canVeinmineWithEmptyHand", restrictions.canVeinmineWithEmptyHand, v -> restrictions.canVeinmineWithEmptyHand = v));
        pair(
                toggle("restrictions.creativeBypass", restrictions.creativeBypass, v -> restrictions.creativeBypass = v),
                toggle("restrictions.canOnlyUseSuitableTools", restrictions.canOnlyUseSuitableTools, v -> restrictions.canOnlyUseSuitableTools = v));
        pair(
                toggle("restrictions.keepToolFromBreaking", restrictions.keepToolFromBreaking, v -> restrictions.keepToolFromBreaking = v),
                toggle("restrictions.onlyBreakBottomBlockForChainReactions", restrictions.onlyBreakBottomBlockForChainReactions, v -> restrictions.onlyBreakBottomBlockForChainReactions = v));

        list.addHeader(label("restrictions.restrictionList"));
        list.addBig(cycle("restrictions.restrictionList.listType", restrictions.restrictionList.listType, SimpleConfig.Restrictions.RestrictionList.ListType.values(), v -> restrictions.restrictionList.listType = v));
        listField("restrictions.restrictionList.list", restrictions.restrictionList.list, v -> restrictions.restrictionList.list = v);

        list.addHeader(label("restrictions.restrictionTags"));
        list.addBig(toggle("restrictions.restrictionTags.enabled", restrictions.restrictionTags.enabled, v -> restrictions.restrictionTags.enabled = v));
        listField("restrictions.restrictionTags.tags", restrictions.restrictionTags.tags, v -> restrictions.restrictionTags.tags = v);

        SimpleConfig.Exhaustion exhaustion = config.exhaustion;
        list.addHeader(label("exhaustion"));
        pair(
                toggle("exhaustion.exhaust", exhaustion.exhaust, v -> exhaustion.exhaust = v),
                toggle("exhaustion.exhaustionBasedOnHardness", exhaustion.exhaustionBasedOnHardness, v -> exhaustion.exhaustionBasedOnHardness = v));
        doubleField("exhaustion.baseValue", exhaustion.baseValue, v -> exhaustion.baseValue = v);
        doubleField("exhaustion.hardnessWeight", exhaustion.hardnessWeight, v -> exhaustion.hardnessWeight = v);

        SimpleConfig.Durability durability = config.durability;
        list.addHeader(label("durability"));
        doubleField("durability.damageMultiplier", durability.damageMultiplier, v -> durability.damageMultiplier = v);
        doubleField("durability.swordMultiplier", durability.swordMultiplier, v -> durability.swordMultiplier = v);
        list.addBig(toggle("durability.consumeOnInstantBreak", durability.consumeOnInstantBreak, v -> durability.consumeOnInstantBreak = v));
    }

    @Override
    public void removed() {
        super.removed();
        config.clientRadius = Mth.clamp(config.clientRadius, 1, 5);
        config._save();
        SimpleVeinminer.setShowRestrictionMessages(config.showRestrictionMessages);

        if (config.clientRadius != initialClientRadius)
            SimpleVeinminerClient.send(new Constants.RadiusPayload(config.clientRadius));

        // In singleplayer (or a LAN world hosted here) the server uses this same config: push the changes to players
        IntegratedServer server = Minecraft.getInstance().getSingleplayerServer();
        if (server != null) server.execute(SimpleVeinminer::SyncConfigForAllPlayers);
    }

    /**
     * Simple integer slider.
     */
    private static class IntSlider extends AbstractSliderButton {
        private final Component label;
        private final int min;
        private final int max;
        private final String suffix;
        private final IntConsumer setter;

        IntSlider(Component label, int min, int max, int value, String suffix, IntConsumer setter) {
            super(0, 0, WIDGET_WIDTH, WIDGET_HEIGHT, Component.empty(), max == min ? 0 : (Mth.clamp(value, min, max) - min) / (double) (max - min));
            this.label = label;
            this.min = min;
            this.max = max;
            this.suffix = suffix;
            this.setter = setter;
            updateMessage();
        }

        private int intValue() {
            return min + (int) Math.round(this.value * (max - min));
        }

        @Override
        protected void updateMessage() {
            setMessage(Component.empty().append(label).append(": " + intValue() + suffix));
        }

        @Override
        protected void applyValue() {
            setter.accept(intValue());
        }
    }

}
