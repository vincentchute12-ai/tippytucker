package com.presence.mod.data;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateManager;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ProfileManager extends PersistentState {

    private static final String KEY = "presence_profiles";
    private final Map<UUID, PlayerProfile> profiles = new HashMap<>();

    public static ProfileManager get(MinecraftServer server) {
        PersistentStateManager manager = server.getOverworld().getPersistentStateManager();
        return manager.getOrCreate(
            nbt -> {
                ProfileManager mgr = new ProfileManager();
                mgr.readNbt(nbt);
                return mgr;
            },
            ProfileManager::new,
            KEY
        );
    }

    public PlayerProfile getProfile(UUID uuid) {
        return profiles.computeIfAbsent(uuid, id -> new PlayerProfile());
    }

    public void readNbt(NbtCompound nbt) {
        if (nbt.contains("profiles")) {
            NbtCompound all = nbt.getCompound("profiles");
            for (String key : all.getKeys()) {
                UUID uuid = UUID.fromString(key);
                PlayerProfile profile = new PlayerProfile();
                profile.fromNbt(all.getCompound(key));
                profiles.put(uuid, profile);
            }
        }
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt) {
        NbtCompound all = new NbtCompound();
        profiles.forEach((uuid, profile) -> all.put(uuid.toString(), profile.toNbt()));
        nbt.put("profiles", all);
        return nbt;
    }
}
