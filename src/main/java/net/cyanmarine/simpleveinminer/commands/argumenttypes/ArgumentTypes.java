package net.cyanmarine.simpleveinminer.commands.argumenttypes;

import net.cyanmarine.simpleveinminer.SimpleVeinminer;
import net.fabricmc.fabric.api.command.v2.ArgumentTypeRegistry;
import net.minecraft.commands.synchronization.SingletonArgumentInfo;

public class ArgumentTypes {
    public ArgumentTypes() {
        SimpleVeinminer.LOGGER.info("Initializing command argument types");
        ArgumentTypeRegistry.registerArgumentType(SimpleVeinminer.getId("list_item"), RestrictionListArgumentType.class, SingletonArgumentInfo.contextFree(RestrictionListArgumentType::listItem));
        ArgumentTypeRegistry.registerArgumentType(SimpleVeinminer.getId("tag_list"), TagListArgumentType.class, SingletonArgumentInfo.contextFree(TagListArgumentType::listItem));
        ArgumentTypeRegistry.registerArgumentType(SimpleVeinminer.getId("block_id_or_tag"), BlockIdOrTagArgumentType.class, SingletonArgumentInfo.contextAware(BlockIdOrTagArgumentType::blockIdOrTag));
        ArgumentTypeRegistry.registerArgumentType(SimpleVeinminer.getId("highlight_modes"), HighlightModesArgumentType.class, SingletonArgumentInfo.contextFree(HighlightModesArgumentType::highlightModes));
    }
}
