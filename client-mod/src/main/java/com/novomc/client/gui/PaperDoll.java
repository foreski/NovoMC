package com.novomc.client.gui;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.mojang.authlib.minecraft.MinecraftProfileTexture;
import com.novomc.client.modules.NovoCapesModule;
import com.novomc.client.render.NovoCosmetics;
import com.novomc.client.util.Draw;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.ModelPlayer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.client.resources.SkinManager;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

/**
 * The title screen's paper doll: the player in front of their Nova cape, wings
 * and hat, with a picker for each slot underneath.
 *
 * The launcher writes the equipped ids, the owned cosmetics and their names
 * to `config/novomc/cosmetics.json` before a launch, because the game has no
 * account token to ask the relay itself (see `src-tauri/src/novo/cosmetics.rs`).
 * Picking another cosmetic only edits this file; the launcher saves whatever
 * changed to the account after the game exits.
 *
 * The doll is the vanilla player model (skin and all) in a front view, drawn
 * back to front — wings, cape, body, hat — with depth testing off, so the
 * layers stack the way they do in game without depth-buffer tricks.
 */
final class PaperDoll {
    /** The three slots, in the order the file and the rows use them. */
    private static final String[] KINDS = { "cape", "wing", "hat" };
    private static final String[] LABELS = { "CAPE", "WINGS", "HAT" };
    private static final String[] LISTS = { "capes", "wings", "hats" };
    private static final int ROW_H = 22;
    private static final int ROW_GAP = 3;
    private static final int PANEL_W = 116;
    /** "YOUR LOOK" above the figure; the font is 9 tall. */
    private static final int HEADER = 14;
    private static final int GAP = 4;
    private static final int PAD = 6;
    /** Hat meshes rise at most 7 units above the head's 8, so 15 is all the headroom needed. */
    private static final int ABOVE_NECK = 15;
    private static final int BELOW_NECK = 24;
    private static final int FIGURE_H = ABOVE_NECK + BELOW_NECK;
    /** Wings reach 22 units out from the shoulder on each side (see `WingModel`). */
    private static final int WING_HALF = 22;

    private static final ModelPlayer MODEL = new ModelPlayer(0.0F, false);
    private static volatile ResourceLocation skin;
    private static boolean askedSkin;

    private final File file;
    private final JsonObject data;

    private int x;
    private int y;
    private int unit;
    /** The shoulder line, where the model's origin and the wings' hinge sit. */
    private int neck;
    private int rowTop;
    private int panelBottom;
    private int centreX;

    private PaperDoll(File file, JsonObject data) {
        this.file = file;
        this.data = data;
    }

    /** The doll for this screen, or null when there is nothing to show. */
    static PaperDoll load() {
        Minecraft mc = Minecraft.getMinecraft();
        File file = new File(mc.mcDataDir, "config/novomc/cosmetics.json");
        if (!file.isFile()) {
            return null;
        }
        try {
            String json = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
            JsonObject data = new JsonParser().parse(json).getAsJsonObject();
            return new PaperDoll(file, data);
        } catch (Exception e) {
            System.err.println("[NovoMC] Could not read cosmetics.json: " + e);
            return null;
        }
    }

    // ---- Layout ----

    /**
     * Places the panel in the left gutter, as large as the room allows: the
     * figure has to clear the menu buttons to its right and the version line
     * below it. False when there is no room for it at all.
     */
    private boolean layout(int width, int height) {
        if (width < 400 || height < 200) {
            return false;
        }
        int buttonLeft = width / 2 - 90;
        int rows = 3 * ROW_H + 2 * ROW_GAP;
        int byHeight = (height - 24 - 30 - HEADER - rows - PAD - GAP) / FIGURE_H;
        int byWidth = (buttonLeft - 12) / (WING_HALF * 2);
        unit = Math.max(2, Math.min(4, Math.min(byHeight, byWidth)));
        int figureH = FIGURE_H * unit;
        int panelH = HEADER + figureH + GAP + rows + PAD;
        x = 6;
        y = Math.max(24, (height - panelH) / 2);
        if (y + panelH > height - 30) {
            return false;
        }
        // Wide wings at the larger sizes can reach past the panel; keep them off the buttons.
        centreX = Math.max(x + PANEL_W / 2, WING_HALF * unit + 3);
        neck = y + HEADER + ABOVE_NECK * unit;
        rowTop = y + HEADER + figureH + GAP;
        panelBottom = rowTop + rows + PAD;
        return true;
    }

    // ---- Drawing ----

    void draw(int mouseX, int mouseY, int width, int height) {
        if (!layout(width, height)) {
            return;
        }
        int right = x + PANEL_W;
        Draw.round(x, y, right, panelBottom, 6, 0xA00C1119);
        Draw.roundOutline(x, y, right, panelBottom, 6, 0x38FFFFFF);
        Draw.text("YOUR LOOK", x + 8, y + 3, 0xFF5B8CFF);

        drawDoll();
        for (int i = 0; i < KINDS.length; i++) {
            drawRow(i, mouseX, mouseY);
        }
    }

    private void drawRow(int index, int mouseX, int mouseY) {
        int top = rowTop + index * (ROW_H + ROW_GAP);
        int right = x + PANEL_W;
        boolean hovered = inside(mouseX, mouseY, x + 4, top, right - 4, top + ROW_H);
        Draw.round(x + 4, top, right - 4, top + ROW_H, 3, hovered ? 0x3DFFFFFF : 0x1FFFFFFF);
        Draw.text(LABELS[index], x + 9, top + 4, 0xFF8A94A6);

        String id = worn(KINDS[index]);
        String name = id == null ? "None" : nameOf(KINDS[index], id);
        Draw.text(Draw.trim(name, PANEL_W - 20), x + 9, top + 12, id == null ? 0xFFB9C3D2 : 0xFFFFFFFF);

        Draw.text("<", right - 26, top + 4, hovered ? 0xFFFFFFFF : 0xFF8A94A6);
        Draw.text(">", right - 13, top + 4, hovered ? 0xFFFFFFFF : 0xFF8A94A6);
    }

    private void drawDoll() {
        Minecraft mc = Minecraft.getMinecraft();

        GlStateManager.pushMatrix();
        GlStateManager.disableDepth();
        GlStateManager.disableLighting();
        GlStateManager.disableCull();
        GlStateManager.enableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.translate(centreX, neck, 0.0F);
        GlStateManager.scale(unit, unit, unit);

        String wing = worn("wing");
        if (wing != null) {
            NovoCosmetics.renderWings(wing, 1.0F);
            GlStateManager.disableLighting();
            GlStateManager.enableTexture2D();
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        }

        String cape = worn("cape");
        if (cape != null) {
            drawCape(cape);
        }

        mc.getTextureManager().bindTexture(skin());
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        MODEL.bipedHead.render(1.0F);
        MODEL.bipedHeadwear.render(1.0F);
        MODEL.bipedBody.render(1.0F);
        MODEL.bipedRightArm.render(1.0F);
        MODEL.bipedLeftArm.render(1.0F);
        MODEL.bipedRightLeg.render(1.0F);
        MODEL.bipedLeftLeg.render(1.0F);

        String hat = worn("hat");
        if (hat != null) {
            NovoCosmetics.renderHat(hat, 1.0F);
            GlStateManager.disableLighting();
            GlStateManager.enableTexture2D();
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        }

        GlStateManager.popMatrix();
        GlStateManager.enableDepth();
        GlStateManager.enableCull();
        GlStateManager.enableBlend();
        GlStateManager.enableAlpha();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
    }

    /**
     * The cape texture's outer face (10x16 at 1,1 of the 64x32 sheet), hung
     * behind the doll. It is drawn wider than a worn cape — flared from 6 to
     * 8.5 units — because from the front a worn cape hides completely behind
     * the body: flared, the player can see which one they are wearing.
     */
    private void drawCape(String id) {
        Minecraft.getMinecraft().getTextureManager().bindTexture(NovoCapesModule.capeTexture(id));
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        float u0 = 1.0F / 64.0F;
        float u1 = 11.0F / 64.0F;
        float v0 = 1.0F / 32.0F;
        float v1 = 17.0F / 32.0F;
        float z = 2.0F;
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glTexCoord2f(u0, v0);
        GL11.glVertex3f(-6.0F, 0.0F, z);
        GL11.glTexCoord2f(u1, v0);
        GL11.glVertex3f(6.0F, 0.0F, z);
        GL11.glTexCoord2f(u1, v1);
        GL11.glVertex3f(8.5F, 16.0F, z + 0.4F);
        GL11.glTexCoord2f(u0, v1);
        GL11.glVertex3f(-8.5F, 16.0F, z + 0.4F);
        GL11.glEnd();
    }

    // ---- Editing ----

    /** Cycles a slot when the click lands on it; true when the click was used. */
    boolean click(int mouseX, int mouseY, int button, int width, int height) {
        if (!layout(width, height)) {
            return false;
        }
        for (int i = 0; i < KINDS.length; i++) {
            int top = rowTop + i * (ROW_H + ROW_GAP);
            if (inside(mouseX, mouseY, x + 4, top, x + PANEL_W - 4, top + ROW_H)) {
                int direction = button == 1 || mouseX < x + 20 ? -1 : 1;
                cycle(KINDS[i], direction);
                return true;
            }
        }
        return false;
    }

    private void cycle(String kind, int direction) {
        List<String> ids = new ArrayList<String>();
        ids.add(null);
        for (JsonElement element : items(kind)) {
            String id = string(element.getAsJsonObject(), "id");
            if (id != null) {
                ids.add(id);
            }
        }
        int at = ids.indexOf(worn(kind));
        if (at < 0) {
            at = 0;
        }
        int next = ((at + direction) % ids.size() + ids.size()) % ids.size();
        set(kind, ids.get(next));
        save();
    }

    private void set(String kind, String id) {
        data.add(kind, id == null ? JsonNull.INSTANCE : new JsonPrimitive(id));
    }

    private void save() {
        try {
            Files.write(file.toPath(), data.toString().getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            System.err.println("[NovoMC] Could not save cosmetics.json: " + e);
        }
    }

    private String worn(String kind) {
        return string(data, kind);
    }

    private JsonArray items(String kind) {
        String key = "cape".equals(kind) ? LISTS[0] : "wing".equals(kind) ? LISTS[1] : LISTS[2];
        JsonElement items = data.get("items");
        if (items == null || !items.isJsonObject()) {
            return new JsonArray();
        }
        JsonElement list = items.getAsJsonObject().get(key);
        return list != null && list.isJsonArray() ? list.getAsJsonArray() : new JsonArray();
    }

    private String nameOf(String kind, String id) {
        for (JsonElement element : items(kind)) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject item = element.getAsJsonObject();
            if (id.equals(string(item, "id"))) {
                String name = string(item, "name");
                return name == null ? id : name;
            }
        }
        return id;
    }

    private static String string(JsonObject object, String key) {
        JsonElement value = object.get(key);
        return value == null || value.isJsonNull() ? null : value.getAsString();
    }

    private static boolean inside(int mx, int my, int left, int top, int right, int bottom) {
        return mx >= left && mx < right && my >= top && my < bottom;
    }

    /** The player's skin, else the grey default while it loads (or offline). */
    private static ResourceLocation skin() {
        Minecraft mc = Minecraft.getMinecraft();
        if (skin == null) {
            if (!askedSkin) {
                askedSkin = true;
                try {
                    mc.getSkinManager().loadProfileTextures(mc.getSession().getProfile(), new SkinManager.SkinAvailableCallback() {
                        @Override
                        public void skinAvailable(MinecraftProfileTexture.Type type, ResourceLocation location, MinecraftProfileTexture texture) {
                            if (type == MinecraftProfileTexture.Type.SKIN) {
                                skin = location;
                            }
                        }
                    }, false);
                } catch (Exception ignored) {
                    // Offline: the default skin below is fine.
                }
            }
            if (skin == null) {
                UUID id = mc.getSession().getProfile().getId();
                skin = id == null ? DefaultPlayerSkin.getDefaultSkinLegacy() : DefaultPlayerSkin.getDefaultSkin(id);
            }
        }
        return skin;
    }
}
