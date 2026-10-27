package net.cyanmarine.simpleveinminer;

import net.cyanmarine.simpleveinminer.commands.CommandRegister;
import net.cyanmarine.simpleveinminer.commands.argumenttypes.ArgumentTypes;
import net.cyanmarine.simpleveinminer.config.SimpleConfig;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.gamerule.v1.GameRuleBuilder;
import net.fabricmc.fabric.api.gamerule.v1.GameRuleEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShearsItem;
import net.minecraft.world.item.component.Tool;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gamerules.GameRule;
import net.minecraft.world.level.gamerules.GameRuleCategory;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.function.IntSupplier;
import java.util.stream.Stream;

public class SimpleVeinminer implements ModInitializer {
    public static final String MOD_ID = "simpleveinminer";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static final TagKey<Block> BREAK_BOTTOM_MOST = TagKey.create(Registries.BLOCK, getId("break_bottom_most"));
    public static final TagKey<Block> BREAK_TOP_MOST = TagKey.create(Registries.BLOCK, getId("break_top_most"));

    /** /gamerule simpleveinminer:do_server_side_veinmining true */
    public static GameRule<Boolean> SERVER_SIDE_VEINMINING;

    static MinecraftServer server;
    private static final Set<UUID> playersVeinMining = Collections.synchronizedSet(new HashSet<>());
    private static final Map<UUID, Integer> playersRadius = Collections.synchronizedMap(new HashMap<>());

    /** The config currently in use. Set by the client or dedicated server initializer. */
    private static SimpleConfig config;
    /** On the client this returns the client's chosen radius. Null on a dedicated server. */
    private static IntSupplier clientRadiusSupplier;
    /** On the client this returns whether restriction messages should be shown */
    private static boolean showRestrictionMessages = false;

    public static Identifier getId(String name) {
        return Identifier.fromNamespaceAndPath(MOD_ID, name);
    }

    public static void setConfig(SimpleConfig newConfig) {
        config = newConfig;
    }

    public static void setClientRadiusSupplier(IntSupplier supplier) {
        clientRadiusSupplier = supplier;
    }

    public static void setShowRestrictionMessages(boolean value) {
        showRestrictionMessages = value;
    }

    private static final BlockState[] STATES = {
            Blocks.STAINED_GLASS.pick(DyeColor.RED).defaultBlockState(),
            Blocks.STAINED_GLASS.pick(DyeColor.ORANGE).defaultBlockState(),
            Blocks.STAINED_GLASS.pick(DyeColor.YELLOW).defaultBlockState(),
            Blocks.STAINED_GLASS.pick(DyeColor.LIME).defaultBlockState(),
            Blocks.STAINED_GLASS.pick(DyeColor.GREEN).defaultBlockState(),
            Blocks.STAINED_GLASS.pick(DyeColor.CYAN).defaultBlockState(),
            Blocks.STAINED_GLASS.pick(DyeColor.LIGHT_BLUE).defaultBlockState(),
            Blocks.STAINED_GLASS.pick(DyeColor.BLUE).defaultBlockState(),
            Blocks.STAINED_GLASS.pick(DyeColor.PURPLE).defaultBlockState(),
            Blocks.STAINED_GLASS.pick(DyeColor.MAGENTA).defaultBlockState(),
            Blocks.STAINED_GLASS.pick(DyeColor.PINK).defaultBlockState(),
            Blocks.STAINED_GLASS.pick(DyeColor.BROWN).defaultBlockState(),
            Blocks.STAINED_GLASS.pick(DyeColor.BLACK).defaultBlockState(),
            Blocks.STAINED_GLASS.pick(DyeColor.GRAY).defaultBlockState(),
            Blocks.STAINED_GLASS.pick(DyeColor.LIGHT_GRAY).defaultBlockState(),
            Blocks.STAINED_GLASS.pick(DyeColor.WHITE).defaultBlockState()
    };

    private static ArrayList<BlockPos> testAroundBlock(@Nullable Set<BlockPos> blocksTested, List<BlockPos> blocksToBreak, Set<BlockPos> blocksToBreakSet, int maxBlocks, Level world, BlockState compareTo, int radius, BlockPos currentPos, int depth, boolean debug, @Nullable List<String> tagList, boolean chainReactions, @Nullable Player player, SimpleConfig.Restrictions restrictions) {
        if (blocksTested == null) blocksTested = new HashSet<>();
        ArrayList<BlockPos> blocksToCrawl = new ArrayList<>();
        Set<BlockPos> blocksToCrawlSet = new HashSet<>();
        int x, y, z;
        for (x = -1; x <= 1; x++)
            for (y = -1; y <= 1; y++)
                for (z = -1; z <= 1; z++) {
                    if (blocksToBreak.size() >= maxBlocks) return blocksToCrawl;
                    BlockPos testPos = currentPos.offset(x, y, z);
                    if (!blocksTested.add(testPos)) continue;

                    if (blocksToBreak.size() < maxBlocks && !blocksToBreakSet.contains(testPos)) {
                        BlockState testState = world.getBlockState(testPos);
                        if (isSameBlock(testState, compareTo, tagList, player, restrictions)) {
                            if ((testState.is(Blocks.TALL_GRASS) || testState.is(Blocks.TALL_SEAGRASS) || testState.is(Blocks.LARGE_FERN)) && (blocksToBreakSet.contains(testPos.above()) || blocksToBreakSet.contains(testPos.below())))
                                continue;
                            if (chainReactions) {
                                boolean skip = false;
                                if (testState.is(BREAK_BOTTOM_MOST)) {
                                    while (isSameBlock(world.getBlockState(testPos.below()), testState, null, player, restrictions)) {
                                        if (blocksToBreakSet.contains(testPos.below())) {
                                            skip = true;
                                            break;
                                        }
                                        testPos = testPos.below();
                                    }
                                    if (skip) continue;
                                }
                                if (testState.is(BREAK_TOP_MOST)) {
                                    while (isSameBlock(world.getBlockState(testPos.above()), testState, null, null, null)) {
                                        if (blocksToBreakSet.contains(testPos.above())) {
                                            skip = true;
                                            break;
                                        }
                                        testPos = testPos.above();
                                    }
                                    if (skip) continue;
                                }
                            }
                            if (blocksToBreakSet.add(testPos)) blocksToBreak.add(testPos);
                        } else {
                            if (debug) {
                                world.setBlockAndUpdate(testPos, STATES[Math.min(depth, STATES.length - 1)]);
                            }
                            if (blocksToCrawlSet.add(testPos)) {
                                blocksToCrawl.add(testPos);
                            }
                        }
                    }
                }
        if (radius > 1 && depth == 0) {
            ArrayList<ArrayList<ArrayList<BlockPos>>> crawlers = new ArrayList<>();
            crawlers.add(new ArrayList<>());
            crawlers.get(0).add(new ArrayList<>(blocksToCrawl));
            for (int d = 0; d < crawlers.size() && d < radius - 1; d++) {
                ArrayList<ArrayList<BlockPos>> _crawlers = crawlers.get(d);
                ArrayList<ArrayList<BlockPos>> next = new ArrayList<>();
                for (ArrayList<BlockPos> crawler : _crawlers) {
                    for (BlockPos pos : crawler) {
                        ArrayList<BlockPos> newCrawler = testAroundBlock(blocksTested, blocksToBreak, blocksToBreakSet, maxBlocks, world, compareTo, radius, pos, 1 + d, debug, tagList, chainReactions, player, restrictions);

                        if (d < radius - 2 && !newCrawler.isEmpty()) {
                            ArrayList<BlockPos> filteredCrawler = new ArrayList<>();
                            for (BlockPos newCrawlerPos : newCrawler) {
                                if (!blocksToCrawlSet.add(newCrawlerPos)) continue;
                                filteredCrawler.add(newCrawlerPos);
                                blocksToCrawl.add(newCrawlerPos);
                            }
                            if (!filteredCrawler.isEmpty()) next.add(filteredCrawler);
                        }
                    }
                }
                if (!next.isEmpty()) crawlers.add(next);
            }
        }
        return blocksToCrawl;
    }

    public static ArrayList<BlockPos> getBlocksToVeinmine(BlockPos pos, BlockState state, int maxBlocks, int radius, SimpleConfig.Limits.SPREAD_ACCURACY spreadAccuracy, Player player, boolean debug, boolean chainReactions) {
        return getBlocksToVeinmine(pos, state, maxBlocks, radius, spreadAccuracy, player, debug, chainReactions, getConfig().restrictions);
    }

    public static ArrayList<BlockPos> getBlocksToVeinmine(BlockPos pos, BlockState state, int maxBlocks, int radius, SimpleConfig.Limits.SPREAD_ACCURACY spreadAccuracy, Player player, boolean debug, boolean chainReactions, SimpleConfig.Restrictions restrictions) {
        Level world = player.level();

        ArrayList<BlockPos> blocksToBreak = new ArrayList<>();
        Set<BlockPos> blocksToBreakSet = new HashSet<>();
        Set<BlockPos> blocksTested = new HashSet<>();
        boolean isDebug = debug && !world.isClientSide();

        SimpleConfig.Restrictions.RestrictionTags rTags = getConfig().restrictions.restrictionTags;
        List<String> tagList = null;
        if (rTags.enabled) {
            Stream<String> tags = state.tags().filter((tag) -> {
                for (String tagString : rTags.tags) {
                    if (tagString.startsWith("#")) {
                        tagString = tagString.substring(1);
                        if (tag.location().toString().matches(tagString)) return true;
                    }
                }
                return false;
            }).map((tag) -> "#" + tag.location().toString());
            String id = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
            for (String tagString : rTags.tags) {
                if (!tagString.startsWith("#") && id.matches(tagString)) tags = Stream.concat(tags, Stream.of(tagString));
            }
            tagList = tags.toList();
        }

        blocksToBreak.add(pos);
        blocksToBreakSet.add(pos);
        for (int i = 0; i < maxBlocks && i < blocksToBreak.size(); i++) {
            BlockPos currentPos = blocksToBreak.get(i);
            if (blocksToBreak.size() >= maxBlocks) {
                return blocksToBreak;
            }
            testAroundBlock(spreadAccuracy == SimpleConfig.Limits.SPREAD_ACCURACY.ACCURATE ? null : blocksTested, blocksToBreak, blocksToBreakSet, maxBlocks, world, state, radius, currentPos, 0, isDebug, tagList, chainReactions, player, restrictions);
        }
        return blocksToBreak;
    }

    public static BlockHitResult getBlockHitResult(Player player) {
        Vec3 cameraPos = player.getEyePosition(1);
        Vec3 rotation = player.getViewVector(1);
        double reachDistance = player.blockInteractionRange();
        Vec3 combined = cameraPos.add(rotation.x * reachDistance, rotation.y * reachDistance, rotation.z * reachDistance);

        return player.level().clip(new ClipContext(cameraPos, combined, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
    }

    /**
     * Old "mining level" of a tool: 0 = wood/gold, 1 = stone, 2 = iron, 3 = diamond, 4 = netherite, -1 = not a tool.
     * Tool tiers no longer exist as a concept, so we work it out from what the tool can harvest.
     */
    public static int getMiningLevel(ItemStack stack) {
        if (stack.getItem() instanceof ShearsItem) return 2; // shears used to count as iron
        Tool tool = stack.get(DataComponents.TOOL);
        if (tool == null) return -1;
        if (tool.isCorrectForDrops(Blocks.OBSIDIAN.defaultBlockState()))
            return tool.defaultMiningSpeed() >= 9.0F || tool.getMiningSpeed(Blocks.OBSIDIAN.defaultBlockState()) >= 9.0F ? 4 : 3;
        if (tool.isCorrectForDrops(Blocks.DIAMOND_ORE.defaultBlockState())) return 2;
        if (tool.isCorrectForDrops(Blocks.IRON_ORE.defaultBlockState())) return 1;
        return 0;
    }

    public static boolean isTool(ItemStack stack) {
        return stack.getItem() instanceof ShearsItem || stack.has(DataComponents.TOOL) || stack.is(ItemTags.SWORDS);
    }

    public static int getMaxBlocks(ItemStack stack) {
        return getMaxBlocks(stack, getConfig().limits);
    }

    public static int getMaxBlocks(ItemStack stack, SimpleConfig.Limits limits) {
        int maxBlocks = limits.maxBlocks;
        if (limits.materialBasedLimits) {
            int limitingFactor = 6;
            int miningLevel = getMiningLevel(stack);
            if (miningLevel >= 0) limitingFactor -= miningLevel + 1;

            maxBlocks = maxBlocks / Math.max(1, limitingFactor);
        }

        return maxBlocks;
    }

    public static int getVeinminingRadius(Player player) {
        return getVeinminingRadius(player, getConfig().limits.radius);
    }

    public static int getVeinminingRadius(Player player, int serverRadius) {
        int playerRadius = (player.level().isClientSide() && clientRadiusSupplier != null) ? clientRadiusSupplier.getAsInt() : playersRadius.getOrDefault(player.getUUID(), serverRadius);
        if (playerRadius <= 0) playerRadius = 1;
        return Math.max(1, Math.min(playerRadius, serverRadius));
    }

    public static SimpleConfig.Limits.SPREAD_ACCURACY getSpreadAccuracy() {
        return getConfig().limits.spreadAccuracy;
    }

    public static boolean isDebug() {
        return getConfig().debug;
    }

    private static boolean isSameBlock(BlockState state, BlockState compareTo, @Nullable List<String> tagList, @Nullable Player player, SimpleConfig.@Nullable Restrictions restrictions) {
        if (state.getBlock().equals(compareTo.getBlock())) return true;
        if (state.isAir() || compareTo.isAir()) return false;
        if (player != null && restrictions != null && !canVeinmine(player, null, null, state, restrictions, false))
            return false;
        if (tagList != null) {
            String id = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
            for (String tag : tagList) {
                if (tag.startsWith("#")) {
                    Identifier identifier = Identifier.tryParse(tag.substring(1));
                    if (identifier != null && state.is(TagKey.create(Registries.BLOCK, identifier))) return true;
                } else if (id.matches(tag)) return true;
            }
        }
        return false;
    }

    private static boolean listIncludes(Block block, SimpleConfig.Restrictions restrictions) {
        for (String name : restrictions.restrictionList.list) {
            Identifier identifier = Identifier.tryParse(name.replaceAll("[^a-z0-9-_:/.]", ""));
            if (identifier == null) continue;
            if (name.startsWith("#")) {
                if (block.defaultBlockState().is(TagKey.create(Registries.BLOCK, identifier))) return true;
            } else {
                Block compareTo = BuiltInRegistries.BLOCK.getValue(identifier);
                if (compareTo.equals(block)) return true;
            }
        }
        return false;
    }

    public static boolean canVeinmine(Player player, @Nullable Level world, @Nullable BlockPos pos, BlockState state, SimpleConfig.Restrictions restrictions) {
        return canVeinmine(player, world, pos, state, restrictions, true);
    }

    public static boolean canVeinmine(Player player, @Nullable Level world, @Nullable BlockPos pos, BlockState state, SimpleConfig.Restrictions restrictions, boolean allowMessages) {
        if (restrictions.creativeBypass && player.isCreative()) return true;

        ItemStack hand = player.getMainHandItem();
        boolean sendMessage = allowMessages && player.level().isClientSide() && showRestrictionMessages;

        if (restrictions.restrictionList.listType != SimpleConfig.Restrictions.RestrictionList.ListType.NONE) {
            boolean isInList = listIncludes(state.getBlock(), restrictions);
            if ((restrictions.restrictionList.listType == SimpleConfig.Restrictions.RestrictionList.ListType.BLACKLIST) == isInList)
                return false;
        }

        if (!restrictions.canVeinmineWithEmptyHand && !isTool(hand)) {
            if (sendMessage) player.sendOverlayMessage(Component.translatable("messages.simpleveinminer.restriction.tool"));
            return false;
        }

        if (restrictions.canOnlyUseSuitableTools && !hand.isCorrectToolForDrops(state)) {
            if (sendMessage)
                player.sendOverlayMessage(Component.translatable("messages.simpleveinminer.restriction.specificTool"));
            return false;
        }

        if (!restrictions.canVeinmineHungry && player.getFoodData().getFoodLevel() < 1) {
            if (sendMessage) player.sendOverlayMessage(Component.translatable("messages.simpleveinminer.restriction.hungry"));
            return false;
        }

        return true;
    }

    public static void sendServerSideVeinminingUpdate(ServerPlayer player, boolean value) {
        if (ServerPlayNetworking.canSend(player, Constants.ServerSideUpdatePayload.TYPE))
            ServerPlayNetworking.send(player, new Constants.ServerSideUpdatePayload(value));
    }

    public static void syncConfig(ServerPlayer player) {
        if (ServerPlayNetworking.canSend(player, Constants.ConfigSyncPayload.TYPE))
            ServerPlayNetworking.send(player, new Constants.ConfigSyncPayload(SimpleConfig.SimpleConfigCopy.from(getConfig())));
    }

    public static void SyncConfigForAllPlayers() {
        if (server == null) return;
        for (ServerPlayer player : PlayerLookup.all(server)) {
            syncConfig(player);
        }
    }

    public static boolean isServerSideVeinmining(Level level) {
        if (level instanceof ServerLevel serverLevel) return serverLevel.getGameRules().get(SERVER_SIDE_VEINMINING);
        return false;
    }

    public static boolean isVeinmining(Player player) {
        return (player.isShiftKeyDown() && isServerSideVeinmining(player.level())) || playersVeinMining.contains(player.getUUID());
    }

    private static void setVeinmining(Player player, boolean isVeinMining) {
        if (isVeinMining)
            playersVeinMining.add(player.getUUID());
        else
            playersVeinMining.remove(player.getUUID());
    }

    private static void setPlayerVeinminingRadius(Player player, int radius) {
        playersRadius.put(player.getUUID(), radius);
    }

    public static SimpleConfig getConfig() {
        if (config == null) {
            // Fallback, should not normally happen (client and server initializers set this)
            config = SimpleConfig.load(SimpleConfig.class);
        }
        return config;
    }

    public static BlockPos[] getNeighbors(BlockPos pos) {
        return new BlockPos[]{
                pos.above(), pos.below(), pos.west(), pos.east(), pos.north(), pos.south()
        };
    }

    @Override
    public void onInitialize() {
        SERVER_SIDE_VEINMINING = GameRuleBuilder.forBoolean(false)
                .category(GameRuleCategory.MISC)
                .buildAndRegister(getId("do_server_side_veinmining"));

        GameRuleEvents.changeCallback(SERVER_SIDE_VEINMINING).register((newValue, server) -> {
            for (ServerPlayer player : PlayerLookup.all(server)) {
                if (newValue)
                    player.sendSystemMessage(Component.literal("Server side veinmining was turned on. Mining while shifting will veinmine"));
                else
                    player.sendSystemMessage(Component.literal("Server side veinmining was turned off"));

                sendServerSideVeinminingUpdate(player, newValue);
            }
        });

        PayloadTypeRegistry.serverboundPlay().register(Constants.VeinminePayload.TYPE, Constants.VeinminePayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(Constants.RadiusPayload.TYPE, Constants.RadiusPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(Constants.ServerSideUpdatePayload.TYPE, Constants.ServerSideUpdatePayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(Constants.ConfigSyncPayload.TYPE, Constants.ConfigSyncPayload.CODEC);

        ServerPlayNetworking.registerGlobalReceiver(Constants.VeinminePayload.TYPE, (payload, context) ->
                context.server().execute(() -> setVeinmining(context.player(), payload.veinmining())));
        ServerPlayNetworking.registerGlobalReceiver(Constants.RadiusPayload.TYPE, (payload, context) ->
                context.server().execute(() -> setPlayerVeinminingRadius(context.player(), payload.radius())));

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayer player = handler.getPlayer();
            boolean serverSideVeinmining = isServerSideVeinmining(player.level());

            sendServerSideVeinminingUpdate(player, serverSideVeinmining);

            if (serverSideVeinmining)
                player.sendSystemMessage(Component.literal("This server has server side veinmining turned on. Mining while shifting will veinmine"));

            syncConfig(player);
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server1) -> {
            setVeinmining(handler.getPlayer(), false);
            playersRadius.remove(handler.getPlayer().getUUID());
        });
        ServerLifecycleEvents.SERVER_STARTED.register(server -> SimpleVeinminer.server = server);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            SimpleVeinminer.server = null;
            playersVeinMining.clear();
            playersRadius.clear();
        });

        PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, blockEntity) -> {
            if (world instanceof ServerLevel serverLevel && player instanceof ServerPlayer serverPlayer && isVeinmining(player)) {
                if (canVeinmine(player, world, pos, state, getConfig().restrictions))
                    Veinminer.veinMine(serverLevel, pos, state, serverPlayer);
            }
            return true;
        });

        new ArgumentTypes();
        new CommandRegister();

        if (FabricLoader.getInstance().getEnvironmentType() == EnvType.SERVER) {
            LOGGER.info("Simple VeinMiner initialized (server)");
        } else {
            LOGGER.info("Simple VeinMiner initialized");
        }
    }
}
