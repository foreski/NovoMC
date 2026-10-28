package com.novomc.client.modules;

import com.google.gson.JsonElement;
import com.novomc.client.NovoClient;
import com.novomc.client.config.Catalog;
import com.novomc.client.module.Module;
import com.novomc.client.net.NovoPresence;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.ResourceLocation;

/**
 * NovoMC Badge: the NovoMC logo next to the names of players who are on
 * NovoMC, on nametags and in the tab list, like Lunar Client's icon.
 *
 * Who's on NovoMC comes from {@link NovoPresence}, which this module also
 * drives for Novo capes ({@link #tickPresence}); "shareMine" decides whether
 * others can see us at all, badge and cape alike.
 */
public class NovoBadgeModule extends Module {
    public static final int SIZE = 8;
    private static final ResourceLocation BADGE = new ResourceLocation("novomc", "textures/gui/badge.png");

    private static volatile boolean on;
    private static volatile boolean showOthers;

    public NovoBadgeModule() {
        super("novo_badge");
    }

    // ---- Who gets a badge (render thread) ----

    public static boolean has(UUID id) {
        if (!on || id == null || !NovoPresence.INSTANCE.isOnline(id)) {
            return false;
        }
        return showOthers || id.equals(self());
    }

    /** True for a player's own name label (not the score line under it). */
    public static boolean labelHas(Entity entity, String label) {
        return entity instanceof EntityPlayer && has(entity.getUniqueID())
                && EnumChatFormatting.getTextWithoutFormattingCodes(label).contains(entity.getName());
    }

    public static boolean tabHas(NetworkPlayerInfo info) {
        return info != null && has(info.getGameProfile().getId());
    }

    /** Draws the badge with its top-left corner at x, y (current GL transform). */
    public static void draw(float x, float y, float alpha) {
        Minecraft.getMinecraft().getTextureManager().bindTexture(BADGE);
        GlStateManager.enableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.color(1, 1, 1, alpha);
        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y, 0);
        Gui.drawModalRectWithCustomSizedTexture(0, 0, 0, 0, SIZE, SIZE, SIZE, SIZE);
        GlStateManager.popMatrix();
        GlStateManager.color(1, 1, 1, 1);
    }

    private static UUID self() {
        return Minecraft.getMinecraft().getSession().getProfile().getId();
    }

    @Override
    public void onSettingsChanged() {
        showOthers = bool("showOthers");
        NovoPresence.INSTANCE.refresh();
    }

    @Override
    protected void onEnable() {
        on = true;
        onSettingsChanged();
    }

    @Override
    protected void onDisable() {
        on = false;
    }

    // ---- Presence (client tick, every tick whether or not this module is on) ----

    private static final NovoPresence.Game GAME = new NovoPresence.Game() {
        private final Minecraft mc = Minecraft.getMinecraft();

        public UUID self() {
            return mc.getSession().getProfile().getId();
        }

        public String name() {
            return mc.getSession().getUsername();
        }

        public void join(String serverId) throws Exception {
            mc.getSessionService().joinServer(mc.getSession().getProfile(), mc.getSession().getToken(), serverId);
        }

        public Iterable<UUID> players() {
            List<UUID> out = new ArrayList<UUID>();
            NetHandlerPlayClient handler = mc.getNetHandler();
            if (handler != null) {
                for (NetworkPlayerInfo info : handler.getPlayerInfoMap()) {
                    out.add(info.getGameProfile().getId());
                }
            }
            return out;
        }
    };

    public static void tickPresence(Minecraft mc) {
        JsonElement share = NovoClient.config().setting(Catalog.get("novo_badge"), "shareMine");
        NovoPresence.INSTANCE.tick(mc.theWorld != null ? GAME : null, on || NovoCapesModule.isOn(), share == null || share.getAsBoolean());
    }
}
