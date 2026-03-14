package com.presence.mod.system;

import com.presence.mod.config.PresenceConfig;
import com.presence.mod.data.PlayerProfile;
import com.presence.mod.data.PlayerProfile.HorrorType;
import com.presence.mod.network.PacketHandler;
import com.presence.mod.network.PacketHandler.EventType;
import com.presence.mod.network.PacketHandler.HorrorEventPayload;
import net.minecraft.block.Blocks;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;

import java.util.Random;

/**
 * PRESENCE — HorrorDirector
 *
 * Decides WHEN and WHAT horror to trigger, using the PlayerProfile AI model.
 * All timings and toggles are driven by PresenceConfig.
 */
public class HorrorDirector {

    private static final Random RAND = new Random();

    public static void tick(ServerPlayerEntity player, PlayerProfile profile) {
        if (!PresenceConfig.get().enabled) return;

        long currentTick = player.getWorld().getTime();
        PresenceConfig cfg = PresenceConfig.get();

        profile.tick(player);

        // Config-driven cooldown
        float sanityFactor = profile.sanity / 100f;
        int cooldownBase = cfg.scaledCooldown(cfg.horrorCooldownBase);
        int cooldownMin  = cfg.scaledCooldown(cfg.horrorCooldownMin);
        int cooldown = (int)(cooldownMin + (cooldownBase - cooldownMin) * sanityFactor);

        if (currentTick - profile.lastHorrorEventTick < cooldown) return;

        float chance = cfg.scaledChance(cfg.horrorBaseTriggerChance)
            + (profile.escalationLevel * cfg.horrorTriggerChancePerLevel);
        if (profile.sanity < 25) chance += cfg.horrorTriggerChanceLowSanity;
        if (RAND.nextFloat() > chance) return;

        // Pick event, re-rolling if that event type is disabled in config
        HorrorType type = profile.getBestHorrorType(RAND);
        int attempts = 0;
        while (!cfg.isEventEnabled(type) && attempts++ < 15) {
            type = profile.getBestHorrorType(RAND);
        }
        if (!cfg.isEventEnabled(type)) return;

        if (cfg.debugAnnounceEvents) {
            player.sendMessage(
                net.minecraft.text.Text.literal("§8[Presence DEBUG] Event: §e" + type.name()),
                false
            );
        }

        executeHorror(player, profile, type, currentTick);
    }

    /** Forced trigger for /presence scare command. */
    public static void forceScare(ServerPlayerEntity player, PlayerProfile profile) {
        HorrorType type = profile.getBestHorrorType(RAND);
        executeHorror(player, profile, type, player.getWorld().getTime());
    }

    private static void executeHorror(ServerPlayerEntity player, PlayerProfile profile,
                                       HorrorType type, long tick) {
        profile.lastHorrorEventTick = tick;
        profile.horrorEventCount++;

        switch (type) {

            case AMBIENT_SOUND -> {
                String[] sounds = {
                    "ambient.breath", "ambient.distant_groan",
                    "ambient.scrape", "ambient.heartbeat"
                };
                send(player, EventType.PLAY_SOUND, pick(sounds), null, 0);
                profile.soundEventCount++;
            }

            case FAKE_FOOTSTEP -> {
                send(player, EventType.PLAY_SOUND, "ambient.footstep", null, 0);
                profile.soundEventCount++;
            }

            case CLOSE_SOUND -> {
                send(player, EventType.PLAY_SOUND, "ambient.breathing_close", null, 0);
                profile.soundEventCount++;
            }

            case SHADOW_FLICKER -> {
                send(player, EventType.SCREEN_FLICKER, null, null, 10);
            }

            case LIGHTS_OUT -> {
                if (profile.fearsDarkness) {
                    int radius = PresenceConfig.get().lightsOutRadius;
                    BlockPos center = player.getBlockPos();
                    for (int dx = -radius; dx <= radius; dx++) for (int dz = -radius; dz <= radius; dz++) {
                        for (int dy = -3; dy <= 5; dy++) {
                            BlockPos pos = center.add(dx, dy, dz);
                            var block = player.getWorld().getBlockState(pos).getBlock();
                            if (block == Blocks.TORCH || block == Blocks.WALL_TORCH) {
                                player.getWorld().removeBlock(pos, false);
                            }
                        }
                    }
                    send(player, EventType.PLAY_SOUND, "ambient.lights_out", null, 0);
                }
            }

            case FAKE_BLOCK_PLACE -> {
                send(player, EventType.PLAY_SOUND, "ambient.block_place", null, 0);
            }

            case WATCHER_GLIMPSE -> {
                BlockPos pos = behindPlayer(player);
                profile.lastWatcherPos = pos;
                profile.watcherSpawned = true;
                send(player, EventType.SHOW_WATCHER, null, pos, 60);
            }

            case REVISIT_DEATH -> {
                if (!profile.deathLocations.isEmpty()) {
                    BlockPos deathSpot = profile.deathLocations.get(
                        RAND.nextInt(profile.deathLocations.size()));
                    send(player, EventType.DEATH_MEMORY, "ambient.your_blood", deathSpot, 0);
                }
            }

            case WHISPER -> {
                String[] whispers = {
                    "i see you", "you can't leave", "i was here before you",
                    "don't sleep", "it's already too late",
                    "you felt that, didn't you", "why do you keep coming back"
                };
                String w = pick(whispers);
                profile.heardWhispers.add(w);
                send(player, EventType.WHISPER, w, null, 0);
            }

            case HOME_INVASION -> {
                if (profile.homeBase != null) {
                    int radius = PresenceConfig.get().homeInvasionRadius;
                    double dist = player.getBlockPos().getSquaredDistance(profile.homeBase);
                    if (dist < (double)(radius * radius)) {
                        send(player, EventType.PLAY_SOUND, "ambient.home_creak", null, 0);
                        send(player, EventType.SCREEN_FLICKER, null, null, 10);
                    }
                }
            }

            case SLEEP_PARALYSIS -> {
                if (player.isSleeping()) {
                    send(player, EventType.SLEEP_TERROR, null, null, 80);
                    send(player, EventType.PLAY_SOUND, "ambient.sleep_scream", null, 0);
                }
            }

            case INVENTORY_TAMPER -> {
                var inventory = player.getInventory();
                for (int i = inventory.size() - 1; i >= 0; i--) {
                    ItemStack stack = inventory.getStack(i);
                    if (!stack.isEmpty() && stack.getItem() != Items.TORCH) {
                        ItemStack dropped = stack.copy();
                        inventory.removeStack(i);
                        ItemEntity entity = new ItemEntity(
                            player.getWorld(),
                            player.getX(), player.getY(), player.getZ(), dropped);
                        player.getWorld().spawnEntity(entity);
                        send(player, EventType.PLAY_SOUND, "ambient.tamper", null, 0);
                        break;
                    }
                }
            }

            case CHASE_SEQUENCE -> {
                BlockPos pos = behindPlayer(player);
                send(player, EventType.CHASE_BEGIN, "ambient.chase_theme", pos, 400);
            }

            case REALITY_BREAK -> {
                send(player, EventType.REALITY_BREAK, null, null, 200);
                send(player, EventType.PLAY_SOUND, "ambient.reality_tear", null, 0);
            }

            case IT_KNOWS_YOUR_NAME -> {
                String name = player.getName().getString();
                String[] msgs = {
                    name + "...",
                    "don't run, " + name,
                    name + " why are you afraid"
                };
                send(player, EventType.WHISPER, pick(msgs), null, 0);
                send(player, EventType.PLAY_SOUND, "ambient.knows_your_name", null, 0);
            }
        }
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private static void send(ServerPlayerEntity player, EventType type,
                              String str, BlockPos pos, int data) {
        PacketHandler.sendHorrorEvent(player, new HorrorEventPayload(type, str, pos, data));
    }

    private static String pick(String[] arr) {
        return arr[RAND.nextInt(arr.length)];
    }

    private static BlockPos behindPlayer(ServerPlayerEntity player) {
        BlockPos center = player.getBlockPos();
        double angle = Math.toRadians(player.getYaw() + 180 + (RAND.nextFloat() - 0.5f) * 40);
        int dist = 25 + RAND.nextInt(15);
        return center.add((int)(Math.sin(angle) * dist), 0, (int)(Math.cos(angle) * dist));
    }
}
