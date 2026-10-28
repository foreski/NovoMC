package com.novomc.modern.ui;

import com.novomc.modern.mixin.VideoSettingsInvoker;
import java.util.List;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.TextureFilteringMethod;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.VideoSettingsScreen;

/**
 * Novo's Video Settings on modern Minecraft. The option lists come from
 * vanilla's Video Settings, so they follow game updates; textures reload on
 * close when mipmaps or filtering changed, as vanilla does.
 */
public class NovoVideo extends NovoSettings {
    private int oldMipmaps;
    private int oldAnisotropy;
    private TextureFilteringMethod oldFiltering;

    public NovoVideo(Screen parent) {
        super(parent, "Video Settings");
    }

    @Override
    protected void init() {
        if (oldFiltering == null) {
            oldMipmaps = minecraft.options.mipmapLevels().get();
            oldAnisotropy = minecraft.options.maxAnisotropyBit().get();
            oldFiltering = minecraft.options.textureFiltering().get();
        }
        super.init();
    }

    @Override
    protected String[][] sections() {
        return new String[][] {{"display", "Display"}, {"quality", "Quality"}, {"preferences", "Preferences"}};
    }

    @Override
    protected String subtitle() {
        return "Changes apply as you make them";
    }

    @Override
    protected void rows(String section, List<Row> out) {
        var o = minecraft.options;
        OptionInstance<?>[] list = switch (section) {
            case "display" -> VideoSettingsInvoker.novomc$display(o);
            case "quality" -> VideoSettingsInvoker.novomc$quality(o);
            default -> VideoSettingsInvoker.novomc$preferences(o);
        };
        for (OptionInstance<?> option : list) {
            out.add(option(option, option.toString(), null));
        }
        if ("display".equals(section)) {
            out.add(link("Fullscreen resolution", "Vanilla's Video Settings", () -> {
                NovoUi.vanillaNext = true;
                return new VideoSettingsScreen(this, minecraft, o);
            }));
        }
    }

    @Override
    public void removed() {
        var o = minecraft.options;
        if (o.mipmapLevels().get() != oldMipmaps || o.maxAnisotropyBit().get() != oldAnisotropy || o.textureFiltering().get() != oldFiltering) {
            minecraft.updateMaxMipLevel(o.mipmapLevels().get());
            minecraft.delayTextureReload();
        }
        super.removed();
    }
}
