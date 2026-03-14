{
  "schemaVersion": 1,
  "id": "presence",
  "version": "${version}",
  "name": "Presence",
  "description": "It knows you're here. It learns. It waits. It follows.\nA psychological horror experience that adapts to YOU.",
  "authors": ["PresenceMod"],
  "contact": {
    "homepage": "https://www.curseforge.com/minecraft/mc-mods/presence",
    "issues": "https://github.com/presence-mod/issues"
  },
  "license": "MIT",
  "icon": "assets/presence/icon.png",
  "environment": "*",
  "entrypoints": {
    "main": ["com.presence.mod.PresenceMod"],
    "client": ["com.presence.mod.client.PresenceClient"],
    "modmenu": ["com.presence.mod.client.ModMenuIntegration"]
  },
  "mixins": [
    "presence.mixins.json",
    {
      "config": "presence.client.mixins.json",
      "environment": "client"
    }
  ],
  "depends": {
    "fabricloader": ">=0.15.0",
    "fabric-api": "*",
    "minecraft": "~1.21.1"
  },
  "suggests": {
    "modmenu": ">=11.0.0"
  }
}
