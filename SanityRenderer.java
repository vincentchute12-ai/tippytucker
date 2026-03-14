package com.presence.mod.network;

import com.presence.mod.PresenceMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * PRESENCE — EntityChatPayload
 *
 * Client → Server packet.
 * The client sends this when the entity wants to say something.
 * The server receives it and broadcasts the message to ALL players
 * as a system message — no player name attached, it just appears
 * raw in everyone's chat like it came from nowhere.
 */
public record EntityChatPayload(String message) implements CustomPayload {

    public static final Id<EntityChatPayload> ID =
        new Id<>(Identifier.of(PresenceMod.MOD_ID, "entity_chat"));

    public static final PacketCodec<RegistryByteBuf, EntityChatPayload> CODEC =
        PacketCodec.of(EntityChatPayload::write, EntityChatPayload::read);

    public static EntityChatPayload read(RegistryByteBuf buf) {
        return new EntityChatPayload(buf.readString(256));
    }

    public void write(RegistryByteBuf buf) {
        buf.writeString(message, 256);
    }

    @Override
    public Id<? extends CustomPayload> getId() { return ID; }
}
