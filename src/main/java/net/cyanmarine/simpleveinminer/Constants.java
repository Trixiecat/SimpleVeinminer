package net.cyanmarine.simpleveinminer;

import net.cyanmarine.simpleveinminer.config.SimpleConfig;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Network payloads used to talk between the client and the server.
 */
public class Constants {
    /** Client -> server: is the veinmine key held/toggled */
    public record VeinminePayload(boolean veinmining) implements CustomPacketPayload {
        public static final Type<VeinminePayload> TYPE = new Type<>(SimpleVeinminer.getId("veinmine"));
        public static final StreamCodec<FriendlyByteBuf, VeinminePayload> CODEC = StreamCodec.composite(
                ByteBufCodecs.BOOL, VeinminePayload::veinmining, VeinminePayload::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Client -> server: the radius (spread) the player wants to use */
    public record RadiusPayload(int radius) implements CustomPacketPayload {
        public static final Type<RadiusPayload> TYPE = new Type<>(SimpleVeinminer.getId("radius"));
        public static final StreamCodec<FriendlyByteBuf, RadiusPayload> CODEC = StreamCodec.composite(
                ByteBufCodecs.INT, RadiusPayload::radius, RadiusPayload::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Server -> client: the "server side veinmining" game rule changed */
    public record ServerSideUpdatePayload(boolean enabled) implements CustomPacketPayload {
        public static final Type<ServerSideUpdatePayload> TYPE = new Type<>(SimpleVeinminer.getId("serverside_update"));
        public static final StreamCodec<FriendlyByteBuf, ServerSideUpdatePayload> CODEC = StreamCodec.composite(
                ByteBufCodecs.BOOL, ServerSideUpdatePayload::enabled, ServerSideUpdatePayload::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Server -> client: the parts of the server config the client needs for its preview */
    public record ConfigSyncPayload(SimpleConfig.SimpleConfigCopy config) implements CustomPacketPayload {
        public static final Type<ConfigSyncPayload> TYPE = new Type<>(SimpleVeinminer.getId("config_sync"));
        public static final StreamCodec<FriendlyByteBuf, ConfigSyncPayload> CODEC = StreamCodec.ofMember(
                (payload, buf) -> payload.config().write(buf),
                buf -> new ConfigSyncPayload(new SimpleConfig.SimpleConfigCopy(buf)));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
}
