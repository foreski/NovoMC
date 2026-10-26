package com.novomc.client.gui;

import com.novomc.client.packs.Modrinth;
import com.novomc.client.packs.PackIcons;
import com.novomc.client.packs.PackLibrary;
import com.novomc.client.util.Draw;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.resources.ResourcePackRepository;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.Sys;

/**
 * In-game Resource Pack library: browse and search every Modrinth resource
 * pack that supports 1.8.x, install one with a click (it turns on right away),
 * and switch installed packs on and off. Looks like the Mods screen.
 */
public class GuiResourcePacks extends NovoPanelScreen {
    private static final int CARD = 52;
    private static final int PAGE = 30;
    private static final ResourceLocation DOWNLOAD = icon("download");
    private static final ResourceLocation CHECK = icon("check");
    private static final ResourceLocation SORT = icon("arrow-up-down");
    private static final ResourceLocation FOLDER = icon("folder-open");
    private static final ResourceLocation LAYERS = icon("layers");
    private static final ExecutorService SEARCH = Executors.newSingleThreadExecutor(new java.util.concurrent.ThreadFactory() {
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, "NovoMC pack search");
            t.setDaemon(true);
            return t;
        }
    });

    /** Chip id -> Modrinth category (null for all; "installed" is local). */
    private static final String[][] CATEGORIES = {
        {"all", "All"}, {"installed", "Installed"}, {"combat", "PvP"}, {"simplistic", "Simplistic"},
        {"vanilla-like", "Vanilla-like"}, {"themed", "Themed"}, {"realistic", "Realistic"}, {"fonts", "Fonts"}
    };
    private static final String[][] SORTS = {{"downloads", "Popular"}, {"relevance", "Relevance"}, {"newest", "Newest"}, {"updated", "Updated"}};
    private static final Map<String, ResourceLocation> CHIP_ICONS = new HashMap<String, ResourceLocation>();

    static {
        CHIP_ICONS.put("installed", LAYERS);
        CHIP_ICONS.put("combat", SWORDS);
    }

    private String category = "all";
    private int sort;
    private final List<Modrinth.Hit> results = new ArrayList<Modrinth.Hit>();
    private int total = -1;
    private boolean loading;
    private String error;
    /** Bumped on every new search; older responses are dropped. */
    private int generation;
    private long searchChangedAt;
    private boolean searchPending;
    private List<ResourcePackRepository.Entry> local;

    public GuiResourcePacks(GuiScreen parent) {
        super(parent);
    }

    @Override
    protected String tab() {
        return "packs";
    }

    @Override
    public void initGui() {
        super.initGui();
        PackLibrary.rescan();
        if (total < 0 && !loading) {
            reload();
        }
    }

    /** Installs the first listed pack (used by the dev autotest). */
    public boolean installFirst() {
        if (results.isEmpty()) {
            return false;
        }
        PackLibrary.install(results.get(0));
        return true;
    }

    // ---- Data ----

    private void reload() {
        generation++;
        results.clear();
        total = -1;
        error = null;
        loading = false;
        scroll = 0;
        scrollTarget = 0;
        local = null;
        if (!"installed".equals(category)) {
            loadMore();
        }
    }

    private void loadMore() {
        if (loading) {
            return;
        }
        loading = true;
        error = null;
        final int gen = generation;
        final String query = search.toString().trim();
        final String cat = "all".equals(category) ? null : category;
        final String index = SORTS[sort][0];
        final int offset = results.size();
        SEARCH.submit(new Runnable() {
            public void run() {
                Modrinth.Page page = null;
                String failure = null;
                try {
                    page = Modrinth.search(query, cat, index, offset, PAGE);
                } catch (Exception e) {
                    failure = e.getMessage() != null ? e.getMessage() : e.toString();
                }
                final Modrinth.Page result = page;
                final String err = failure;
                mc.addScheduledTask(new Runnable() {
                    public void run() {
                        if (gen != generation) {
                            return;
                        }
                        loading = false;
                        if (result != null) {
                            total = result.total;
                            results.addAll(result.hits);
                        } else {
                            error = "Couldn't reach Modrinth: " + err;
                        }
                    }
                });
            }
        });
    }

    @Override
    protected void searchChanged() {
        searchChangedAt = System.currentTimeMillis();
        searchPending = true;
    }

    @Override
    protected void chipSelected(String group, String id) {
        if (!id.equals(category)) {
            category = id;
            reload();
        }
    }

    // ---- Drawing ----

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        if (searchPending && System.currentTimeMillis() - searchChangedAt > 300) {
            searchPending = false;
            if ("installed".equals(category)) {
                scrollTarget = 0;
            } else {
                reload();
            }
        }
        int[] p = beginFrame(mouseX, mouseY);
        int l = p[0];
        int top = p[1];
        int r = p[2];
        String subtitle = total >= 0
                ? String.format(Locale.ROOT, "%,d packs for 1.8.9 from Modrinth. One click to install.", total)
                : "Resource packs for 1.8.9 from Modrinth. One click to install.";
        drawHeader(l, r, top, "Resource Packs", subtitle);
        float searchLeft = r - searchWidth(l, r);
        drawSearch(searchLeft, top + 31, r, "Search packs");
        drawSortButton(searchLeft - 4, top + 31);

        List<String[]> chips = new ArrayList<String[]>();
        for (String[] c : CATEGORIES) {
            chips.add(c);
        }
        int toolbarBottom = drawChips("category", chips, category, CHIP_ICONS, l, r, top + 58);
        if ("installed".equals(category)) {
            drawInstalled(l, r, toolbarBottom + 8, p[3] - PAD);
        } else {
            drawResults(l, r, toolbarBottom + 8, p[3] - PAD);
        }
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    /** "Popular" etc.; clicking cycles the sort order. Ends at `right`. */
    private void drawSortButton(float right, float y) {
        if ("installed".equals(category)) {
            return;
        }
        String text = SORTS[sort][1];
        float w = Draw.width(text) + 26;
        float l = right - w;
        boolean hover = in(l, y, right, y + 16);
        Draw.round(l, y, right, y + 16, 8, hover ? 0x22FFFFFF : 0x99080B12);
        Draw.roundOutline(l, y, right, y + 16, 8, 0x26FFFFFF);
        Draw.icon(SORT, l + 7, y + 4, 8, MUTED);
        Draw.text(text, l + 18, y + 4, hover ? TEXT : MUTED);
        hit(l, y, right, y + 16, new Runnable() {
            public void run() {
                sort = (sort + 1) % SORTS.length;
                reload();
            }
        });
    }

    private void drawResults(int l, int r, int top, int bottom) {
        int cols = r - l >= 460 ? 2 : 1;
        int gap = 6;
        float colW = (r - l - gap * (cols - 1)) / (float) cols;
        int rows = (results.size() + cols - 1) / cols;
        float footer = loading || error != null || results.isEmpty() ? 30 : 0;
        float content = rows * (CARD + gap) + footer;
        float max = updateScroll(content, bottom - top);

        // Load the next page as the end comes into view.
        if (!loading && error == null && total > results.size() && scroll >= max - 80) {
            loadMore();
        }

        clip(l - 1, top, r + 1, bottom);
        for (int i = 0; i < results.size(); i++) {
            float x = l + (i % cols) * (colW + gap);
            float y = top - scroll + (i / cols) * (CARD + gap);
            if (y + CARD >= top && y <= bottom) {
                drawHit(results.get(i), x, y, colW, l - 1, top, r + 1, bottom);
            }
        }
        float fy = top - scroll + rows * (CARD + gap) + 8;
        if (loading) {
            String dots = "...".substring(0, (int) (System.currentTimeMillis() / 300 % 4));
            String text = "Loading packs" + dots;
            Draw.text(text, (l + r - Draw.width("Loading packs...")) / 2f, fy, MUTED);
        } else if (error != null) {
            String text = Draw.trim(error, r - l - 70);
            float tw = Draw.width(text);
            float x = (l + r - tw - 50) / 2f;
            Draw.text(text, x, fy, RED);
            button(x + tw + 50, fy - 2, "Retry", null, new Runnable() {
                public void run() {
                    error = null;
                    loadMore();
                }
            });
        } else if (results.isEmpty() && total == 0) {
            String text = "No packs match.";
            Draw.text(text, (l + r - Draw.width(text)) / 2f, fy, MUTED);
        }
        noClip();
        drawScrollbar(r, top, bottom, max);
    }

    private void drawCardBase(String id, float x, float y, float w, boolean active) {
        boolean hover = in(x, y, x + w, y + CARD);
        float hv = ease("hover/" + id, hover ? 1 : 0);
        int fill = active ? 0xC0182340 : 0xB0121722;
        Draw.round(x, y, x + w, y + CARD, 6, Draw.blend(fill, 0xD01C2333, hv * 0.5f));
        Draw.roundOutline(x, y, x + w, y + CARD, 6, active ? 0x775B8CFF : Draw.blend(0x1FFFFFFF, 0x40FFFFFF, hv));
    }

    private void drawHit(final Modrinth.Hit hit, float x, float y, float w, float gl, float gt, float gr, float gb) {
        String file = PackLibrary.installedFile(hit.id);
        boolean active = file != null && PackLibrary.isActive(file);
        clip(gl, gt, gr, gb);
        drawCardBase(hit.id, x, y, w, active);
        clip(Math.max(gl, x), Math.max(gt, y), Math.min(gr, x + w), Math.min(gb, y + CARD));

        drawIcon(PackIcons.get(hit.iconUrl), hit.title, x + 8, y + 9, 34);
        float actionW = 62;
        float tx = x + 50;
        float textW = w - 50 - actionW - 14;
        String title = Draw.trim(hit.title, (int) textW);
        Draw.text(title, tx, y + 7, TEXT);
        String by = " by " + hit.author;
        if (Draw.width(title) + Draw.width(by) <= textW) {
            Draw.text(by, tx + Draw.width(title), y + 7, MUTED);
        }

        PackLibrary.Job job = PackLibrary.job(hit.id);
        if (job != null && job.error != null) {
            Draw.text(Draw.trim(job.error, (int) textW), tx, y + 19, RED);
        } else {
            List<String> lines = Draw.wrap(hit.description, (int) textW);
            for (int i = 0; i < Math.min(2, lines.size()); i++) {
                String line = lines.get(i);
                if (i == 1 && lines.size() > 2) {
                    line = Draw.trim(line + "...", (int) textW);
                }
                Draw.text(line, tx, y + 18 + i * 10, MUTED);
            }
        }
        Draw.icon(DOWNLOAD, tx, y + 40, 7, 0xFF5E6676);
        String meta = formatCount(hit.downloads);
        for (String c : hit.categories) {
            if (c.matches("\\d+x[-+]?")) {
                meta += "  " + c;
                break;
            }
        }
        if (hit.categories.contains("combat")) {
            meta += "  PvP";
        }
        Draw.text(meta, tx + 10, y + 40, 0xFF5E6676);

        drawAction(hit, file, active, job, x + w - actionW - 8, y + (CARD - 16) / 2f, actionW);
        clip(gl, gt, gr, gb);
    }

    /** Install / progress / Enable / Active, depending on the pack's state. */
    private void drawAction(final Modrinth.Hit hit, final String file, final boolean active, PackLibrary.Job job, float x, float y, float w) {
        float h = 16;
        if (job != null && job.error == null) {
            Draw.round(x, y, x + w, y + h, 8, FIELD);
            float f = "downloading".equals(job.stage) ? job.fraction() : 0;
            if (f > 0) {
                Draw.round(x, y, x + Math.max(h, w * f), y + h, 8, 0x995B8CFF);
            }
            String text = "downloading".equals(job.stage) ? Math.round(f * 100) + "%" : "applying".equals(job.stage) ? "Applying" : "Finding";
            Draw.text(text, x + (w - Draw.width(text)) / 2f, y + 4, TEXT);
            return;
        }
        boolean hover = in(x, y, x + w, y + h);
        Runnable action;
        if (file == null) {
            String text = job != null ? "Retry" : "Install";
            Draw.round(x, y, x + w, y + h, 8, hover ? 0xFF6C9BFF : ACCENT);
            if (hover) {
                Draw.round(x + w / 2, y, x + w, y + h, 8, Draw.withAlpha(PURPLE, 90));
            }
            float tw = Draw.width(text) + 11;
            Draw.icon(DOWNLOAD, x + (w - tw) / 2, y + 4, 8, 0xFFFFFFFF);
            Draw.text(text, x + (w - tw) / 2 + 11, y + 4, 0xFFFFFFFF);
            action = new Runnable() {
                public void run() {
                    PackLibrary.forget(hit.id);
                    PackLibrary.install(hit);
                }
            };
        } else {
            action = drawToggleButton(file, active, x, y, w, hover);
        }
        hit(x, y, x + w, y + h, action);
    }

    /** "Active" (green, click to turn off) or "Enable"; returns the click action. */
    private Runnable drawToggleButton(final String file, final boolean active, float x, float y, float w, boolean hover) {
        float h = 16;
        if (active) {
            String text = hover ? "Turn off" : "Active";
            Draw.round(x, y, x + w, y + h, 8, hover ? 0x33F87171 : 0x334ADE80);
            Draw.roundOutline(x, y, x + w, y + h, 8, hover ? 0x88F87171 : 0x884ADE80);
            float tw = Draw.width(text) + (hover ? 0 : 11);
            if (!hover) {
                Draw.icon(CHECK, x + (w - tw) / 2, y + 4, 8, GREEN);
            }
            Draw.text(text, x + (w - tw) / 2 + (hover ? 0 : 11), y + 4, hover ? RED : GREEN);
        } else {
            String text = "Enable";
            Draw.round(x, y, x + w, y + h, 8, hover ? 0x445B8CFF : 0x1AFFFFFF);
            Draw.roundOutline(x, y, x + w, y + h, 8, 0x665B8CFF);
            Draw.text(text, x + (w - Draw.width(text)) / 2, y + 4, hover ? TEXT : 0xFFB9CBFF);
        }
        return new Runnable() {
            public void run() {
                PackLibrary.setActive(file, !active);
                local = null;
            }
        };
    }

    private void drawInstalled(int l, int r, int top, int bottom) {
        if (local == null) {
            local = PackLibrary.local();
        }
        String q = search.toString().trim().toLowerCase(Locale.ROOT);
        List<ResourcePackRepository.Entry> shown = new ArrayList<ResourcePackRepository.Entry>();
        for (ResourcePackRepository.Entry e : local) {
            if (q.isEmpty() || e.getResourcePackName().toLowerCase(Locale.ROOT).contains(q)) {
                shown.add(e);
            }
        }
        int cols = r - l >= 460 ? 2 : 1;
        int gap = 6;
        float colW = (r - l - gap * (cols - 1)) / (float) cols;
        int rows = (shown.size() + cols - 1) / cols;
        float max = updateScroll(rows * (CARD + gap) + 24, bottom - top);

        clip(l - 1, top, r + 1, bottom);
        for (int i = 0; i < shown.size(); i++) {
            float x = l + (i % cols) * (colW + gap);
            float y = top - scroll + (i / cols) * (CARD + gap);
            if (y + CARD >= top && y <= bottom) {
                drawLocal(shown.get(i), x, y, colW, l - 1, top, r + 1, bottom);
            }
        }
        float fy = top - scroll + rows * (CARD + gap) + 6;
        String hint = shown.isEmpty() ? "No packs installed yet. Install some from All." : "Packs turned on most recently sit on top.";
        Draw.text(Draw.trim(hint, r - l - 90), l, fy + 2, MUTED);
        button(r, fy, "Open folder", FOLDER, new Runnable() {
            public void run() {
                Sys.openURL("file:///" + mc.getResourcePackRepository().getDirResourcepacks().getAbsolutePath().replace('\\', '/'));
            }
        });
        noClip();
        drawScrollbar(r, top, bottom, max);
    }

    private void drawLocal(ResourcePackRepository.Entry e, float x, float y, float w, float gl, float gt, float gr, float gb) {
        String file = e.getResourcePackName();
        boolean active = PackLibrary.isActive(file);
        clip(gl, gt, gr, gb);
        drawCardBase(file, x, y, w, active);
        clip(Math.max(gl, x), Math.max(gt, y), Math.min(gr, x + w), Math.min(gb, y + CARD));

        // The pack's own pack.png, bound by vanilla.
        float ix = x + 8;
        float iy = y + 9;
        Draw.round(ix, iy, ix + 34, iy + 34, 4, FIELD);
        e.bindTexturePackIcon(mc.getTextureManager());
        GlStateManager.enableBlend();
        GlStateManager.color(1, 1, 1, 1);
        GlStateManager.pushMatrix();
        GlStateManager.translate(ix, iy, 0);
        Gui.drawModalRectWithCustomSizedTexture(0, 0, 0, 0, 34, 34, 34, 34);
        GlStateManager.popMatrix();

        float actionW = 62;
        float tx = x + 50;
        float textW = w - 50 - actionW - 14;
        String name = file.toLowerCase(Locale.ROOT).endsWith(".zip") ? file.substring(0, file.length() - 4) : file;
        Draw.text(Draw.trim(name, (int) textW), tx, y + 7, TEXT);
        List<String> lines = Draw.wrap(e.getTexturePackDescription(), (int) textW);
        for (int i = 0; i < Math.min(3, lines.size()); i++) {
            Draw.text(lines.get(i), tx, y + 18 + i * 10, MUTED);
        }
        float bx = x + w - actionW - 8;
        float by = y + (CARD - 16) / 2f;
        hit(bx, by, bx + actionW, by + 16, drawToggleButton(file, active, bx, by, actionW, in(bx, by, bx + actionW, by + 16)));
        clip(gl, gt, gr, gb);
    }

    /** A pack icon, or a colored tile with its first letter while it loads. */
    private void drawIcon(ResourceLocation tex, String title, float x, float y, float size) {
        if (tex != null) {
            Draw.round(x, y, x + size, y + size, 4, FIELD);
            Draw.icon(tex, x, y, size, 0xFFFFFFFF);
            return;
        }
        int hue = Math.abs(title.hashCode()) % 360;
        int color = java.awt.Color.HSBtoRGB(hue / 360f, 0.45f, 0.55f) | 0xFF000000;
        Draw.round(x, y, x + size, y + size, 4, color);
        String letter = title.isEmpty() ? "?" : title.substring(0, 1).toUpperCase(Locale.ROOT);
        Draw.scaled(letter, x + size / 2 - Draw.width(letter), y + size / 2 - 8, 0xFFFFFFFF, 2);
    }

    private static String formatCount(long n) {
        if (n >= 1000000) {
            return String.format(Locale.ROOT, "%.1fM", n / 1e6);
        }
        if (n >= 1000) {
            return String.format(Locale.ROOT, "%.0fK", n / 1e3);
        }
        return String.valueOf(n);
    }
}
