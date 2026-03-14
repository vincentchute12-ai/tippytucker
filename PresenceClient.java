package com.presence.mod;

import com.presence.mod.config.ConfigCommand;
import com.presence.mod.config.PresenceConfig;
import com.presence.mod.event.EntityChatHandler;
import com.presence.mod.event.ServerEventHandler;
import com.presence.mod.network.PacketHandler;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PresenceMod implements ModInitializer {

    public static final String MOD_ID = "presence";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        LOGGER.info("Presence awakens...");
        // Load config first — all systems read from it
        PresenceConfig cfg = PresenceConfig.get();
        LOGGER.info("[Presence] Config loaded. Difficulty: {}x, Enabled: {}",
            cfg.difficultyMultiplier, cfg.enabled);
        PacketHandler.registerPackets();
        ServerEventHandler.register();
        EntityChatHandler.register();
        ConfigCommand.register();
    }
}
