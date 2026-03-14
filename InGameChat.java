package com.presence.mod.event;

import com.presence.mod.data.PlayerProfile;
import com.presence.mod.data.ProfileManager;
import com.presence.mod.network.PacketHandler;
import com.presence.mod.system.HorrorDirector;
import com.presence.mod.system.MultiplayerHorrorSystem;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import com.presence.mod.config.PresenceConfig;
import com.presence.mod.world.WorldHorrorDirector;
import net.minecraft.item.Items;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;

public class ServerEventHandler {

    public static void register() {

        ServerTickEvents.END_SERVER_TICK.register((MinecraftServer server) -> {
            ProfileManager mgr = ProfileManager.get(server);
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                PlayerProfile profile = mgr.getProfile(player.getUuid());
                HorrorDirector.tick(player, profile);
                WorldHorrorDirector.tick(player, profile);
                if (server.getTicks() % 20 == 0)
                    PacketHandler.sendSanityUpdate(player, profile.sanity, profile.escalationLevel);
            }
            // Multiplayer horror runs separately, coordinating between players
            MultiplayerHorrorSystem.tick(server, mgr);
            if (server.getTicks() % 100 == 0) mgr.markDirty();
        });

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ProfileManager mgr = ProfileManager.get(server);
            PlayerProfile profile = mgr.getProfile(handler.player.getUuid());
            profile.sanity = Math.min(100, profile.sanity + PresenceConfig.get().sanityRecoveryOnLogin);
            mgr.markDirty();
        });

        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (!(entity instanceof ServerPlayerEntity player)) return;
            MinecraftServer server = player.getServer();
            if (server == null) return;
            ProfileManager mgr = ProfileManager.get(server);
            PlayerProfile profile = mgr.getProfile(player.getUuid());
            profile.totalDeaths++;
            BlockPos pos = player.getBlockPos();
            if (profile.deathLocations.size() < 10) profile.deathLocations.add(pos);
            else profile.deathLocations.set(0, pos);
            profile.deathCauses.add(source.getType().msgId());
            profile.sanity = Math.max(0, profile.sanity - PresenceConfig.get().sanityPenaltyDeath);
            mgr.markDirty();
        });

        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (!world.isClient && player instanceof ServerPlayerEntity sp) {
                var stack = player.getStackInHand(hand);
                if (stack.getItem() == Items.TORCH || stack.getItem() == Items.WALL_TORCH) {
                    MinecraftServer server = sp.getServer();
                    if (server != null)
                        ProfileManager.get(server).getProfile(player.getUuid()).torchesPlaced++;
                }
            }
            return ActionResult.PASS;
        });

        // Sleep and flee tracking
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            ProfileManager mgr = ProfileManager.get(server);
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                PlayerProfile profile = mgr.getProfile(player.getUuid());
                if (player.isSleeping()) {
                    profile.homeBase = player.getBlockPos();
                    profile.sanity = Math.min(100, profile.sanity + 0.005f * PresenceConfig.get().difficultyMultiplier);
                }
                if (player.hurtTime > 0) {
                    if (player.isSprinting()) profile.fleeCount++;
                    else profile.fightCount++;
                }
            }
        });
    }
}
