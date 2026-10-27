package net.cyanmarine.simpleveinminer.client;

import net.cyanmarine.simpleveinminer.SimpleVeinminer;
import net.cyanmarine.simpleveinminer.config.SimpleConfig;
import net.cyanmarine.simpleveinminer.config.SimpleConfigClient;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.state.level.BlockBreakingRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.gizmos.RectGizmo;
import net.minecraft.server.level.BlockDestructionProgress;
import net.minecraft.util.ARGB;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;

/**
 * Client side preview of the blocks that will be veinmined: highlight, mining progress on every block and the HUD counter.
 * Replaces the old WorldRendererMixin / InGameHudMixin, which targeted rendering code that no longer exists.
 */
@Environment(EnvType.CLIENT)
public final class VeinminePreview {
    public static boolean previewFrozen = false;
    public static BlockState currentlyOutliningState;
    public static ArrayList<BlockPos> blocksToHighlight;
    private static Set<BlockPos> blocksToHighlightSet = new HashSet<>();

    private static BlockPos currentlyOutliningPos;
    private static Item holding;
    private static long lastUpdateTick = Long.MIN_VALUE;
    /** True while we are drawing our own highlight this frame */
    private static boolean drawingHighlight = false;

    private VeinminePreview() {
    }

    public static void register() {
        LevelExtractionEvents.AFTER_BLOCK_OUTLINE_EXTRACTION.register((context, hitResult) -> {
            try {
                update(context, hitResult);
            } catch (Exception e) {
                SimpleVeinminerClient.LOGGER.error("Error while updating veinmining preview", e);
                reset();
            }
        });
        LevelExtractionEvents.END_EXTRACTION.register(VeinminePreview::extractMiningProgress);
        HudElementRegistry.addLast(SimpleVeinminer.getId("veinmine_count"), (graphics, deltaTracker) -> renderHud(graphics));
    }

    public static void reset() {
        currentlyOutliningPos = null;
        currentlyOutliningState = null;
        blocksToHighlight = null;
        blocksToHighlightSet = new HashSet<>();
        holding = null;
        lastUpdateTick = Long.MIN_VALUE;
        drawingHighlight = false;
    }

    private static boolean isVeinminingActive(LocalPlayer player) {
        return SimpleVeinminerClient.veinMineKeybind.isDown() || (SimpleVeinminerClient.isVeinMiningServerSide && player.isShiftKeyDown());
    }

    private static void update(LevelExtractionContext context, HitResult hitResult) {
        drawingHighlight = false;
        Minecraft client = Minecraft.getInstance();
        LocalPlayer player = client.player;
        ClientLevel level = context.level();
        SimpleConfigClient config = SimpleVeinminerClient.getConfig();
        if (player == null || level == null || config == null) return;

        if (!SimpleVeinminerClient.isInstalledOnServerSide.get()) {
            if (blocksToHighlight != null) reset();
            return;
        }

        SimpleConfigClient.Highlight highlight = config.highlight;
        SimpleConfig.SimpleConfigCopy worldConfig = SimpleVeinminerClient.getWorldConfig();

        if (!previewFrozen) {
            boolean wanted = (highlight.doHighlight || config.hudDisplay.showCount || config.hudDisplay.showBlock) && isVeinminingActive(player);
            if (wanted && hitResult instanceof BlockHitResult blockHit && hitResult.getType() == HitResult.Type.BLOCK) {
                BlockPos pos = blockHit.getBlockPos();
                BlockState state = level.getBlockState(pos);
                if (!state.isAir() && SimpleVeinminer.canVeinmine(player, level, pos, state, worldConfig.restrictions)) {
                    ItemStack handStack = player.getMainHandItem();
                    Item hand = handStack.getItem();
                    int updateRate = highlight.updateRate;
                    long gameTime = level.getGameTime();
                    boolean timeToUpdate = updateRate > 0 && gameTime - lastUpdateTick >= updateRate;
                    if (config.isChanged() || timeToUpdate || blocksToHighlight == null || !pos.equals(currentlyOutliningPos) || !state.equals(currentlyOutliningState) || !hand.equals(holding)) {
                        holding = hand;
                        currentlyOutliningPos = pos;
                        currentlyOutliningState = state;
                        lastUpdateTick = gameTime;
                        blocksToHighlight = SimpleVeinminer.getBlocksToVeinmine(pos, state,
                                SimpleVeinminer.getMaxBlocks(handStack, worldConfig.limits),
                                SimpleVeinminer.getVeinminingRadius(player, worldConfig.limits.radius),
                                config.limits.spreadAccuracy, player, false,
                                config.restrictions.onlyBreakBottomBlockForChainReactions,
                                worldConfig.restrictions);
                        blocksToHighlightSet = new HashSet<>(blocksToHighlight);
                    }
                } else if (currentlyOutliningPos != null) reset();
            } else if (currentlyOutliningPos != null) reset();
        }

        if (blocksToHighlight != null && highlight.doHighlight) {
            drawingHighlight = true;
            // Hide the vanilla outline, we draw our own
            context.levelState().blockOutlineRenderState = null;
            drawHighlight(level, player, config);
        }
    }

    private static void drawHighlight(ClientLevel level, LocalPlayer player, SimpleConfigClient config) {
        SimpleConfigClient.Highlight highlight = config.highlight;
        float alpha = Math.max(0.11f, highlight.opacity / 100.0f); // Prevents the highlight from being invisible
        int color = ARGB.color(Math.round(alpha * 255), highlight.color & 0xFFFFFF);
        boolean allSides = highlight.highlightAllSides;
        CollisionContext collisionContext = CollisionContext.of(player);

        for (BlockPos pos : blocksToHighlight) {
            VoxelShape shape = level.getBlockState(pos).getShape(level, pos, collisionContext);
            switch (highlight.mode) {
                case OUTLINE -> {
                    if (shape.isEmpty()) continue;
                    int lineColor = ARGB.color(Math.max(Math.round(alpha * 255), 102), highlight.color & 0xFFFFFF);
                    shape.forAllEdges((x1, y1, z1, x2, y2, z2) -> Gizmos.line(
                            new Vec3(pos.getX() + x1, pos.getY() + y1, pos.getZ() + z1),
                            new Vec3(pos.getX() + x2, pos.getY() + y2, pos.getZ() + z2),
                            lineColor));
                }
                case CUBE -> drawBox(new AABB(0, 0, 0, 1, 1, 1), pos, color, allSides);
                case CUBE_SHAPE -> {
                    if (!shape.isEmpty()) drawBox(shape.bounds(), pos, color, allSides);
                }
                case SHAPE -> shape.forAllBoxes((x1, y1, z1, x2, y2, z2) -> drawBox(new AABB(x1, y1, z1, x2, y2, z2), pos, color, allSides));
            }
        }
    }

    /**
     * Draws the faces of a box (relative to the block position), skipping faces that touch another highlighted block.
     */
    private static void drawBox(AABB box, BlockPos pos, int color, boolean allSides) {
        Vec3 min = new Vec3(pos.getX() + box.minX, pos.getY() + box.minY, pos.getZ() + box.minZ);
        Vec3 max = new Vec3(pos.getX() + box.maxX, pos.getY() + box.maxY, pos.getZ() + box.maxZ);
        GizmoStyle style = GizmoStyle.fill(color);
        for (Direction direction : Direction.values()) {
            if (!allSides) {
                boolean touchesEdge = switch (direction) {
                    case DOWN -> box.minY <= 0;
                    case UP -> box.maxY >= 1;
                    case NORTH -> box.minZ <= 0;
                    case SOUTH -> box.maxZ >= 1;
                    case WEST -> box.minX <= 0;
                    case EAST -> box.maxX >= 1;
                };
                if (touchesEdge && blocksToHighlightSet.contains(pos.relative(direction))) continue;
            }
            Gizmos.addGizmo(RectGizmo.fromCuboidFace(min, max, direction, style)).setAlwaysOnTop();
        }
    }

    /**
     * Copies the mining progress of the block being mined onto every block that will be veinmined.
     */
    private static void extractMiningProgress(LevelExtractionContext context) {
        SimpleConfigClient config = SimpleVeinminerClient.getConfig();
        if (config == null || !config.showMiningProgress || blocksToHighlight == null || currentlyOutliningPos == null || !SimpleVeinminerClient.veinMining)
            return;
        ClientLevel level = context.level();
        SortedSet<BlockDestructionProgress> progressSet = level.destructionProgress().get(currentlyOutliningPos.asLong());
        if (progressSet == null || progressSet.isEmpty()) return;
        int progress = progressSet.last().getProgress();

        List<BlockBreakingRenderState> states = context.levelState().blockBreakingRenderStates;
        for (BlockPos pos : blocksToHighlight) {
            if (pos.equals(currentlyOutliningPos)) continue;
            BlockState state = level.getBlockState(pos);
            if (state.isAir()) continue;
            states.add(new BlockBreakingRenderState(pos, state, progress));
        }
    }

    private static void renderHud(GuiGraphicsExtractor graphics) {
        if (blocksToHighlight == null || currentlyOutliningState == null) return;
        SimpleConfigClient config = SimpleVeinminerClient.getConfig();
        if (config == null) return;
        SimpleConfigClient.HudDisplay hudDisplay = config.hudDisplay;
        if (!hudDisplay.showCount && !hudDisplay.showBlock) return;
        Font font = Minecraft.getInstance().font;

        ItemStack stack = currentlyOutliningState.getBlock().asItem().getDefaultInstance();
        boolean showBlock = hudDisplay.showBlock && !stack.isEmpty();

        int count = blocksToHighlight.size();
        int width = graphics.guiWidth();
        int height = graphics.guiHeight();

        int x = 0, y = 0;
        switch (hudDisplay.horizontal_anchor) {
            case CENTER -> x = width / 2 - 8;
            case RIGHT ->
                    x = width - ((showBlock ? 16 : 0) + (hudDisplay.showCount ? font.width(String.valueOf(count)) + 6 : 0));
            default -> {
            }
        }
        switch (hudDisplay.vertical_anchor) {
            case CENTER -> y = height / 2 - 8;
            case BOTTOM -> y = height - 16;
            default -> {
            }
        }
        if (hudDisplay.horizontal_anchor == SimpleConfigClient.HudDisplay.HORIZONTAL_ANCHOR.RIGHT) x -= hudDisplay.x;
        else x += hudDisplay.x;
        if (hudDisplay.vertical_anchor == SimpleConfigClient.HudDisplay.VERTICAL_ANCHOR.BOTTOM) y -= hudDisplay.y;
        else y += hudDisplay.y;

        if (showBlock) {
            graphics.item(stack, x, y);
        }

        if (hudDisplay.showCount) {
            graphics.text(font, String.valueOf(count), x + 6 + (showBlock ? hudDisplay.blockNumberSpacing : 0), y + font.lineHeight / 2, 0xFFFFFFFF, true);
        }
    }
}
