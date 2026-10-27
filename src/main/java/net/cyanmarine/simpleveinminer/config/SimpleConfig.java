package net.cyanmarine.simpleveinminer.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.cyanmarine.simpleveinminer.SimpleVeinminer;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.FriendlyByteBuf;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Server side (and shared) configuration.
 * Saved as JSON in config/simpleveinminer.json (replaces the old CompleteConfig based config).
 */
public class SimpleConfig {
    protected static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public boolean placeInInventory = false;
    public boolean debug = false;
    public Limits limits = new Limits();
    public Restrictions restrictions = new Restrictions();
    public Exhaustion exhaustion = new Exhaustion();
    public Durability durability = new Durability();

    public static Path getPath() {
        return FabricLoader.getInstance().getConfigDir().resolve(SimpleVeinminer.MOD_ID + ".json");
    }

    public static <T extends SimpleConfig> T load(Class<T> type) {
        Path path = getPath();
        T config = null;
        if (Files.exists(path)) {
            try (Reader reader = Files.newBufferedReader(path)) {
                config = GSON.fromJson(reader, type);
            } catch (Exception e) {
                SimpleVeinminer.LOGGER.error("Could not read config file {}, using defaults", path, e);
            }
        }
        if (config == null) {
            try {
                config = type.getDeclaredConstructor().newInstance();
            } catch (ReflectiveOperationException e) {
                throw new RuntimeException(e);
            }
        }
        config.fixNulls();
        config.save();
        return config;
    }

    protected void fixNulls() {
        if (limits == null) limits = new Limits();
        if (limits.spreadAccuracy == null) limits.spreadAccuracy = Limits.SPREAD_ACCURACY.ACCURATE;
        if (restrictions == null) restrictions = new Restrictions();
        if (restrictions.restrictionList == null) restrictions.restrictionList = new Restrictions.RestrictionList();
        if (restrictions.restrictionList.list == null) restrictions.restrictionList.list = new ArrayList<>();
        if (restrictions.restrictionList.listType == null) restrictions.restrictionList.listType = Restrictions.RestrictionList.ListType.NONE;
        if (restrictions.restrictionTags == null) restrictions.restrictionTags = new Restrictions.RestrictionTags();
        if (restrictions.restrictionTags.tags == null) restrictions.restrictionTags.tags = new ArrayList<>();
        if (exhaustion == null) exhaustion = new Exhaustion();
        if (durability == null) durability = new Durability();
    }

    public void save() {
        Path path = getPath();
        try {
            Files.createDirectories(path.getParent());
            try (Writer writer = Files.newBufferedWriter(path)) {
                GSON.toJson(this, writer);
            }
        } catch (IOException e) {
            SimpleVeinminer.LOGGER.error("Could not save config file {}", path, e);
        }
    }

    public void reset() {
        this.placeInInventory = false;
        this.limits = new Limits();
        this.restrictions = new Restrictions();
        this.exhaustion = new Exhaustion();
        this.durability = new Durability();
        syncConfig();
    }

    public static void syncConfig() {
        SimpleVeinminer.getConfig().save();
        SimpleVeinminer.SyncConfigForAllPlayers();
    }

    public void setPlaceInInventory(boolean placeInInventory) {
        this.placeInInventory = placeInInventory;
        syncConfig();
    }

    public void setDebug(boolean debug) {
        this.debug = debug;
        syncConfig();
    }

    public static class Limits {
        public int maxBlocks = 150;
        public boolean materialBasedLimits = false;
        public int radius = 1;
        public SPREAD_ACCURACY spreadAccuracy = SPREAD_ACCURACY.ACCURATE;

        public enum SPREAD_ACCURACY {
            KINDA_ACCURATE, ACCURATE
        }

        public void setMaxBlocks(int maxBlocks) {
            this.maxBlocks = maxBlocks;
            syncConfig();
        }

        public void setMaterialBasedLimits(boolean materialBasedLimits) {
            this.materialBasedLimits = materialBasedLimits;
            syncConfig();
        }

        public void setRadius(int radius) {
            if (radius < 1) radius = 1;
            this.radius = radius;
            syncConfig();
        }

        public void setAccurateSpread(SPREAD_ACCURACY spreadAccuracy) {
            this.spreadAccuracy = spreadAccuracy;
            syncConfig();
        }
    }

    public static class Restrictions {
        public boolean canVeinmineHungry = false;
        public boolean canVeinmineWithEmptyHand = true;
        public boolean creativeBypass = false;
        public boolean keepToolFromBreaking = true;
        public boolean onlyBreakBottomBlockForChainReactions = false;
        /** Will only allow to veinmine wood using an axe, dirt using a shovel, stone using a pickaxe, etc. */
        public boolean canOnlyUseSuitableTools = false;
        public RestrictionList restrictionList = new RestrictionList();
        public RestrictionTags restrictionTags = new RestrictionTags();

        public void setKeepToolFromBreaking(boolean keepToolFromBreaking) {
            this.keepToolFromBreaking = keepToolFromBreaking;
            syncConfig();
        }

        public void setCanVeinmineWithEmptyHand(boolean canVeinmineWithEmptyHand) {
            this.canVeinmineWithEmptyHand = canVeinmineWithEmptyHand;
            syncConfig();
        }

        public void setCreativeBypass(boolean creativeBypass) {
            this.creativeBypass = creativeBypass;
            syncConfig();
        }

        public void setCanVeinmineHungry(boolean canVeinmineHungry) {
            this.canVeinmineHungry = canVeinmineHungry;
            syncConfig();
        }

        public void setCanOnlyUseSuitableTools(boolean canOnlyUseSuitableTools) {
            this.canOnlyUseSuitableTools = canOnlyUseSuitableTools;
            syncConfig();
        }

        public static class RestrictionTags {
            public boolean enabled = true;
            public List<String> tags = new ArrayList<>(Arrays.asList("#c:.+_ores", "minecraft:(short_|tall_)?grass", "minecraft:(large_)?fern"));

            public void setEnabled(boolean enabled) {
                this.enabled = enabled;
                syncConfig();
            }

            public void setTags(List<String> tags) {
                this.tags = new ArrayList<>(tags);
                syncConfig();
            }

            public void add(String value) {
                if (this.tags.contains(value)) return;
                ArrayList<String> newList = new ArrayList<>(this.tags);
                newList.add(value);
                this.setTags(newList);
            }

            public void remove(String value) {
                if (!this.tags.contains(value)) return;
                ArrayList<String> newList = new ArrayList<>(this.tags);
                newList.remove(value);
                this.setTags(newList);
            }

            public void clear() {
                this.setTags(new ArrayList<>());
            }
        }

        public static class RestrictionList {
            /** Valid values are NONE, BLACKLIST, and WHITELIST */
            public ListType listType = ListType.NONE;
            /** More information at https://github.com/PrincessCyanMarine/Simple-Veinminer/wiki/Whitelist-and-Blacklist */
            public List<String> list = new ArrayList<>(Arrays.asList("#c:logs", "#c:ores"));

            public void setList(List<String> list) {
                this.list = new ArrayList<>(list);
                syncConfig();
            }

            public void add(String value) {
                if (this.list.contains(value)) return;
                ArrayList<String> newList = new ArrayList<>(this.list);
                newList.add(value);
                this.setList(newList);
            }

            public void remove(String value) {
                ArrayList<String> newList = new ArrayList<>(this.list);
                newList.remove(value);
                this.setList(newList);
            }

            public void setListType(ListType listType) {
                this.listType = listType;
                syncConfig();
            }

            public enum ListType {
                NONE, WHITELIST, BLACKLIST
            }
        }
    }

    public static class Exhaustion {
        public boolean exhaust = true;
        public double baseValue = 0.3;
        public boolean exhaustionBasedOnHardness = true;
        public double hardnessWeight = 0.1;

        public void setExhaust(boolean exhaust) {
            this.exhaust = exhaust;
            SimpleVeinminer.getConfig().save();
        }

        public void setBaseValue(double baseValue) {
            this.baseValue = baseValue;
            SimpleVeinminer.getConfig().save();
        }

        public void setExhaustionBasedOnHardness(boolean exhaustionBasedOnHardness) {
            this.exhaustionBasedOnHardness = exhaustionBasedOnHardness;
            SimpleVeinminer.getConfig().save();
        }

        public void setHardnessWeight(double hardnessWeight) {
            this.hardnessWeight = hardnessWeight;
            SimpleVeinminer.getConfig().save();
        }
    }

    public static class Durability {
        public double damageMultiplier = 1.0;
        public double swordMultiplier = 2.0;
        public boolean consumeOnInstantBreak = false;

        public void setDamageMultiplier(double damageMultiplier) {
            this.damageMultiplier = damageMultiplier;
            SimpleVeinminer.getConfig().save();
        }

        public void setSwordMultiplier(double swordMultiplier) {
            this.swordMultiplier = swordMultiplier;
            SimpleVeinminer.getConfig().save();
        }

        public void setConsumeOnInstantBreak(boolean consumeOnInstantBreak) {
            this.consumeOnInstantBreak = consumeOnInstantBreak;
            SimpleVeinminer.getConfig().save();
        }
    }

    /**
     * The part of the server's config that is sent to clients so their preview matches what the server will do.
     */
    public static class SimpleConfigCopy {
        public Restrictions restrictions = new Restrictions();
        public Limits limits = new Limits();

        private SimpleConfigCopy() {
        }

        public SimpleConfigCopy(FriendlyByteBuf buf) {
            this.restrictions.canVeinmineHungry = buf.readBoolean();
            this.restrictions.canVeinmineWithEmptyHand = buf.readBoolean();
            this.restrictions.creativeBypass = buf.readBoolean();
            this.restrictions.canOnlyUseSuitableTools = buf.readBoolean();
            this.restrictions.restrictionList.listType = Restrictions.RestrictionList.ListType.values()[buf.readVarInt()];
            String list = buf.readUtf();
            this.restrictions.restrictionList.list = list.isEmpty() ? new ArrayList<>() : new ArrayList<>(Arrays.asList(list.split(";")));

            this.limits.maxBlocks = buf.readInt();
            this.limits.materialBasedLimits = buf.readBoolean();
            this.limits.radius = buf.readInt();
        }

        public void write(FriendlyByteBuf buf) {
            buf.writeBoolean(restrictions.canVeinmineHungry);
            buf.writeBoolean(restrictions.canVeinmineWithEmptyHand);
            buf.writeBoolean(restrictions.creativeBypass);
            buf.writeBoolean(restrictions.canOnlyUseSuitableTools);
            buf.writeVarInt(restrictions.restrictionList.listType.ordinal());
            buf.writeUtf(String.join(";", restrictions.restrictionList.list));

            buf.writeInt(limits.maxBlocks);
            buf.writeBoolean(limits.materialBasedLimits);
            buf.writeInt(limits.radius);
        }

        public static SimpleConfigCopy from(SimpleConfig config) {
            SimpleConfigCopy res = new SimpleConfigCopy();

            res.restrictions = config.restrictions;
            res.limits = config.limits;

            return res;
        }
    }
}
