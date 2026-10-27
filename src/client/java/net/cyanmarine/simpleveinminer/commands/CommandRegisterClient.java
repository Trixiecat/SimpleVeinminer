package net.cyanmarine.simpleveinminer.commands;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.cyanmarine.simpleveinminer.client.SimpleVeinminerClient;
import net.cyanmarine.simpleveinminer.commands.argumenttypes.HighlightModesArgumentType;
import net.cyanmarine.simpleveinminer.config.SimpleConfigClient;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.network.chat.Component;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;

public class CommandRegisterClient {
    private int setVerticalAnchor(CommandContext<FabricClientCommandSource> context, SimpleConfigClient.HudDisplay.VERTICAL_ANCHOR anchor) {
        SimpleVeinminerClient.getConfig().setHudVerticalAnchor(anchor);
        context.getSource().sendFeedback(Component.literal("Hud vertical anchor set to " + anchor.name().toLowerCase()));
        return 1;
    }
    private int setHorizontalAnchor(CommandContext<FabricClientCommandSource> context, SimpleConfigClient.HudDisplay.HORIZONTAL_ANCHOR anchor) {
        SimpleVeinminerClient.getConfig().setHudHorizontalAnchor(anchor);
        context.getSource().sendFeedback(Component.literal("Hud horizontal anchor set to " + anchor.name().toLowerCase()));
        return 1;
    }

    public CommandRegisterClient() {
        SimpleVeinminerClient.LOGGER.info("Initializing client commands");
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(literal("veinminingclient").then(
                            literal("config").executes(context -> {
                                SimpleVeinminerClient.openSettingsNextTick = true;
                                return 1;
                            })
                    ).then(
                            literal("hud").then(
                                    literal("position").then(
                                            literal("x").then(
                                                    argument("value", IntegerArgumentType.integer())
                                                            .executes((context) -> {
                                                                int x = IntegerArgumentType.getInteger(context, "value");
                                                                SimpleVeinminerClient.getConfig().setHudX(x);
                                                                context.getSource().sendFeedback(Component.literal("Hud x position set to " + x));
                                                                return 1;
                                                            })
                                            )
                                    ).then(
                                            literal("y").then(
                                                    argument("value", IntegerArgumentType.integer())
                                                            .executes((context) -> {
                                                                int y = IntegerArgumentType.getInteger(context, "value");
                                                                SimpleVeinminerClient.getConfig().setHudY(y);
                                                                context.getSource().sendFeedback(Component.literal("Hud y position set to " + y));
                                                                return 1;
                                                            })
                                            )
                                    ).executes((context) -> {
                                        int x = SimpleVeinminerClient.getConfig().hudDisplay.x;
                                        int y = SimpleVeinminerClient.getConfig().hudDisplay.y;
                                        context.getSource().sendFeedback(Component.literal("Hud position is " + x + ", " + y));
                                        return 1;
                                    })
                            ).then(
                                    literal("anchor").then(
                                            literal("vertical").then(
                                                    literal("top").executes((context) -> setVerticalAnchor(context, SimpleConfigClient.HudDisplay.VERTICAL_ANCHOR.TOP))
                                            ).then(
                                                    literal("center").executes((context) -> setVerticalAnchor(context, SimpleConfigClient.HudDisplay.VERTICAL_ANCHOR.CENTER))
                                            ).then(
                                                    literal("bottom").executes((context) -> setVerticalAnchor(context, SimpleConfigClient.HudDisplay.VERTICAL_ANCHOR.BOTTOM))
                                            )
                                    ).then(
                                            literal("horizontal").then(
                                                    literal("left").executes((context) -> setHorizontalAnchor(context, SimpleConfigClient.HudDisplay.HORIZONTAL_ANCHOR.LEFT))
                                            ).then(
                                                    literal("center").executes((context) -> setHorizontalAnchor(context, SimpleConfigClient.HudDisplay.HORIZONTAL_ANCHOR.CENTER))
                                            ).then(
                                                    literal("right").executes((context) -> setHorizontalAnchor(context, SimpleConfigClient.HudDisplay.HORIZONTAL_ANCHOR.RIGHT))
                                            )
                                    )
                            ).then(
                                    literal("show").then(
                                            literal("block").then(
                                                    argument("value", BoolArgumentType.bool()).executes((context) -> {
                                                        boolean showBlock = BoolArgumentType.getBool(context, "value");
                                                        SimpleVeinminerClient.getConfig().setHudShowBlock(showBlock);
                                                        context.getSource().sendFeedback(Component.literal("Hud \"showBlock\" set to " + (showBlock ? "true" : "false")));
                                                        return 1;
                                                    })
                                            ).executes(context -> {
                                                boolean showBlock = SimpleVeinminerClient.getConfig().hudDisplay.showBlock;
                                                context.getSource().sendFeedback(Component.literal((showBlock ? "Showing block" : "Not showing block")));
                                                return 1;
                                            })
                                    ).then(
                                            literal("count").then(
                                                    argument("value", BoolArgumentType.bool()).executes((context) -> {
                                                        boolean showCount = BoolArgumentType.getBool(context, "value");
                                                        SimpleVeinminerClient.getConfig().setHudShowCount(showCount);
                                                        context.getSource().sendFeedback(Component.literal("Hud \"showCount\" set to " + (showCount ? "true" : "false")));
                                                        return 1;
                                                    })
                                            ).executes(context -> {
                                                boolean showCount = SimpleVeinminerClient.getConfig().hudDisplay.showCount;
                                                context.getSource().sendFeedback(Component.literal((showCount ? "Showing count" : "Not showing count")));
                                                return 1;
                                            })
                                    )
                            ).then(
                                    literal("spacing").then(
                                            argument("value", IntegerArgumentType.integer())
                                                    .executes((context) -> {
                                                        int spacing = IntegerArgumentType.getInteger(context, "value");
                                                        SimpleVeinminerClient.getConfig().setHudBlockNumberSpacing(spacing);
                                                        context.getSource().sendFeedback(Component.literal("Hud block number spacing set to " + spacing));
                                                        return 1;
                                                    })
                                    ).executes(context -> {
                                        int spacing = SimpleVeinminerClient.getConfig().hudDisplay.blockNumberSpacing;
                                        context.getSource().sendFeedback(Component.literal("Hud block number spacing is " + spacing));
                                        return 1;
                                    })
                            )
                    )
                    .then(
                            literal("highlight")
                                    .then(
                                            literal("updateRate").then(
                                                    argument("value", IntegerArgumentType.integer(0))
                                                            .executes((context) -> {
                                                                int updateRate = IntegerArgumentType.getInteger(context, "value");
                                                                SimpleVeinminerClient.getConfig().setHighlightUpdateRate(updateRate);
                                                                context.getSource().sendFeedback(Component.literal("Highlight update rate set to " + updateRate));
                                                                return 1;
                                                            })
                                            ).executes((context) -> {
                                                int updateRate = SimpleVeinminerClient.getConfig().highlight.updateRate;
                                                context.getSource().sendFeedback(Component.literal("Highlight update rate is " + updateRate));
                                                return 1;
                                            })
                                    ).then(
                                            literal("opacity")
                                                    .then(
                                                            argument("value", IntegerArgumentType.integer(0, 100))
                                                                    .executes((context) -> {
                                                                        int opacity = IntegerArgumentType.getInteger(context, "value");
                                                                        SimpleVeinminerClient.getConfig().setOpacity(opacity);
                                                                        context.getSource().sendFeedback(Component.literal("Highlight opacity set to " + opacity));
                                                                        return 1;
                                                                    })
                                                    ).executes((context) -> {
                                                        int opacity = SimpleVeinminerClient.getConfig().highlight.opacity;
                                                        context.getSource().sendFeedback(Component.literal("Highlight opacity is " + opacity));
                                                        return 1;
                                                    })
                                    ).then(
                                            literal("mode")
                                                    .then(
                                                            argument("value", HighlightModesArgumentType.highlightModes())
                                                                    .executes((context) -> {
                                                                        SimpleConfigClient.Highlight.MODES mode = HighlightModesArgumentType.getHighlightMode(context, "value");
                                                                        SimpleVeinminerClient.getConfig().setMode(mode);
                                                                        context.getSource().sendFeedback(Component.literal("Highlight mode set to " + mode.name().toUpperCase()));
                                                                        return 1;
                                                                    })
                                                    ).executes((context) -> {
                                                        SimpleConfigClient.Highlight.MODES mode = SimpleVeinminerClient.getConfig().highlight.mode;
                                                        context.getSource().sendFeedback(Component.literal("Highlight mode is " + mode.name()));
                                                        return 1;
                                                    })
                                    ).then(
                                            literal("highlightAllSides").then(
                                                    argument("value", BoolArgumentType.bool()).executes((context) -> {
                                                        boolean highlightAllSides = BoolArgumentType.getBool(context, "value");
                                                        SimpleVeinminerClient.getConfig().setHighlightAllSides(highlightAllSides);
                                                        context.getSource().sendFeedback(Component.literal("Highlight \"highlightAllSides\" set to " + (highlightAllSides ? "true" : "false")));
                                                        return 1;
                                                    })
                                            ).executes((context)->{
                                                boolean highlightAllSides = SimpleVeinminerClient.getConfig().highlight.highlightAllSides;
                                                context.getSource().sendFeedback(Component.literal((highlightAllSides ? "Highlighting all sides" : "Not highlighting connected sides")));
                                                return 1;
                                            })
                                    )/*.then(
                                            literal("onlyExposed").then(
                                                    argument("value", BoolArgumentType.bool()).executes((context) -> {
                                                        boolean onlyExposed = BoolArgumentType.getBool(context, "value");
                                                        SimpleVeinminerClient.getConfig().setOnlyExposed(onlyExposed);
                                                        context.getSource().sendFeedback(Component.literal("Highlight \"onlyExposed\" set to " + (onlyExposed ? "true" : "false")));
                                                        return 1;
                                                    })
                                            ).executes((context) -> {
                                                boolean onlyExposed = SimpleVeinminerClient.getConfig().highlight.onlyExposed;
                                                context.getSource().sendFeedback(Component.literal((onlyExposed ? "Highlighting only exposed blocks" : "Highlighting all blocks")));
                                                return 1;
                                            })
                                    )*/.then(
                                            literal("color").then(
                                                    argument("r", IntegerArgumentType.integer(0, 255)).then(
                                                            argument("g", IntegerArgumentType.integer(0, 255)).then(
                                                                    argument("b", IntegerArgumentType.integer(0, 255)).executes((context) -> {
                                                                        int r = IntegerArgumentType.getInteger(context, "r");
                                                                        int g = IntegerArgumentType.getInteger(context, "g");
                                                                        int b = IntegerArgumentType.getInteger(context, "b");
                                                                        SimpleVeinminerClient.getConfig().setColor((r << 16) | (g << 8) | b);
                                                                        context.getSource().sendFeedback(Component.literal("Highlight color set to " + r + ", " + g + ", " + b));
                                                                        return 1;
                                                                    })
                                                            )
                                                    )
                                            ).executes(context->{
                                                int color = SimpleVeinminerClient.getConfig().highlight.color;
                                                context.getSource().sendFeedback(Component.literal("Highlight color is " + ((color >> 16) & 0xFF) + ", " + ((color >> 8) & 0xFF) + ", " + (color & 0xFF)));
                                                return 1;
                                            })
                                    ).then(
                                            literal("doHighlight").then(
                                                    argument("value", BoolArgumentType.bool()).executes((context) -> {
                                                        boolean outlineBlocks = BoolArgumentType.getBool(context, "value");
                                                        SimpleVeinminerClient.getConfig().setDoHighlight(outlineBlocks);
                                                        context.getSource().sendFeedback(Component.literal("Veinmining \"doHighlight\" set to " + (outlineBlocks ? "true" : "false")));
                                                        return 1;
                                                    })
                                            ).executes(context-> {
                                                boolean doHighlight = SimpleVeinminerClient.getConfig().highlight.doHighlight;
                                                context.getSource().sendFeedback(Component.literal((doHighlight ? "Highlighting blocks" : "Not highlighting blocks")));
                                                return 1;
                                            })
                                    )
                    ).then(
                            literal("keybindToggles").then(
                                    argument("value", BoolArgumentType.bool()).executes((context) -> {
                                        boolean keybindToggles = BoolArgumentType.getBool(context, "value");
                                        SimpleVeinminerClient.getConfig().setKeybindToggles(keybindToggles);
                                        context.getSource().sendFeedback(Component.literal("Veinmining \"keybindToggles\" set to " + (keybindToggles ? "true" : "false")));
                                        return 1;
                                    })
                            ).executes(context-> {
                                boolean keybindToggles = SimpleVeinminerClient.getConfig().keybindToggles;
                                context.getSource().sendFeedback(Component.literal((keybindToggles ? "Keybind on toggle mode" : "Keybind on hold mode")));
                                return 1;
                            })
                    ).then(
                            literal("showMiningProgress").then(
                                    argument("value", BoolArgumentType.bool()).executes((context) -> {
                                        boolean showMiningProgress = BoolArgumentType.getBool(context, "value");
                                        SimpleVeinminerClient.getConfig().setShowMiningProgress(showMiningProgress);
                                        context.getSource().sendFeedback(Component.literal("Veinmining \"showMiningProgress\" set to " + (showMiningProgress ? "true" : "false")));
                                        return 1;
                                    })
                            ).executes(context -> {
                                boolean showMiningProgress = SimpleVeinminerClient.getConfig().showMiningProgress;
                                context.getSource().sendFeedback(Component.literal((showMiningProgress ? "Showing mining progress" : "Not showing mining progress")));
                                return 1;
                            })
                    ).then(
                            literal("showRestrictionMessages").then(
                                    argument("value", BoolArgumentType.bool()).executes((context) -> {
                                        boolean showRestrictionMessages = BoolArgumentType.getBool(context, "value");
                                        SimpleVeinminerClient.getConfig().setShowRestrictionMessages(showRestrictionMessages);
                                        context.getSource().sendFeedback(Component.literal("Veinmining \"showRestrictionMessages\" set to " + (showRestrictionMessages ? "true" : "false")));
                                        return 1;
                                    })
                            ).executes(context -> {
                                boolean showRestrictionMessages = SimpleVeinminerClient.getConfig().showRestrictionMessages;
                                context.getSource().sendFeedback(Component.literal((showRestrictionMessages ? "Showing restriction messages" : "Not showing restriction messages")));
                                return 1;
                            })
                    ).then(
                            literal("reset").executes(context-> {
                                SimpleVeinminerClient.getConfig().resetClient();
                                context.getSource().sendFeedback(Component.literal("Resetting client config to default values"));
                                return 1;
                            })
                    )
            );
        });
    }
}
