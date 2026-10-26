package com.novomc.client.packs;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.novomc.client.NovoMC;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Minimal client for Modrinth's public API (https://docs.modrinth.com/api/):
 * searching resource packs that support 1.8.x and downloading their files
 * from Modrinth's CDN. All calls block; run them off the render thread.
 */
public final class Modrinth {
    private static final String API = "https://api.modrinth.com/v2/";
    private static final String USER_AGENT = "NovoMC-Client/" + NovoMC.VERSION + " (Minecraft 1.8.9 client)";
    /** Game versions whose packs work on 1.8.9 (same pack format). */
    private static final String[] GAME_VERSIONS = {"1.8.9", "1.8.8", "1.8"};

    /** One search result. */
    public static final class Hit {
        public String id;
        public String slug;
        public String title;
        public String author;
        public String description;
        public long downloads;
        public String iconUrl;
        public List<String> categories = new ArrayList<String>();
    }

    public static final class Page {
        public int total;
        public List<Hit> hits = new ArrayList<Hit>();
    }

    /** The file to download for a pack. */
    public static final class PackFile {
        public String url;
        public String filename;
        public String sha1;
        public long size;
    }

    public interface Progress {
        void update(long done, long total);
    }

    private Modrinth() {}

    /**
     * Resource packs for 1.8.x. `sort` is a Modrinth index: relevance,
     * downloads, follows, newest or updated. `category` may be null.
     */
    public static Page search(String query, String category, String sort, int offset, int limit) throws IOException {
        StringBuilder facets = new StringBuilder("[[\"project_type:resourcepack\"],[");
        for (int i = 0; i < GAME_VERSIONS.length; i++) {
            facets.append(i > 0 ? "," : "").append("\"versions:").append(GAME_VERSIONS[i]).append('"');
        }
        facets.append(']');
        if (category != null) {
            facets.append(",[\"categories:").append(category).append("\"]");
        }
        facets.append(']');
        String url = API + "search?limit=" + limit + "&offset=" + offset + "&index=" + sort
                + "&facets=" + enc(facets.toString())
                + (query.isEmpty() ? "" : "&query=" + enc(query));
        JsonObject root = json(url).getAsJsonObject();
        Page page = new Page();
        page.total = root.get("total_hits").getAsInt();
        for (JsonElement e : root.getAsJsonArray("hits")) {
            JsonObject o = e.getAsJsonObject();
            Hit h = new Hit();
            h.id = o.get("project_id").getAsString();
            h.slug = str(o, "slug");
            h.title = str(o, "title");
            h.author = str(o, "author");
            h.description = str(o, "description");
            h.downloads = o.has("downloads") ? o.get("downloads").getAsLong() : 0;
            h.iconUrl = str(o, "icon_url");
            if (o.has("categories")) {
                for (JsonElement c : o.getAsJsonArray("categories")) {
                    h.categories.add(c.getAsString());
                }
            }
            page.hits.add(h);
        }
        return page;
    }

    /** The newest file of a pack that supports 1.8.x. */
    public static PackFile latestFile(String projectId) throws IOException {
        StringBuilder versions = new StringBuilder("[");
        for (int i = 0; i < GAME_VERSIONS.length; i++) {
            versions.append(i > 0 ? "," : "").append('"').append(GAME_VERSIONS[i]).append('"');
        }
        versions.append(']');
        JsonArray list = json(API + "project/" + enc(projectId) + "/version?game_versions=" + enc(versions.toString())).getAsJsonArray();
        if (list.size() == 0) {
            throw new IOException("No 1.8.9 version of this pack");
        }
        JsonArray files = list.get(0).getAsJsonObject().getAsJsonArray("files");
        JsonObject chosen = null;
        for (JsonElement f : files) {
            JsonObject o = f.getAsJsonObject();
            if (chosen == null || (o.has("primary") && o.get("primary").getAsBoolean())) {
                chosen = o;
            }
        }
        if (chosen == null) {
            throw new IOException("This pack has no files");
        }
        PackFile file = new PackFile();
        file.url = chosen.get("url").getAsString();
        file.filename = chosen.get("filename").getAsString();
        file.sha1 = chosen.getAsJsonObject("hashes").get("sha1").getAsString();
        file.size = chosen.get("size").getAsLong();
        return file;
    }

    /** Downloads `file` to `dest`, checking its SHA-1. */
    public static void download(PackFile file, File dest, Progress progress) throws IOException {
        File tmp = new File(dest.getPath() + ".part");
        HttpURLConnection c = open(file.url);
        MessageDigest sha1;
        try {
            sha1 = MessageDigest.getInstance("SHA-1");
        } catch (Exception e) {
            throw new IOException(e);
        }
        InputStream in = null;
        OutputStream out = null;
        try {
            in = c.getInputStream();
            out = new FileOutputStream(tmp);
            byte[] buf = new byte[65536];
            long done = 0;
            long total = file.size > 0 ? file.size : c.getContentLengthLong();
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
                sha1.update(buf, 0, n);
                done += n;
                progress.update(done, total);
            }
        } finally {
            close(in);
            close(out);
        }
        String hex = hex(sha1.digest());
        if (!hex.equalsIgnoreCase(file.sha1)) {
            tmp.delete();
            throw new IOException("Download was corrupted (checksum mismatch)");
        }
        if (dest.exists() && !dest.delete()) {
            tmp.delete();
            throw new IOException("Couldn't replace " + dest.getName());
        }
        if (!tmp.renameTo(dest)) {
            throw new IOException("Couldn't save " + dest.getName());
        }
    }

    /** Raw bytes (for icons), capped at `max` bytes. */
    public static byte[] bytes(String url, int max) throws IOException {
        HttpURLConnection c = open(url);
        InputStream in = null;
        try {
            in = c.getInputStream();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[16384];
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
                if (out.size() > max) {
                    throw new IOException("Too large");
                }
            }
            return out.toByteArray();
        } finally {
            close(in);
        }
    }

    private static JsonElement json(String url) throws IOException {
        HttpURLConnection c = open(url);
        InputStream in = null;
        try {
            in = c.getInputStream();
            return new JsonParser().parse(new InputStreamReader(in, StandardCharsets.UTF_8));
        } finally {
            close(in);
        }
    }

    private static HttpURLConnection open(String url) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setRequestProperty("User-Agent", USER_AGENT);
        c.setConnectTimeout(10000);
        c.setReadTimeout(20000);
        c.setInstanceFollowRedirects(true);
        int code = c.getResponseCode();
        if (code == 429) {
            throw new IOException("Modrinth is rate limiting; try again in a minute");
        }
        if (code / 100 != 2) {
            throw new IOException("Modrinth returned HTTP " + code);
        }
        return c;
    }

    private static String str(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return e == null || e.isJsonNull() ? "" : e.getAsString();
    }

    private static String enc(String s) throws IOException {
        return URLEncoder.encode(s, "UTF-8");
    }

    private static String hex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format(Locale.ROOT, "%02x", b));
        }
        return sb.toString();
    }

    private static void close(java.io.Closeable c) {
        if (c != null) {
            try {
                c.close();
            } catch (IOException ignored) {
                // nothing useful to do
            }
        }
    }
}
