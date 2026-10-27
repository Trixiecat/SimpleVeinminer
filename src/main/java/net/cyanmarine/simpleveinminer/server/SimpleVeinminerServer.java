package net.cyanmarine.simpleveinminer.server;

import net.cyanmarine.simpleveinminer.SimpleVeinminer;
import net.cyanmarine.simpleveinminer.config.SimpleConfig;
import net.fabricmc.api.DedicatedServerModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SimpleVeinminerServer implements DedicatedServerModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("SimpleVeinMiner - Server");

    public static SimpleConfig getConfig() {
        return SimpleVeinminer.getConfig();
    }

    @Override
    public void onInitializeServer() {
        SimpleVeinminer.setConfig(SimpleConfig.load(SimpleConfig.class));
        LOGGER.info("Simple veinminer initialized");
    }
}
