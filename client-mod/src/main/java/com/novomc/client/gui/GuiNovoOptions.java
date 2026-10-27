package com.novomc.client.gui;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.audio.SoundCategory;
import net.minecraft.client.gui.GuiControls;
import net.minecraft.client.gui.GuiLanguage;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.entity.player.EnumPlayerModelParts;
import net.minecraft.world.EnumDifficulty;

/**
 * NovoMC's Options screen: general options and links to the other settings
 * screens, plus sound, chat and skin settings in place of vanilla's separate
 * screens.
 */
public class GuiNovoOptions extends GuiNovoSettings {
    public GuiNovoOptions(GuiScreen parent) {
        super(parent);
    }

    @Override
    protected String title() {
        return "Options";
    }

    @Override
    protected String subtitle() {
        return "Game settings, sound, chat and skin";
    }

    @Override
    protected String[][] sections() {
        return new String[][] {{"general", "General"}, {"sound", "Sound"}, {"chat", "Chat"}, {"skin", "Skin"}};
    }

    @Override
    protected List<Row> build(String id) {
        List<Row> out = new ArrayList<Row>();
        if (id.equals("general")) {
            out.add(option(GameSettings.Options.FOV, "Field of view", null));
            if (mc().theWorld != null && mc().isSingleplayer() && !mc().theWorld.getWorldInfo().isDifficultyLocked()) {
                out.add(new Row("Difficulty", "This world only") {
                    protected String value() {
                        return I18n.format(mc.theWorld.getDifficulty().getDifficultyResourceKey());
                    }

                    protected void click() {
                        EnumDifficulty next = EnumDifficulty.getDifficultyEnum((mc.theWorld.getDifficulty().getDifficultyId() + 1) % 4);
                        mc.theWorld.getWorldInfo().setDifficulty(next);
                    }
                });
            }
            final GuiScreen self = this;
            out.add(link("Video settings", "Graphics, Sodium and rendering features", () -> mc.displayGuiScreen(new GuiNovoVideo(self))));
            out.add(link("Controls", "Key bindings and mouse", () -> mc.displayGuiScreen(new GuiControls(self, gs))));
            out.add(link("Language", null, () -> mc.displayGuiScreen(new GuiLanguage(self, gs, mc.getLanguageManager()))));
            out.add(link("Resource packs", "Browse and install packs", () -> mc.displayGuiScreen(new GuiResourcePacks(self))));
            out.add(link("NovoMC mods", "Turn mods on and off", () -> mc.displayGuiScreen(new GuiModules(self))));
        } else if (id.equals("sound")) {
            for (final SoundCategory cat : SoundCategory.values()) {
                out.add(new Row(I18n.format("soundCategory." + cat.getCategoryName()), null) {
                    protected String value() {
                        float v = gs.getSoundLevel(cat);
                        return v == 0 ? "Off" : Math.round(v * 100) + "%";
                    }

                    protected float fraction() {
                        return gs.getSoundLevel(cat);
                    }

                    protected void slide(float f) {
                        float v = Math.max(0, Math.min(1, f));
                        if (v != gs.getSoundLevel(cat)) {
                            gs.setSoundLevel(cat, v);
                        }
                    }
                });
            }
        } else if (id.equals("chat")) {
            out.add(option(GameSettings.Options.CHAT_VISIBILITY, "Chat", null));
            out.add(option(GameSettings.Options.CHAT_COLOR, "Colors", null));
            out.add(option(GameSettings.Options.CHAT_LINKS, "Web links", null));
            out.add(option(GameSettings.Options.CHAT_LINKS_PROMPT, "Prompt on links", null));
            out.add(option(GameSettings.Options.CHAT_OPACITY, "Opacity", null));
            out.add(option(GameSettings.Options.CHAT_SCALE, "Scale", null));
            out.add(option(GameSettings.Options.CHAT_WIDTH, "Width", null));
            out.add(option(GameSettings.Options.CHAT_HEIGHT_FOCUSED, "Focused height", null));
            out.add(option(GameSettings.Options.CHAT_HEIGHT_UNFOCUSED, "Unfocused height", null));
            out.add(option(GameSettings.Options.REDUCED_DEBUG_INFO, "Reduced debug info", null));
        } else {
            for (final EnumPlayerModelParts part : EnumPlayerModelParts.values()) {
                out.add(new Row(part.func_179326_d().getFormattedText(), null) {
                    protected Boolean on() {
                        return gs.getModelParts().contains(part);
                    }

                    protected void click() {
                        gs.switchModelPartEnabled(part);
                    }
                });
            }
        }
        return out;
    }

    private static net.minecraft.client.Minecraft mc() {
        return net.minecraft.client.Minecraft.getMinecraft();
    }
}
