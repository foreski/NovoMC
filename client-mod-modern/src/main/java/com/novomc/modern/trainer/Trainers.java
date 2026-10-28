package com.novomc.modern.trainer;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.InputConstants;
import com.novomc.modern.NovoModern;
import com.novomc.modern.Tweaks;
import com.novomc.modern.ui.NovoTrainers;
import com.novomc.modern.ui.NovoUi;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.GenericMessageScreen;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;

/**
 * Novo Client's trainers on modern Minecraft: drills in a private flat world
 * ({@code saves/novo-trainer}). Starting a drill opens (or creates) that world,
 * builds the arena and runs it; personal bests go to the same
 * {@code config/novomc/trainers.json} as on 1.8.9. In the trainer world, R
 * restarts the drill and G picks another.
 */
public final class Trainers {
    public static final String FOLDER = "novo-trainer";
    private static final String LEVEL_NAME = "Novo Trainer";
    private static final int COUNTDOWN_TICKS = 60;
    private static final KeyMapping RESTART = KeyMappingHelper.registerKeyMapping(
            new KeyMapping("key.novomc.trainer_restart", InputConstants.Type.KEYBOARD, InputConstants.KEY_R, Tweaks.CATEGORY));
    private static final KeyMapping MENU = KeyMappingHelper.registerKeyMapping(
            new KeyMapping("key.novomc.trainers", InputConstants.Type.KEYBOARD, InputConstants.KEY_G, Tweaks.CATEGORY));

    private enum State { IDLE, COUNTDOWN, RUNNING, RESULTS }

    private static final Minecraft mc = Minecraft.getInstance();
    private static Drill active;
    private static State state = State.IDLE;
    private static String pendingId;
    private static int countdown;
    private static int settle;
    private static float lastScore;
    private static boolean newBest;
    private static JsonObject bests;

    private Trainers() {}

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> tick());
        AttackEntityCallback.EVENT.register((player, level, hand, target, hit) -> {
            if (level.isClientSide() && player == mc.player && state == State.RUNNING && active != null) {
                active.attacked(target);
            }
            return InteractionResult.PASS;
        });
    }

    public static boolean inTrainerWorld() {
        IntegratedServer server = mc.getSingleplayerServer();
        return mc.level != null && server != null && LEVEL_NAME.equals(server.getWorldData().getLevelName());
    }

    /** Starts a drill, opening the trainer world first if needed. */
    public static void start(String id) {
        pendingId = id;
        state = State.IDLE;
        active = null;
        if (inTrainerWorld()) {
            mc.gui.setScreen(null);
            return;
        }
        if (mc.level != null) {
            mc.disconnect(new GenericMessageScreen(Component.literal("Opening the trainer...")), false);
        }
        if (mc.getLevelSource().levelExists(FOLDER)) {
            mc.createWorldOpenFlows().openWorld(FOLDER, () -> mc.gui.setScreen(new NovoTrainers(null)));
        } else {
            LevelSettings settings = new LevelSettings(LEVEL_NAME, GameType.SURVIVAL,
                    new LevelSettings.DifficultySettings(Difficulty.EASY, false, false), true, WorldDataConfiguration.DEFAULT);
            mc.createWorldOpenFlows().createFreshLevel(FOLDER, settings, new WorldOptions(0L, false, false),
                    WorldPresets::createTestWorldDimensions, null);
        }
    }

    private static void begin(String id) {
        active = Drills.create(id);
        // Leftover targets and drops, then a clean arena, full health and a fresh kit.
        List<String> reset = List.of("kill @e[type=!minecraft:player]", "fill -12 95 -12 12 112 31 minecraft:air", "clear @s",
                "gamemode survival", "time set noon", "weather clear", "effect clear @s",
                "effect give @s minecraft:resistance infinite 4 true", "effect give @s minecraft:instant_health 1 10 true",
                "effect give @s minecraft:saturation 1 10 true");
        run(reset);
        run(active.setup());
        countdown = COUNTDOWN_TICKS;
        settle = 10;
        state = active.countdown() ? State.COUNTDOWN : State.RUNNING;
    }

    /** Runs commands as the player on the integrated server, without chat feedback. */
    static void run(List<String> commands) {
        IntegratedServer server = mc.getSingleplayerServer();
        java.util.UUID uuid = mc.player.getUUID();
        server.execute(() -> {
            var player = server.getPlayerList().getPlayer(uuid);
            if (player != null) {
                for (String c : commands) {
                    server.getCommands().performPrefixedCommand(player.createCommandSourceStack().withSuppressedOutput(), c);
                }
            }
        });
    }

    private static void finish() {
        lastScore = active.score();
        Float best = best(active.id);
        newBest = best == null || (active.lowerIsBetter ? lastScore < best : lastScore > best);
        if (newBest) {
            all().addProperty(active.id, lastScore);
            save();
        }
        state = State.RESULTS;
    }

    private static void tick() {
        if (!inTrainerWorld()) {
            if (mc.level == null && pendingId == null) {
                state = State.IDLE;
                active = null;
            }
            return;
        }
        if (pendingId != null && mc.player != null) {
            String id = pendingId;
            pendingId = null;
            begin(id);
            return;
        }
        while (RESTART.consumeClick()) {
            if (active != null) {
                begin(active.id);
                return;
            }
        }
        while (MENU.consumeClick()) {
            mc.gui.setScreen(new NovoTrainers(null));
        }
        if (state == State.COUNTDOWN && --countdown <= 0) {
            state = State.RUNNING;
        } else if (state == State.RUNNING && --settle < 0 && active.tick()) {
            finish();
        }
    }

    /** The drill HUD: name and live numbers, the countdown, then the results card. */
    public static void render(GuiGraphicsExtractor g) {
        if (active == null || state == State.IDLE || !inTrainerWorld()) {
            return;
        }
        int cx = g.guiWidth() / 2;
        if (state == State.RESULTS) {
            List<String> lines = new ArrayList<String>(active.details());
            int h = 64 + lines.size() * 11;
            int top = g.guiHeight() / 2 - h / 2 - 20;
            NovoUi.round(g, cx - 110, top, cx + 110, top + h, 0xE00C1019);
            NovoUi.roundOutline(g, cx - 110, top, cx + 110, top + h, newBest ? 0xCCFACC15 : 0x665B8CFF);
            NovoUi.centered(g, NovoUi.text(active.name.toUpperCase()), cx, top + 8, NovoUi.CYAN);
            g.pose().pushMatrix();
            g.pose().translate(cx, top + 20);
            g.pose().scale(1.5f, 1.5f);
            NovoUi.centered(g, NovoUi.bold(active.format(lastScore)), 0, 0, NovoUi.TEXT);
            g.pose().popMatrix();
            Float best = best(active.id);
            String pb = newBest ? "New personal best!" : "Best: " + (best == null ? "-" : active.format(best));
            NovoUi.centered(g, NovoUi.text(pb), cx, top + 36, newBest ? 0xFFFACC15 : NovoUi.MUTED);
            int y = top + 50;
            for (String l : lines) {
                NovoUi.centered(g, NovoUi.text(l), cx, y, 0xFFC9D1E0);
                y += 11;
            }
            NovoUi.centered(g, NovoUi.text(key(RESTART) + " to retry  ·  " + key(MENU) + " for drills"), cx, top + h - 12, NovoUi.MUTED);
            return;
        }
        List<String> lines = active.status();
        int w = 150;
        // Below the top-centre row (compass, profile toast).
        int top = 26;
        NovoUi.round(g, cx - w / 2, top, cx + w / 2, top + 14 + lines.size() * 11, 0xC00C1019);
        NovoUi.centered(g, NovoUi.bold(active.name), cx, top + 4, NovoUi.TEXT);
        int y = top + 15;
        for (String l : lines) {
            NovoUi.centered(g, NovoUi.text(l), cx, y, NovoUi.MUTED);
            y += 11;
        }
        if (state == State.COUNTDOWN) {
            g.pose().pushMatrix();
            g.pose().translate(cx, g.guiHeight() / 2f - 30);
            g.pose().scale(3, 3);
            NovoUi.centered(g, NovoUi.bold(String.valueOf((countdown + 19) / 20)), 0, 0, 0xFFFFFFFF);
            g.pose().popMatrix();
        }
    }

    private static String key(KeyMapping k) {
        return k.getTranslatedKeyMessage().getString();
    }

    // ---- Personal bests (shared with 1.8.9's trainers.json) ----

    public static Float best(String drill) {
        JsonElement e = all().get(drill);
        return e != null && e.isJsonPrimitive() ? e.getAsFloat() : null;
    }

    private static File file() {
        return new File(NovoModern.gameDir(), "config/novomc/trainers.json");
    }

    private static JsonObject all() {
        if (bests == null) {
            bests = new JsonObject();
            File f = file();
            if (f.isFile()) {
                try {
                    JsonElement parsed = JsonParser.parseString(Files.readString(f.toPath(), StandardCharsets.UTF_8));
                    if (parsed.isJsonObject()) {
                        bests = parsed.getAsJsonObject();
                    }
                } catch (Exception e) {
                    System.err.println("[NovoMC] Couldn't read " + f + ": " + e);
                }
            }
        }
        return bests;
    }

    private static void save() {
        File f = file();
        try {
            f.getParentFile().mkdirs();
            Files.writeString(f.toPath(), new GsonBuilder().setPrettyPrinting().create().toJson(all()), StandardCharsets.UTF_8);
        } catch (Exception e) {
            System.err.println("[NovoMC] Couldn't save " + f + ": " + e);
        }
    }
}
