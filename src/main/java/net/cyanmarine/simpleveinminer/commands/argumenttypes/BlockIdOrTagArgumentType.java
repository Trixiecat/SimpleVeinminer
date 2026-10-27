package net.cyanmarine.simpleveinminer.commands.argumenttypes;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Block;

import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.CompletableFuture;

public class BlockIdOrTagArgumentType implements ArgumentType<String> {
    private static final Collection<String> EXAMPLES = Arrays.asList("stone", "minecraft:stone", "#stone", "#minecraft:stone");
    private final HolderLookup<Block> blocks;

    public BlockIdOrTagArgumentType(CommandBuildContext commandBuildContext) {
        this.blocks = commandBuildContext.lookupOrThrow(Registries.BLOCK);
    }

    public static String parse(HolderLookup<Block> blocks, StringReader reader) throws CommandSyntaxException {
        String res = BlockStateParser.parseForTesting(blocks, reader, false).map(
                (result) -> BuiltInRegistries.BLOCK.getKey(result.blockState().getBlock()).toString(),
                (result) -> result.tag().unwrapKey().map(tagKey -> "#" + tagKey.location()).orElse(null)
        );
        if (res == null) throw new SimpleCommandExceptionType(Component.literal("Invalid block")).createWithContext(reader);
        return res;
    }

    public static String getBlockIdOrTag(CommandContext<CommandSourceStack> context, String name) throws CommandSyntaxException {
        return context.getArgument(name, String.class);
    }

    public static BlockIdOrTagArgumentType blockIdOrTag(CommandBuildContext commandBuildContext) {
        return new BlockIdOrTagArgumentType(commandBuildContext);
    }

    @Override
    public String parse(StringReader stringReader) throws CommandSyntaxException {
        return parse(this.blocks, stringReader);
    }

    @Override
    public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> context, SuggestionsBuilder builder) {
        return BlockStateParser.fillSuggestions(this.blocks, builder, true, false);
    }

    @Override
    public Collection<String> getExamples() {
        return EXAMPLES;
    }
}
