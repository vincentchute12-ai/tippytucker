package com.presence.mod.event;

import com.presence.mod.network.EntityChatPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

/**
 * PRESENCE — EntityChatHandler
 *
 * Receives the EntityChatPayload from a client and broadcasts
 * the entity's message to ALL players on the server as a raw
 * system message — no player name, no header, just the text
 * appearing in everyone's chat out of nowhere.
 *
 * Rate-limited so a client can't spam it.
 */
public class EntityChatHandler {

    // Per-player timestamp of last entity chat broadcast (ms)
    private static final java.util.Map<java.util.UUID, Long> lastBroadcast =
        new java.util.HashMap<>();
    private static final long RATE_LIMIT_MS = 3000; // max one message per 3 seconds

    public static void register() {
        ServerPlayNetworking.registerGlobalReceiver(
            EntityChatPayload.ID, (payload, context) -> {
                ServerPlayerEntity sender = context.player();
                MinecraftServer server = context.server();

                // Rate limit
                long now = System.currentTimeMillis();
                Long last = lastBroadcast.get(sender.getUuid());
                if (last != null && now - last < RATE_LIMIT_MS) return;
                lastBroadcast.put(sender.getUuid(), now);

                // Sanitize — strip any formatting codes the client injected
                // then re-apply our own controlled formatting
                String raw = payload.message().replaceAll("§.", "").trim();
                if (raw.isEmpty() || raw.length() > 200) return;

                // Broadcast to every player as a system message
                // System messages have no sender name — they just appear raw
                Text message = Text.literal("§8§o" + raw);
                for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                    player.sendMessage(message, false);
                }
            }
        );
    }
}
