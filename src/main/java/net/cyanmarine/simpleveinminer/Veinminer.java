package net.cyanmarine.simpleveinminer;

import net.cyanmarine.simpleveinminer.config.SimpleConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShearsItem;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/**
 * Server side veinmining logic (was BlockMixin before the port).
 * Called from the Fabric "before block break" event, so the original block is still there.
 * Like the original mod, this also breaks the origin block itself (so its drops follow the placeInInventory option);
 * vanilla then sees an air block and doesn't drop anything twice.
 */
public final class Veinminer {
    private Veinminer() {
    }

    public static void veinMine(ServerLevel world, BlockPos pos, BlockState state, ServerPlayer player) {
        ItemStack hand = player.getMainHandItem();
        boolean startedWithItem = !hand.isEmpty();

        SimpleConfig config = SimpleVeinminer.getConfig();

        SimpleConfig.Exhaustion exhaustion = config.exhaustion;
        SimpleConfig.Durability durability = config.durability;
        double damageMultiplier = durability.damageMultiplier;
        if (isSwordLike(hand)) damageMultiplier *= durability.swordMultiplier;
        boolean debug = SimpleVeinminer.isDebug();

        ArrayList<BlockPos> blocksToBreak = SimpleVeinminer.getBlocksToVeinmine(pos, state, SimpleVeinminer.getMaxBlocks(hand), SimpleVeinminer.getVeinminingRadius(player), SimpleVeinminer.getSpreadAccuracy(), player, debug, config.restrictions.onlyBreakBottomBlockForChainReactions);
        if (debug) world.setBlockAndUpdate(pos, Blocks.BEDROCK.defaultBlockState());
        for (int i = 0; i < blocksToBreak.size(); i++) {
            BlockPos currentPos = blocksToBreak.get(i);
            if (debug) {
                world.setBlockAndUpdate(currentPos, Blocks.BEDROCK.defaultBlockState());
                continue;
            }
            BlockState currentState = world.getBlockState(currentPos);
            if (currentState.isAir()) continue;
            Block currentBlock = currentState.getBlock();
            float hardness = currentState.getDestroySpeed(world, currentPos);
            if (hardness < 0) continue; // unbreakable (bedrock, etc.)
            BlockEntity currentBlockEntity = world.getBlockEntity(currentPos);

            boolean willDrop = shouldDrop(player, hand, currentState);

            List<ItemStack> stacks = Block.getDrops(currentState, world, currentPos, currentBlockEntity, player, hand);
            if (willDrop)
                stacks.forEach((stack) -> {
                    if (config.placeInInventory) {
                        if (!player.getInventory().add(stack)) Block.popResource(world, pos, stack);
                    } else Block.popResource(world, pos, stack);
                });
            currentState.spawnAfterBreak(world, currentPos, hand, willDrop);

            if (willDrop && stacks.isEmpty() && currentState.is(Blocks.ICE) && !world.getBlockState(currentPos.below()).isAir())
                world.setBlockAndUpdate(currentPos, Blocks.WATER.defaultBlockState());
            else world.removeBlock(currentPos, false);

            player.awardStat(Stats.BLOCK_MINED.get(currentBlock));

            // The first block is the one being mined normally, vanilla already damages the tool for it
            boolean doDamage = i > 0 && shouldDamage(player, hand, hardness, durability);

            if (doDamage)
                hand.hurtAndBreak((int) damageMultiplier, player, EquipmentSlot.MAINHAND);

            if (exhaustion.exhaust) {
                double totalExhausted = (exhaustion.baseValue + (exhaustion.exhaustionBasedOnHardness ? (hardness * exhaustion.hardnessWeight) : 0));
                player.causeFoodExhaustion((float) totalExhausted);
            }

            if (startedWithItem && hand.isEmpty()) break; // the tool broke
            if (doDamage && (hand.getDamageValue() >= hand.getMaxDamage() || (config.restrictions.keepToolFromBreaking && hand.getDamageValue() >= hand.getMaxDamage() - 2)))
                break;
        }
    }

    private static boolean isSwordLike(ItemStack stack) {
        return stack.is(ItemTags.SWORDS) || stack.getItem() instanceof TridentItem;
    }

    public static boolean shouldDrop(Player player, ItemStack hand, BlockState state) {
        return !player.isCreative() && (!state.requiresCorrectToolForDrops() || hand.isCorrectToolForDrops(state));
    }

    public static boolean shouldDamage(Player player, ItemStack hand, float hardness, SimpleConfig.Durability durability) {
        return !player.isCreative() && hand.isDamageableItem() && (durability.consumeOnInstantBreak || hardness > 0) && (
                isSwordLike(hand) ||
                        hand.has(DataComponents.TOOL) ||
                        hand.getItem() instanceof ShearsItem
        );
    }
}
