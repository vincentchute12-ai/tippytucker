package com.presence.mod.world;

import com.presence.mod.config.PresenceConfig;
import com.presence.mod.data.PlayerProfile;
import com.presence.mod.world.WorldHorrorBuilder.StructureType;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.Random;

/**
 * PRESENCE — WorldHorrorDirector
 *
 * Decides when to place world structures and which type to use,
 * based on the player's AI profile and escalation level.
 *
 * World events are rarer and more impactful than audio/visual events —
 * they're permanent (or semi-permanent) changes the player discovers.
 */
public class WorldHorrorDirector {

    private static final Random RAND = new Random();

    // Cooldown between world builds (much longer than audio events)
    private static final int BASE_COOLDOWN = 12000; // ~10 min
    private static final int MIN_COOLDOWN  = 3600;  //  ~3 min

    // Per-player last build tick tracker (stored outside profile to avoid NBT bloat)
    private static final java.util.Map<java.util.UUID, Long> lastBuildTick =
        new java.util.HashMap<>();

    public static void tick(ServerPlayerEntity player, PlayerProfile profile) {
        PresenceConfig cfg = PresenceConfig.get();
        if (!cfg.enabled) return;

        // World events don't start until escalation level 1
        if (profile.escalationLevel < 1) return;

        long currentTick = player.getWorld().getTime();
        long last = lastBuildTick.getOrDefault(player.getUuid(), 0L);

        float sanityFactor = profile.sanity / 100f;
        int cooldown = (int)(MIN_COOLDOWN + (BASE_COOLDOWN - MIN_COOLDOWN) * sanityFactor);
        cooldown = cfg.scaledCooldown(cooldown);

        if (currentTick - last < cooldown) return;

        // Low trigger chance — world events should feel rare and shocking
        float chance = 0.01f + (profile.escalationLevel * 0.008f);
        if (RAND.nextFloat() > chance) return;

        StructureType type = pickStructure(profile);
        WorldHorrorBuilder.build(player, profile, type);
        lastBuildTick.put(player.getUuid(), currentTick);
    }

    private static StructureType pickStructure(PlayerProfile profile) {
        java.util.List<StructureType> candidates = new java.util.ArrayList<>();

        // Always available at escalation 1+
        candidates.add(StructureType.SIGN);
        candidates.add(StructureType.TRAIL);

        if (profile.escalationLevel >= 2) {
            candidates.add(StructureType.RING);
            candidates.add(StructureType.CROSS);
            if (!profile.deathLocations.isEmpty())
                candidates.add(StructureType.OBSIDIAN_MARK);
        }

        if (profile.escalationLevel >= 3) {
            candidates.add(StructureType.GRAVE);
        }

        // Weight GRAVE and CROSS higher at very low sanity
        if (profile.sanity < 25) {
            candidates.add(StructureType.CROSS);
            candidates.add(StructureType.GRAVE);
        }

        return candidates.get(RAND.nextInt(candidates.size()));
    }
}
