package com.presence.mod.network;

import com.presence.mod.PresenceMod;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;

public class PacketHandler {

    public static void registerPackets() {
        PayloadTypeRegistry.playS2C().register(HorrorEventPayload.ID, HorrorEventPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(SanityPayload.ID, SanityPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(EntityChatPayload.ID, EntityChatPayload.CODEC);
    }

    public static void sendHorrorEvent(ServerPlayerEntity player, HorrorEventPayload payload) {
        ServerPlayNetworking.send(player, payload);
    }

    public static void sendSanityUpdate(ServerPlayerEntity player, float sanity, int escalation) {
        ServerPlayNetworking.send(player, new SanityPayload(sanity, escalation));
    }

    // ── Horror Event Payload ────────────────────────────────────────────────

    public record HorrorEventPayload(
        EventType eventType,
        String stringData,
        BlockPos posData,
        int intData
    ) implements CustomPayload {

        public static final Id<HorrorEventPayload> ID =
            new Id<>(Identifier.of(PresenceMod.MOD_ID, "horror_event"));

        public static final PacketCodec<RegistryByteBuf, HorrorEventPayload> CODEC =
            PacketCodec.of(HorrorEventPayload::write, HorrorEventPayload::read);

        public static HorrorEventPayload read(RegistryByteBuf buf) {
            EventType type = buf.readEnumConstant(EventType.class);
            String str = buf.readBoolean() ? buf.readString() : null;
            BlockPos pos = buf.readBoolean() ? buf.readBlockPos() : null;
            int data = buf.readInt();
            return new HorrorEventPayload(type, str, pos, data);
        }

        public void write(RegistryByteBuf buf) {
            buf.writeEnumConstant(eventType);
            buf.writeBoolean(stringData != null);
            if (stringData != null) buf.writeString(stringData);
            buf.writeBoolean(posData != null);
            if (posData != null) buf.writeBlockPos(posData);
            buf.writeInt(intData);
        }

        @Override
        public Id<? extends CustomPayload> getId() { return ID; }
    }

    // ── Sanity Payload ──────────────────────────────────────────────────────

    public record SanityPayload(float sanity, int escalationLevel) implements CustomPayload {

        public static final Id<SanityPayload> ID =
            new Id<>(Identifier.of(PresenceMod.MOD_ID, "sanity_sync"));

        public static final PacketCodec<RegistryByteBuf, SanityPayload> CODEC =
            PacketCodec.of(SanityPayload::write, SanityPayload::read);

        public static SanityPayload read(RegistryByteBuf buf) {
            return new SanityPayload(buf.readFloat(), buf.readInt());
        }

        public void write(RegistryByteBuf buf) {
            buf.writeFloat(sanity);
            buf.writeInt(escalationLevel);
        }

        @Override
        public Id<? extends CustomPayload> getId() { return ID; }
    }

    public enum EventType {
        // Single-player horror
        PLAY_SOUND, SCREEN_FLICKER, SHOW_WATCHER, DEATH_MEMORY,
        WHISPER, SLEEP_TERROR, CHASE_BEGIN, REALITY_BREAK,

        // Multiplayer horror
        SHOW_MIMIC,       // render Mimic wearing another player's skin at a position
        FAKE_DISCONNECT   // show fake "[player] left the game" then rejoin
    }
}
