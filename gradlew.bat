package com.presence.mod.system;

import com.presence.mod.config.PresenceConfig;
import com.presence.mod.data.PlayerProfile;
import com.presence.mod.data.ProfileManager;
import com.presence.mod.network.PacketHandler;
import com.presence.mod.network.PacketHandler.EventType;
import com.presence.mod.network.PacketHandler.HorrorEventPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;

import java.util.*;

/**
 * PRESENCE — MultiplayerHorrorSystem
 */
public class MultiplayerHorrorSystem {

    private static final Random RAND = new Random();
    private static long lastMultiEventTime = 0;

    public static void tick(MinecraftServer server, ProfileManager mgr) {
        PresenceConfig cfg = PresenceConfig.get();
        if (!cfg.enabled || !cfg.multiplayerHorrorEnabled) return;

        List<ServerPlayerEntity> players = server.getPlayerManager().getPlayerList();
        if (players.size() < 2) return;

        long now = System.currentTimeMillis();
        if (now - lastMultiEventTime < cfg.multiplayerEventCooldownMs) return;

        boolean anyScared = players.stream()
            .anyMatch(p -> mgr.getProfile(p.getUuid()).sanity < cfg.multiplayerSanityThreshold);
        if (!anyScared) return;

        // Pick an enabled multiplayer event
        MultiEvent event = pickEnabledEvent(cfg);
        if (event == null) return;

        executeMultiEvent(server, players, mgr, event, cfg);
        lastMultiEventTime = now;
    }

    private static MultiEvent pickEnabledEvent(PresenceConfig cfg) {
        List<MultiEvent> candidates = new java.util.ArrayList<>();
        for (MultiEvent e : MultiEvent.values()) {
            if (cfg.isMpEventEnabled(e)) candidates.add(e);
        }
        if (candidates.isEmpty()) return null;
        return candidates.get(RAND.nextInt(candidates.size()));
    }

    private static void executeMultiEvent(MinecraftServer server,
                                           List<ServerPlayerEntity> players,
                                           ProfileManager mgr, MultiEvent event,
                                           PresenceConfig cfg) {
        // Pick two different players: the "source" (unwitting tool) and "target" (victim)
        ServerPlayerEntity target = players.get(RAND.nextInt(players.size()));
        ServerPlayerEntity source = players.stream()
            .filter(p -> !p.getUuid().equals(target.getUuid()))
            .findAny().orElse(null);
        if (source == null) return;

        PlayerProfile targetProfile = mgr.getProfile(target.getUuid());
        PlayerProfile sourceProfile = mgr.getProfile(source.getUuid());

        switch (event) {

            case FAKE_CHAT_MESSAGE -> {
                // Send a fake chat message to the target that appears to be from the source
                // e.g., "[PlayerB]: did you hear that"
                String[] creepyMessages = {
                    "did you hear that",
                    "something is behind you",
                    "don't look at the door",
                    "i think it followed me here",
                    "why are you still up",
                    "i can see you from here",
                    "it's watching both of us"
                };
                String msg = source.getName().getString() + ": §7§o"
                    + creepyMessages[RAND.nextInt(creepyMessages.length)];
                sendWhisper(target, msg);
                // The real source sees nothing — they didn't send it
            }

            case WHISPER_OTHER_NAME -> {
                // Whisper the other player's name to the target
                String name = source.getName().getString();
                String[] lines = {
                    "§8§o" + name + " left you",
                    "§8§o" + name + " can't help you",
                    "§8§o" + name + " is already gone",
                    "§8§owhere did " + name + " go",
                    "§8§o" + name + " saw it too"
                };
                sendWhisper(target, lines[RAND.nextInt(lines.length)]);
                // Also send a mirror event to source
                sendWhisper(source, "§8§o" + target.getName().getString() + " needs you");
            }

            case SYNCHRONIZED_SCARE -> {
                // Both players hear a sound at the same time — makes each think
                // the other caused it
                String[] sounds = {"ambient.footstep", "ambient.scrape", "ambient.breath"};
                String sound = sounds[RAND.nextInt(sounds.length)];
                sendSound(target, sound);
                sendSound(source, sound);
            }

            case MIMIC_VISIT -> {
                BlockPos mimicPos = nearPlayerConfigured(target, cfg);
                PacketHandler.sendHorrorEvent(target, new HorrorEventPayload(
                    EventType.SHOW_MIMIC, source.getUuid().toString(), mimicPos, 120));
                sendSound(target, "ambient.footstep");
                sendWhisper(source, "§8§oit wore your face");
            }

            case ISOLATION_FAKE_LEAVE -> {
                // Show a fake "[PlayerB] left the game" message to the target
                // Then a "joined the game" 10 seconds later — pure psychological dread
                PacketHandler.sendHorrorEvent(target, new HorrorEventPayload(
                    EventType.FAKE_DISCONNECT,
                    source.getName().getString(),
                    null, 200 // ticks before fake-rejoin
                ));
            }

            case GASLIGHTING -> {
                // Tell target that source said something disturbing
                // Then tell source a different disturbing thing
                String[] toTarget = {
                    "§8§o" + source.getName().getString() + " said to leave without you",
                    "§8§o" + source.getName().getString() + " asked me to tell you: run"
                };
                String[] toSource = {
                    "§8§o" + target.getName().getString() + " said they can see it",
                    "§8§o" + target.getName().getString() + " stopped moving"
                };
                sendWhisper(target, toTarget[RAND.nextInt(toTarget.length)]);
                sendWhisper(source, toSource[RAND.nextInt(toSource.length)]);
            }

            case PROXIMITY_TERROR -> {
                double dist = target.squaredDistanceTo(source);
                int pr = cfg.proximityTerrorRadius;
                if (dist < (double)(pr * pr)) {
                    sendFlicker(target);
                    sendFlicker(source);
                    sendSound(target, "ambient.heartbeat");
                    sendSound(source, "ambient.heartbeat");

                    // Watcher appears between them
                    BlockPos between = target.getBlockPos().add(source.getBlockPos()).multiply(0).add(
                        (target.getBlockX() + source.getBlockX()) / 2,
                        (target.getBlockY() + source.getBlockY()) / 2,
                        (target.getBlockZ() + source.getBlockZ()) / 2
                    );
                    PacketHandler.sendHorrorEvent(target, new HorrorEventPayload(
                        EventType.SHOW_WATCHER, null, between, 80));
                    PacketHandler.sendHorrorEvent(source, new HorrorEventPayload(
                        EventType.SHOW_WATCHER, null, between, 80));
                }
            }
        }

        // Update profiles
        targetProfile.horrorEventCount++;
        sourceProfile.horrorEventCount++;
        mgr.markDirty();
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private static void sendWhisper(ServerPlayerEntity p, String msg) {
        PacketHandler.sendHorrorEvent(p, new HorrorEventPayload(EventType.WHISPER, msg, null, 0));
    }

    private static void sendSound(ServerPlayerEntity p, String sound) {
        PacketHandler.sendHorrorEvent(p, new HorrorEventPayload(EventType.PLAY_SOUND, sound, null, 0));
    }

    private static void sendFlicker(ServerPlayerEntity p) {
        PacketHandler.sendHorrorEvent(p, new HorrorEventPayload(EventType.SCREEN_FLICKER, null, null, 10));
    }

    private static BlockPos nearPlayer(ServerPlayerEntity player) {
        double angle = Math.toRadians(player.getYaw() + (RAND.nextFloat() - 0.5f) * 60);
        int dist = 8 + RAND.nextInt(10);
        return player.getBlockPos().add(
            (int)(Math.sin(angle) * dist), 0, (int)(Math.cos(angle) * dist));
    }

    public enum MultiEvent {
        FAKE_CHAT_MESSAGE,
        WHISPER_OTHER_NAME,
        SYNCHRONIZED_SCARE,
        MIMIC_VISIT,
        ISOLATION_FAKE_LEAVE,
        GASLIGHTING,
        PROXIMITY_TERROR
    }
}
