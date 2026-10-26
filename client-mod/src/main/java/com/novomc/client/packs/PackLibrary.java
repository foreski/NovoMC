package com.novomc.client.packs;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.ResourcePackRepository;
import net.minecraft.client.settings.GameSettings;

/**
 * Installs Modrinth packs into the game's resourcepacks folder and turns packs
 * on and off the way the vanilla Resource Packs screen does. Remembers which
 * file belongs to which Modrinth project in config/novomc/packs.json.
 */
public final class PackLibrary {
    /** Install progress for one Modrinth project. */
    public static final class Job {
        public volatile String stage = "resolving";
        public volatile long done;
        public volatile long total;
        public volatile String error;

        public float fraction() {
            return total > 0 ? Math.min(1, done / (float) total) : 0;
        }
    }

    private static final ExecutorService DOWNLOADS = Executors.newFixedThreadPool(2, daemon("NovoMC pack download"));
    private static final Map<String, Job> JOBS = new ConcurrentHashMap<String, Job>();
    /** Modrinth project id -> pack file name. */
    private static final Map<String, String> INSTALLED = new ConcurrentHashMap<String, String>();
    private static boolean loaded;

    private PackLibrary() {}

    static ThreadFactory daemon(final String name) {
        return new ThreadFactory() {
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, name);
                t.setDaemon(true);
                return t;
            }
        };
    }

    private static Minecraft mc() {
        return Minecraft.getMinecraft();
    }

    private static File dir() {
        return mc().getResourcePackRepository().getDirResourcepacks();
    }

    private static File indexFile() {
        return new File(mc().mcDataDir, "config/novomc/packs.json");
    }

    // ---- Queries ----

    public static Job job(String projectId) {
        return JOBS.get(projectId);
    }

    /** The installed pack file for a Modrinth project, or null. */
    public static String installedFile(String projectId) {
        load();
        String name = INSTALLED.get(projectId);
        if (name == null) {
            return null;
        }
        // The repository's list of the folder (refreshed on screen open and after changes), not a disk check per frame.
        for (ResourcePackRepository.Entry e : mc().getResourcePackRepository().getRepositoryEntriesAll()) {
            if (e.getResourcePackName().equals(name)) {
                return name;
            }
        }
        return null;
    }

    /** Re-reads the resourcepacks folder. */
    public static void rescan() {
        mc().getResourcePackRepository().updateRepositoryEntriesAll();
    }

    public static boolean isActive(String fileName) {
        for (ResourcePackRepository.Entry e : mc().getResourcePackRepository().getRepositoryEntries()) {
            if (e.getResourcePackName().equals(fileName)) {
                return true;
            }
        }
        return false;
    }

    /** Every pack in the resourcepacks folder, active ones first (top priority first). */
    public static List<ResourcePackRepository.Entry> local() {
        ResourcePackRepository repo = mc().getResourcePackRepository();
        repo.updateRepositoryEntriesAll();
        List<ResourcePackRepository.Entry> active = new ArrayList<ResourcePackRepository.Entry>(repo.getRepositoryEntries());
        java.util.Collections.reverse(active);
        List<ResourcePackRepository.Entry> out = new ArrayList<ResourcePackRepository.Entry>(active);
        for (ResourcePackRepository.Entry e : repo.getRepositoryEntriesAll()) {
            if (!out.contains(e)) {
                out.add(e);
            }
        }
        return out;
    }

    // ---- Actions ----

    /** Downloads a pack (in the background), then turns it on above the others. */
    public static void install(final Modrinth.Hit hit) {
        if (JOBS.containsKey(hit.id) && JOBS.get(hit.id).error == null) {
            return;
        }
        final Job job = new Job();
        JOBS.put(hit.id, job);
        DOWNLOADS.submit(new Runnable() {
            public void run() {
                try {
                    Modrinth.PackFile file = Modrinth.latestFile(hit.id);
                    job.total = file.size;
                    job.stage = "downloading";
                    final File dest = new File(dir(), safeName(file.filename));
                    dir().mkdirs();
                    Modrinth.download(file, dest, new Modrinth.Progress() {
                        public void update(long done, long total) {
                            job.done = done;
                            job.total = total;
                        }
                    });
                    job.stage = "applying";
                    mc().addScheduledTask(new Runnable() {
                        public void run() {
                            INSTALLED.put(hit.id, dest.getName());
                            save();
                            setActive(dest.getName(), true);
                            JOBS.remove(hit.id);
                        }
                    });
                } catch (Exception e) {
                    job.error = e.getMessage() != null ? e.getMessage() : e.toString();
                    job.stage = "failed";
                }
            }
        });
    }

    public static void forget(String projectId) {
        JOBS.remove(projectId);
    }

    /** Turns a pack on (on top) or off, then reloads resources. */
    public static void setActive(String fileName, boolean on) {
        ResourcePackRepository repo = mc().getResourcePackRepository();
        repo.updateRepositoryEntriesAll();
        // Lowest priority first, like the repository keeps them.
        List<ResourcePackRepository.Entry> list = new ArrayList<ResourcePackRepository.Entry>(repo.getRepositoryEntries());
        ResourcePackRepository.Entry target = null;
        for (ResourcePackRepository.Entry e : repo.getRepositoryEntriesAll()) {
            if (e.getResourcePackName().equals(fileName)) {
                target = e;
            }
        }
        if (target == null) {
            return;
        }
        list.remove(target);
        if (on) {
            list.add(target);
        }
        repo.setRepositories(list);
        GameSettings gs = mc().gameSettings;
        gs.resourcePacks.clear();
        gs.incompatibleResourcePacks.clear();
        for (ResourcePackRepository.Entry e : repo.getRepositoryEntries()) {
            gs.resourcePacks.add(e.getResourcePackName());
            // Packs made for newer versions still load; this stops the vanilla screen nagging.
            if (e.func_183027_f() != 1) {
                gs.incompatibleResourcePacks.add(e.getResourcePackName());
            }
        }
        gs.saveOptions();
        mc().refreshResources();
    }

    // ---- Index ----

    private static synchronized void load() {
        if (loaded) {
            return;
        }
        loaded = true;
        File f = indexFile();
        if (!f.exists()) {
            return;
        }
        try {
            JsonObject o = new JsonParser().parse(new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8)).getAsJsonObject();
            for (Map.Entry<String, JsonElement> e : o.entrySet()) {
                INSTALLED.put(e.getKey(), e.getValue().getAsString());
            }
        } catch (Exception e) {
            System.err.println("[NovoMC] Couldn't read " + f + ": " + e);
        }
    }

    private static synchronized void save() {
        JsonObject o = new JsonObject();
        for (Map.Entry<String, String> e : INSTALLED.entrySet()) {
            o.addProperty(e.getKey(), e.getValue());
        }
        File f = indexFile();
        f.getParentFile().mkdirs();
        try {
            Files.write(f.toPath(), new GsonBuilder().setPrettyPrinting().create().toJson(o).getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            System.err.println("[NovoMC] Couldn't save " + f + ": " + e);
        }
    }

    /** A file name that is safe on every OS. */
    private static String safeName(String name) {
        String s = name.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
        return s.isEmpty() ? "pack.zip" : s;
    }
}
