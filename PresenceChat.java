package com.presence.mod.data;

import net.minecraft.block.Blocks;
import net.minecraft.entity.player.PlayerEntity;
import com.presence.mod.config.PresenceConfig;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.LightType;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * PRESENCE — PlayerProfile
 *
 * The AI core. Tracks everything about a player's behaviour and builds a
 * psychological model used to select and target horror events.
 *
 * It learns:
 *  • When they sleep (or if they skip nights entirely)
 *  • Whether they fear darkness (torch-hoarding behaviour)
 *  • Where they died — and revisits those locations
 *  • How they react to sounds (movement spikes after audio events)
 *  • Whether they fight or flee mobs
 *  • Time-of-day preferences
 */
public class PlayerProfile {

    // ── Sanity ──────────────────────────────────────────────────────────────
    public float sanity = 100.0f;
    public int totalPlayTicks = 0;

    // ── Sleep ────────────────────────────────────────────────────────────────
    public int sleepAttempts = 0;
    public int sleepSuccesses = 0;
    public boolean avoidsSleep = false;

    // ── Light / Darkness fear ────────────────────────────────────────────────
    public int torchesPlaced = 0;
    public int timeSpentInDark = 0;
    public boolean fearsDarkness = false;

    // ── Death memory ─────────────────────────────────────────────────────────
    public List<BlockPos> deathLocations = new ArrayList<>();
    public List<String> deathCauses = new ArrayList<>();
    public int totalDeaths = 0;

    // ── Movement profile ─────────────────────────────────────────────────────
    public long sprintTicks = 0;
    public long sneakTicks = 0;
    public boolean isNervous = false;

    // ── Location memory ──────────────────────────────────────────────────────
    public BlockPos homeBase = null;

    // ── Sound reactivity ─────────────────────────────────────────────────────
    public int soundReactionCount = 0;
    public int soundEventCount = 0;
    public float soundReactivity = 0.0f;

    // ── Combat ───────────────────────────────────────────────────────────────
    public int fleeCount = 0;
    public int fightCount = 0;
    public boolean isCowardly = false;

    // ── Time preference ──────────────────────────────────────────────────────
    public long ticksPlayedAtNight = 0;
    public long ticksPlayedAtDay = 0;
    public boolean playsAtNight = false;

    // ── Horror escalation ────────────────────────────────────────────────────
    public int horrorEventCount = 0;
    public long lastHorrorEventTick = 0;
    public int escalationLevel = 0;   // 0–4

    // ── Watcher state ────────────────────────────────────────────────────────
    public boolean watcherSpawned = false;
    public int watcherSeenCount = 0;
    public BlockPos lastWatcherPos = null;

    // ── Whispers heard ───────────────────────────────────────────────────────
    public List<String> heardWhispers = new ArrayList<>();

    // ════════════════════════════════════════════════════════════════════════
    // TICK — called every server tick per player
    // ════════════════════════════════════════════════════════════════════════

    public void tick(PlayerEntity player) {
        totalPlayTicks++;

        long timeOfDay = player.getWorld().getTimeOfDay() % 24000;
        if (timeOfDay > 13000 && timeOfDay < 23000) ticksPlayedAtNight++;
        else ticksPlayedAtDay++;

        if (totalPlayTicks % 1200 == 0) updateLearning();

        // Sanity decay — rates driven by config
        PresenceConfig cfg = PresenceConfig.get();
        float decayRate = cfg.scaledDecay(cfg.sanityDecayBase)
            + (escalationLevel * cfg.scaledDecay(cfg.sanityDecayPerEscalationLevel));
        if (avoidsSleep) decayRate += cfg.scaledDecay(cfg.sanityDecayAvoidsSleep);
        int lightLevel = player.getWorld()
            .getLightLevel(LightType.BLOCK, player.getBlockPos());
        if (fearsDarkness && lightLevel < 4) decayRate += cfg.scaledDecay(cfg.sanityDecayInDark);
        sanity = Math.max(0, sanity - decayRate);

        // Movement tracking
        if (player.isSprinting()) sprintTicks++;
        if (player.isSneaking()) sneakTicks++;

        // Darkness time
        int blockLight = player.getWorld()
            .getLightLevel(LightType.BLOCK, player.getBlockPos());
        if (blockLight <= 4) timeSpentInDark++;
    }

    public void updateLearning() {
        // Darkness fear
        float torchRate = totalPlayTicks > 0
            ? (float) torchesPlaced / (totalPlayTicks / 1200f) : 0;
        fearsDarkness = torchRate > 5f;

        // Sleep avoidance
        if (sleepAttempts > 3) avoidsSleep = sleepSuccesses < sleepAttempts / 2;

        // Nervous movement
        if (soundEventCount > 0) {
            soundReactivity = (float) soundReactionCount / soundEventCount;
            isNervous = soundReactivity > 0.5f;
        }

        // Cowardice
        if (fleeCount + fightCount > 5) isCowardly = fleeCount > fightCount;

        // Night preference
        if (ticksPlayedAtDay + ticksPlayedAtNight > 12000)
            playsAtNight = ticksPlayedAtNight > ticksPlayedAtDay;

        // Escalation by sanity — thresholds from config
        float[] thresh = PresenceConfig.get().escalationThresholds;
        if (sanity < thresh[0]) escalationLevel = Math.max(escalationLevel, 1);
        if (sanity < thresh[1]) escalationLevel = Math.max(escalationLevel, 2);
        if (sanity < thresh[2]) escalationLevel = Math.max(escalationLevel, 3);
        if (sanity < thresh[3]) escalationLevel = Math.max(escalationLevel, 4);
    }

    /**
     * The AI decision: pick the most effective horror for THIS player.
     */
    public HorrorType getBestHorrorType(Random rand) {
        List<HorrorType> candidates = new ArrayList<>();
        candidates.add(HorrorType.AMBIENT_SOUND);

        if (escalationLevel >= 1) {
            if (fearsDarkness) candidates.add(HorrorType.LIGHTS_OUT);
            candidates.add(HorrorType.FAKE_FOOTSTEP);
            candidates.add(HorrorType.SHADOW_FLICKER);
        }
        if (escalationLevel >= 2) {
            if (!deathLocations.isEmpty()) candidates.add(HorrorType.REVISIT_DEATH);
            if (isNervous) candidates.add(HorrorType.CLOSE_SOUND);
            candidates.add(HorrorType.WATCHER_GLIMPSE);
            candidates.add(HorrorType.FAKE_BLOCK_PLACE);
        }
        if (escalationLevel >= 3) {
            candidates.add(HorrorType.WHISPER);
            if (homeBase != null) candidates.add(HorrorType.HOME_INVASION);
            if (avoidsSleep) candidates.add(HorrorType.SLEEP_PARALYSIS);
            candidates.add(HorrorType.INVENTORY_TAMPER);
        }
        if (escalationLevel >= 4) {
            candidates.add(HorrorType.CHASE_SEQUENCE);
            candidates.add(HorrorType.REALITY_BREAK);
            candidates.add(HorrorType.IT_KNOWS_YOUR_NAME);
        }

        return candidates.get(rand.nextInt(candidates.size()));
    }

    public enum HorrorType {
        AMBIENT_SOUND, FAKE_FOOTSTEP, CLOSE_SOUND, SHADOW_FLICKER,
        LIGHTS_OUT, FAKE_BLOCK_PLACE, WATCHER_GLIMPSE, REVISIT_DEATH,
        WHISPER, HOME_INVASION, SLEEP_PARALYSIS, INVENTORY_TAMPER,
        CHASE_SEQUENCE, REALITY_BREAK, IT_KNOWS_YOUR_NAME
    }

    // ════════════════════════════════════════════════════════════════════════
    // NBT PERSISTENCE — save/load between sessions
    // ════════════════════════════════════════════════════════════════════════

    public NbtCompound toNbt() {
        NbtCompound tag = new NbtCompound();
        tag.putFloat("sanity", sanity);
        tag.putInt("totalPlayTicks", totalPlayTicks);
        tag.putInt("sleepAttempts", sleepAttempts);
        tag.putInt("sleepSuccesses", sleepSuccesses);
        tag.putBoolean("avoidsSleep", avoidsSleep);
        tag.putInt("torchesPlaced", torchesPlaced);
        tag.putInt("timeSpentInDark", timeSpentInDark);
        tag.putBoolean("fearsDarkness", fearsDarkness);
        tag.putInt("totalDeaths", totalDeaths);
        tag.putLong("sprintTicks", sprintTicks);
        tag.putLong("sneakTicks", sneakTicks);
        tag.putBoolean("isNervous", isNervous);
        tag.putInt("fleeCount", fleeCount);
        tag.putInt("fightCount", fightCount);
        tag.putBoolean("isCowardly", isCowardly);
        tag.putLong("ticksPlayedAtNight", ticksPlayedAtNight);
        tag.putLong("ticksPlayedAtDay", ticksPlayedAtDay);
        tag.putBoolean("playsAtNight", playsAtNight);
        tag.putInt("horrorEventCount", horrorEventCount);
        tag.putLong("lastHorrorEventTick", lastHorrorEventTick);
        tag.putInt("escalationLevel", escalationLevel);
        tag.putBoolean("watcherSpawned", watcherSpawned);
        tag.putInt("watcherSeenCount", watcherSeenCount);
        tag.putInt("soundReactionCount", soundReactionCount);
        tag.putInt("soundEventCount", soundEventCount);
        tag.putFloat("soundReactivity", soundReactivity);

        if (homeBase != null) {
            NbtCompound home = new NbtCompound();
            home.putInt("x", homeBase.getX());
            home.putInt("y", homeBase.getY());
            home.putInt("z", homeBase.getZ());
            tag.put("homeBase", home);
        }

        NbtList deaths = new NbtList();
        for (int i = 0; i < Math.min(deathLocations.size(), 10); i++) {
            NbtCompound d = new NbtCompound();
            BlockPos p = deathLocations.get(i);
            d.putInt("x", p.getX()); d.putInt("y", p.getY()); d.putInt("z", p.getZ());
            deaths.add(d);
        }
        tag.put("deathLocations", deaths);
        return tag;
    }

    public void fromNbt(NbtCompound tag) {
        sanity = tag.getFloat("sanity");
        totalPlayTicks = tag.getInt("totalPlayTicks");
        sleepAttempts = tag.getInt("sleepAttempts");
        sleepSuccesses = tag.getInt("sleepSuccesses");
        avoidsSleep = tag.getBoolean("avoidsSleep");
        torchesPlaced = tag.getInt("torchesPlaced");
        timeSpentInDark = tag.getInt("timeSpentInDark");
        fearsDarkness = tag.getBoolean("fearsDarkness");
        totalDeaths = tag.getInt("totalDeaths");
        sprintTicks = tag.getLong("sprintTicks");
        sneakTicks = tag.getLong("sneakTicks");
        isNervous = tag.getBoolean("isNervous");
        fleeCount = tag.getInt("fleeCount");
        fightCount = tag.getInt("fightCount");
        isCowardly = tag.getBoolean("isCowardly");
        ticksPlayedAtNight = tag.getLong("ticksPlayedAtNight");
        ticksPlayedAtDay = tag.getLong("ticksPlayedAtDay");
        playsAtNight = tag.getBoolean("playsAtNight");
        horrorEventCount = tag.getInt("horrorEventCount");
        lastHorrorEventTick = tag.getLong("lastHorrorEventTick");
        escalationLevel = tag.getInt("escalationLevel");
        watcherSpawned = tag.getBoolean("watcherSpawned");
        watcherSeenCount = tag.getInt("watcherSeenCount");
        soundReactionCount = tag.getInt("soundReactionCount");
        soundEventCount = tag.getInt("soundEventCount");
        soundReactivity = tag.getFloat("soundReactivity");

        if (tag.contains("homeBase")) {
            NbtCompound home = tag.getCompound("homeBase");
            homeBase = new BlockPos(home.getInt("x"), home.getInt("y"), home.getInt("z"));
        }
        if (tag.contains("deathLocations")) {
            NbtList deaths = tag.getList("deathLocations", NbtList.COMPOUND_TYPE);
            for (int i = 0; i < deaths.size(); i++) {
                NbtCompound d = deaths.getCompound(i);
                deathLocations.add(new BlockPos(d.getInt("x"), d.getInt("y"), d.getInt("z")));
            }
        }
    }
}
