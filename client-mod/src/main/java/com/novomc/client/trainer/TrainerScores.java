package com.novomc.client.trainer;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.File;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import net.minecraft.client.Minecraft;

/** Personal bests: {@code config/novomc/trainers.json}, drill id to best score. */
public final class TrainerScores {
    private static final File FILE = new File(Minecraft.getMinecraft().mcDataDir, "config/novomc/trainers.json");
    private static JsonObject bests;

    private TrainerScores() {}

    /** The best score so far, or null. */
    public static Float best(String drill) {
        JsonElement e = all().get(drill);
        return e != null && e.isJsonPrimitive() ? e.getAsFloat() : null;
    }

    /** Records a score; true if it's a new personal best. */
    public static boolean record(String drill, float score, boolean lowerIsBetter) {
        Float best = best(drill);
        boolean better = best == null || (lowerIsBetter ? score < best : score > best);
        if (better) {
            all().addProperty(drill, score);
            save();
        }
        return better;
    }

    private static JsonObject all() {
        if (bests == null) {
            bests = new JsonObject();
            if (FILE.isFile()) {
                try (Reader r = new InputStreamReader(Files.newInputStream(FILE.toPath()), StandardCharsets.UTF_8)) {
                    JsonElement parsed = new JsonParser().parse(r);
                    if (parsed.isJsonObject()) {
                        bests = parsed.getAsJsonObject();
                    }
                } catch (Exception e) {
                    System.err.println("[NovoMC] Couldn't read " + FILE + ": " + e);
                }
            }
        }
        return bests;
    }

    private static void save() {
        try {
            FILE.getParentFile().mkdirs();
            Files.write(FILE.toPath(), new GsonBuilder().setPrettyPrinting().create().toJson(all()).getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            System.err.println("[NovoMC] Couldn't save " + FILE + ": " + e);
        }
    }
}
