package com.presence.mod.world;

import com.presence.mod.data.PlayerProfile;
import com.presence.mod.network.PacketHandler;
import com.presence.mod.network.PacketHandler.EventType;
import com.presence.mod.network.PacketHandler.HorrorEventPayload;
import net.minecraft.block.*;
import net.minecraft.block.entity.SignBlockEntity;
import net.minecraft.block.entity.SignText;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import java.util.Random;

/**
 * PRESENCE — WorldHorrorBuilder
 *
 * Places physical structures in the world to scare players.
 * All placements are:
 *   • Only in air/replaceable blocks — never destroys the player's builds
 *   • Temporary where appropriate (signs removed after a delay)
 *   • Logged to the profile so they can be revisited
 *
 * Structures:
 *   SIGN          — A standing sign with a message near the player
 *   CROSS         — A cross shape built from soul sand / netherrack
 *   GRAVE         — A dirt mound with a sign naming the player
 *   RING          — A ring of candles or soul fire on the ground
 *   TRAIL         — A path of gravel leading somewhere (or nowhere)
 *   OBSIDIAN_MARK — A single obsidian block placed near a death spot
 */
public class WorldHorrorBuilder {

    private static final Random RAND = new Random();

    public enum StructureType {
        SIGN, CROSS, GRAVE, RING, TRAIL, OBSIDIAN_MARK
    }

    // ════════════════════════════════════════════════════════════════════════
    // Main dispatch
    // ════════════════════════════════════════════════════════════════════════

    public static void build(ServerPlayerEntity player, PlayerProfile profile,
                             StructureType type) {
        ServerWorld world = (ServerWorld) player.getWorld();
        BlockPos center = findSafeGround(world, player.getBlockPos());
        if (center == null) return;

        switch (type) {
            case SIGN          -> placeSign(world, player, profile, center);
            case CROSS         -> placeCross(world, center);
            case GRAVE         -> placeGrave(world, player, center);
            case RING          -> placeRing(world, center);
            case TRAIL         -> placeTrail(world, player, center);
            case OBSIDIAN_MARK -> placeObsidianMark(world, profile, center);
        }

        // Tell the client to play a sound
        PacketHandler.sendHorrorEvent(player, new HorrorEventPayload(
            EventType.PLAY_SOUND, "ambient.block_place", null, 0));
    }

    // ════════════════════════════════════════════════════════════════════════
    // SIGN — appears nearby with a message directed at the player
    // ════════════════════════════════════════════════════════════════════════

    private static void placeSign(ServerWorld world, ServerPlayerEntity player,
                                   PlayerProfile profile, BlockPos pos) {
        String name = player.getName().getString();
        int escalation = profile.escalationLevel;

        String[][] messagesByEscalation = {
            // Level 0
            { "i was here", "", "before you", "" },
            // Level 1
            { "turn around", name, "", "" },
            // Level 2
            { "i know where", "you sleep", name, "" },
            // Level 3
            { "your deaths:", String.valueOf(profile.totalDeaths), "mean nothing", "to me" },
            // Level 4
            { "last warning", name, "leave.", "or don't." },
        };

        String[] lines = messagesByEscalation[Math.min(escalation, 4)];

        BlockPos signPos = pos.up();
        if (!isReplaceable(world, signPos)) return;

        world.setBlockState(signPos, Blocks.OAK_SIGN.getDefaultState()
            .with(SignBlock.ROTATION, RAND.nextInt(16)));

        if (world.getBlockEntity(signPos) instanceof SignBlockEntity sign) {
            sign.setText(new SignText(
                new Text[]{ Text.literal(lines[0]), Text.literal(lines[1]),
                            Text.literal(lines[2]), Text.literal(lines[3]) },
                new Text[]{ Text.literal(""), Text.literal(""),
                            Text.literal(""), Text.literal("") },
                0xAAAAAA, false
            ), true);
        }

        // Schedule removal after 2 minutes — it disappears on its own
        world.getServer().execute(() -> scheduleRemoval(world, signPos, 2400));
    }

    // ════════════════════════════════════════════════════════════════════════
    // CROSS — a cross shape built from netherrack with soul fire on top
    // ════════════════════════════════════════════════════════════════════════

    private static void placeCross(ServerWorld world, BlockPos center) {
        // Vertical beam
        for (int dy = 0; dy <= 4; dy++) {
            BlockPos p = center.up(dy);
            if (isReplaceable(world, p))
                world.setBlockState(p, Blocks.NETHERRACK.getDefaultState());
        }
        // Horizontal crossbar at dy=3
        for (int dx = -2; dx <= 2; dx++) {
            if (dx == 0) continue;
            BlockPos p = center.up(3).east(dx);
            if (isReplaceable(world, p))
                world.setBlockState(p, Blocks.NETHERRACK.getDefaultState());
        }
        // Soul fire on top
        BlockPos top = center.up(5);
        if (isReplaceable(world, top))
            world.setBlockState(top, Blocks.SOUL_FIRE.getDefaultState());
    }

    // ════════════════════════════════════════════════════════════════════════
    // GRAVE — a small dirt mound with a sign bearing the player's name
    // ════════════════════════════════════════════════════════════════════════

    private static void placeGrave(ServerWorld world, ServerPlayerEntity player,
                                    BlockPos center) {
        String name = player.getName().getString();

        // Dirt mound (3x1x3 raised area)
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                BlockPos p = center.add(dx, 0, dz);
                if (isReplaceable(world, p))
                    world.setBlockState(p, Blocks.DIRT.getDefaultState());
            }
        }
        // Centre raised block
        BlockPos peak = center.up(1);
        if (isReplaceable(world, peak))
            world.setBlockState(peak, Blocks.DIRT.getDefaultState());

        // Sign headstone
        BlockPos headstone = center.north(2);
        if (isReplaceable(world, headstone)) {
            world.setBlockState(headstone, Blocks.OAK_SIGN.getDefaultState()
                .with(SignBlock.ROTATION, 8)); // facing south (toward grave)
            if (world.getBlockEntity(headstone) instanceof SignBlockEntity sign) {
                sign.setText(new SignText(
                    new Text[]{
                        Text.literal("here lies"),
                        Text.literal(name),
                        Text.literal(""),
                        Text.literal("it was inevitable")
                    },
                    new Text[]{ Text.literal(""), Text.literal(""),
                                Text.literal(""), Text.literal("") },
                    0x888888, false
                ), true);
            }
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // RING — a circle of soul sand with candles or soul fire torches
    // ════════════════════════════════════════════════════════════════════════

    private static void placeRing(ServerWorld world, BlockPos center) {
        int radius = 4;
        for (int angle = 0; angle < 360; angle += 30) {
            double rad = Math.toRadians(angle);
            int dx = (int) Math.round(Math.sin(rad) * radius);
            int dz = (int) Math.round(Math.cos(rad) * radius);
            BlockPos pos = center.add(dx, 0, dz);

            if (isReplaceable(world, pos)) {
                world.setBlockState(pos, Blocks.SOUL_SAND.getDefaultState());
                BlockPos above = pos.up();
                if (isReplaceable(world, above)) {
                    // Alternate candles and soul fire torches
                    if (angle % 60 == 0) {
                        world.setBlockState(above, Blocks.SOUL_FIRE.getDefaultState());
                    } else {
                        world.setBlockState(above,
                            Blocks.CANDLE.getDefaultState()
                                .with(CandleBlock.LIT, true));
                    }
                }
            }
        }

        // Single soul sand in the middle with a sign
        if (isReplaceable(world, center))
            world.setBlockState(center, Blocks.SOUL_SAND.getDefaultState());
    }

    // ════════════════════════════════════════════════════════════════════════
    // TRAIL — a path of gravel/soul sand leading away from the player
    // ════════════════════════════════════════════════════════════════════════

    private static void placeTrail(ServerWorld world, ServerPlayerEntity player,
                                    BlockPos start) {
        // Trail leads in the direction the player is NOT facing
        float yaw = player.getYaw() + 180;
        double angle = Math.toRadians(yaw);
        int length = 12 + RAND.nextInt(8);

        for (int i = 1; i <= length; i++) {
            int dx = (int) Math.round(Math.sin(angle) * i);
            int dz = (int) Math.round(Math.cos(angle) * i);
            BlockPos trailPos = findSafeGround(world, start.add(dx, 0, dz));
            if (trailPos == null) continue;

            Block trailBlock = (i % 3 == 0) ? Blocks.SOUL_SAND : Blocks.GRAVEL;
            if (isReplaceable(world, trailPos))
                world.setBlockState(trailPos, trailBlock.getDefaultState());

            // Place a sign at the end of the trail
            if (i == length) {
                BlockPos signPos = trailPos.up();
                if (isReplaceable(world, signPos)) {
                    world.setBlockState(signPos, Blocks.OAK_SIGN.getDefaultState()
                        .with(SignBlock.ROTATION, RAND.nextInt(16)));
                    if (world.getBlockEntity(signPos) instanceof SignBlockEntity sign) {
                        sign.setText(new SignText(
                            new Text[]{
                                Text.literal("you followed"),
                                Text.literal("it"),
                                Text.literal(""),
                                Text.literal("good.")
                            },
                            new Text[]{ Text.literal(""), Text.literal(""),
                                        Text.literal(""), Text.literal("") },
                            0x888888, false
                        ), true);
                    }
                }
            }
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // OBSIDIAN MARK — placed at a previous death location
    // ════════════════════════════════════════════════════════════════════════

    private static void placeObsidianMark(ServerWorld world, PlayerProfile profile,
                                           BlockPos fallback) {
        BlockPos target = fallback;
        if (!profile.deathLocations.isEmpty()) {
            target = profile.deathLocations.get(RAND.nextInt(profile.deathLocations.size()));
        }

        // Single obsidian block at the death spot, with a sign
        BlockPos markPos = findSafeGround(world, target);
        if (markPos == null) return;

        if (isReplaceable(world, markPos))
            world.setBlockState(markPos, Blocks.OBSIDIAN.getDefaultState());

        BlockPos signPos = markPos.up();
        if (isReplaceable(world, signPos)) {
            world.setBlockState(signPos, Blocks.OAK_SIGN.getDefaultState()
                .with(SignBlock.ROTATION, RAND.nextInt(16)));
            if (world.getBlockEntity(signPos) instanceof SignBlockEntity sign) {
                sign.setText(new SignText(
                    new Text[]{
                        Text.literal("you died here"),
                        Text.literal("i watched"),
                        Text.literal(""),
                        Text.literal("i remember")
                    },
                    new Text[]{ Text.literal(""), Text.literal(""),
                                Text.literal(""), Text.literal("") },
                    0x660000, false
                ), true);
            }
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // Helpers
    // ════════════════════════════════════════════════════════════════════════

    private static boolean isReplaceable(ServerWorld world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        return state.isAir() || state.isReplaceable()
            || state.getBlock() == Blocks.GRASS
            || state.getBlock() == Blocks.TALL_GRASS
            || state.getBlock() == Blocks.FERN;
    }

    /** Find the ground level at or near a position. Returns null if none found. */
    private static BlockPos findSafeGround(ServerWorld world, BlockPos near) {
        // Search offset from the player — not right on top of them
        int ox = RAND.nextInt(10) - 5;
        int oz = RAND.nextInt(10) - 5;
        BlockPos search = near.add(ox, 0, oz);

        for (int dy = 5; dy >= -10; dy--) {
            BlockPos check = search.add(0, dy, 0);
            BlockPos above = check.up();
            BlockState state = world.getBlockState(check);
            boolean solid = !state.isAir() && !state.isReplaceable();
            boolean aboveAir = isReplaceable(world, above);
            if (solid && aboveAir) return above;
        }
        return null;
    }

    /** Schedules a block removal after a delay (in ticks). Simple approach. */
    private static void scheduleRemoval(ServerWorld world, BlockPos pos, int delayTicks) {
        new Thread(() -> {
            try { Thread.sleep((delayTicks / 20L) * 1000L); }
            catch (InterruptedException ignored) {}
            world.getServer().execute(() -> {
                if (world.getBlockState(pos).getBlock() == Blocks.OAK_SIGN) {
                    world.removeBlock(pos, false);
                }
            });
        }).start();
    }
}
