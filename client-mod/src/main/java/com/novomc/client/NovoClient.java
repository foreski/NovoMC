package com.novomc.client;

import com.novomc.client.config.Catalog;
import com.novomc.client.config.ModConfig;
import com.novomc.client.gui.GuiHudEditor;
import com.novomc.client.module.HudModule;
import com.novomc.client.module.Module;
import com.novomc.client.util.Draw;
import com.novomc.client.modules.*;
import com.novomc.client.modules.ArmorStatusModule;
import com.novomc.client.modules.ClockModule;
import com.novomc.client.modules.CoordinatesModule;
import com.novomc.client.modules.CpsModule;
import com.novomc.client.modules.CpsTracker;
import com.novomc.client.modules.CrosshairModule;
import com.novomc.client.modules.DirectionModule;
import com.novomc.client.modules.EntityCullingModule;
import com.novomc.client.modules.FpsModule;
import com.novomc.client.modules.FullbrightModule;
import com.novomc.client.modules.ItemCounterModule;
import com.novomc.client.modules.KeystrokesModule;
import com.novomc.client.modules.MemoryModule;
import com.novomc.client.modules.ParticleLimiterModule;
import com.novomc.client.modules.PerformanceTweaksModule;
import com.novomc.client.modules.PingModule;
import com.novomc.client.modules.PotionEffectsModule;
import com.novomc.client.modules.SaturationModule;
import com.novomc.client.modules.ToggleSprintModule;
import com.novomc.client.modules.UnfocusedFpsModule;
import com.novomc.client.modules.ZoomModule;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.settings.KeyBinding;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.input.Keyboard;

/** Owns the config, the modules and the keybinds, and draws the HUD. */
public final class NovoClient {
    public static final KeyBinding MENU_KEY = new KeyBinding("key.novomc.menu", Keyboard.KEY_RSHIFT, "key.categories.novomc");
    public static final KeyBinding ZOOM_KEY = new KeyBinding("key.novomc.zoom", Keyboard.KEY_C, "key.categories.novomc");
    public static final KeyBinding FREELOOK_KEY = new KeyBinding("key.novomc.freelook", Keyboard.KEY_LMENU, "key.categories.novomc");
    /** Unbound by default; set it in Controls. */
    public static final KeyBinding SNAPLOOK_KEY = new KeyBinding("key.novomc.snaplook", Keyboard.KEY_NONE, "key.categories.novomc");
    /** Unbound by default; set it in Controls. */
    public static final KeyBinding STOPWATCH_KEY = new KeyBinding("key.novomc.stopwatch", Keyboard.KEY_NONE, "key.categories.novomc");

    private static final org.apache.logging.log4j.Logger LOG = org.apache.logging.log4j.LogManager.getLogger("NovoMC");
    private static final List<Module> MODULES = new ArrayList<Module>();
    private static ModConfig config;

    private final Minecraft mc = Minecraft.getMinecraft();
    private int ticks;

    private NovoClient() {}

    public static void init() {
        Catalog.load();
        config = new ModConfig(Minecraft.getMinecraft().mcDataDir);
        config.load();

        Collections.addAll(MODULES,
                new FpsModule(), new CpsModule(), new KeystrokesModule(), new CoordinatesModule(),
                new DirectionModule(), new ArmorStatusModule(), new PotionEffectsModule(), new PingModule(),
                new MemoryModule(), new ClockModule(), new SaturationModule(), new ItemCounterModule(),
                new ToggleSprintModule(), new ZoomModule(), new FullbrightModule(), new CrosshairModule(),
                new EntityCullingModule(), new ParticleLimiterModule(), new UnfocusedFpsModule(),
                new PerformanceTweaksModule(),
                // Batch 2
                new ComboCounterModule(), new ReachDisplayModule(), new OldAnimationsModule(), new HitColorModule(),
                new BlockOutlineModule(), new HurtCamModule(), new FovChangerModule(), new ParticleChangerModule(),
                new TimeChangerModule(), new WeatherChangerModule(), new FogModule(), new GlintModule(),
                new MotionBlurModule(), new MenuBlurModule(), new ModernFontModule(), new Items2DModule(), new ItemPhysicsModule(),
                new HitboxModule(), new ChunkBordersModule(), new NametagsModule(), new FreelookModule(),
                new SnaplookModule(), new MainMenuModule(),
                // Batch 3
                new ScoreboardModule(), new BossBarModule(), new TitlesModule(), new ChatModule(),
                new TabEditorModule(), new NickHiderModule(), new ScrollableTooltipsModule(), new TntCountdownModule(),
                new CooldownsModule(), new StopwatchModule(), new ServerAddressModule(), new PackDisplayModule(),
                new PlaytimeModule(), new DayCounterModule());

        for (KeyBinding key : new KeyBinding[] {MENU_KEY, ZOOM_KEY, FREELOOK_KEY, SNAPLOOK_KEY, STOPWATCH_KEY}) {
            ClientRegistry.registerKeyBinding(key);
        }
        MinecraftForge.EVENT_BUS.register(new NovoClient());
        MinecraftForge.EVENT_BUS.register(CpsTracker.INSTANCE);
        MinecraftForge.EVENT_BUS.register(new ShaderController());
        for (Module m : MODULES) {
            MinecraftForge.EVENT_BUS.register(m);
        }
        apply();
        if (DevAutoTest.active()) {
            MinecraftForge.EVENT_BUS.register(new DevAutoTest());
        }
        if (DevBenchmark.active()) {
            MinecraftForge.EVENT_BUS.register(new DevBenchmark());
        }
        if (DevModuleCheck.active()) {
            MinecraftForge.EVENT_BUS.register(new DevModuleCheck());
        }
        LOG.info("[NovoMC] Loaded {} modules; particle limiter mixin active: {}", MODULES.size(), mixinActive());
    }

    /** Whether MixinEffectRenderer was applied (its handler exists on the target). */
    private static boolean mixinActive() {
        for (java.lang.reflect.Method m : net.minecraft.client.particle.EffectRenderer.class.getDeclaredMethods()) {
            if (m.getName().contains("limitParticles")) {
                return true;
            }
        }
        return false;
    }

    public static ModConfig config() {
        return config;
    }

    public static List<Module> modules() {
        return Collections.unmodifiableList(MODULES);
    }

    public static List<HudModule> hudModules() {
        List<HudModule> out = new ArrayList<HudModule>();
        for (Module m : MODULES) {
            if (m instanceof HudModule) {
                out.add((HudModule) m);
            }
        }
        return out;
    }

    /** Brings every module in line with the config. */
    public static void apply() {
        for (Module m : MODULES) {
            m.setEnabled(config.enabled(m.def));
            if (m.isEnabled()) {
                m.onSettingsChanged();
            }
        }
    }

    public static void save() {
        config.save();
    }

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        // Pick up edits from the launcher's Mod Library about once a second.
        if (++ticks % 20 == 0 && config.reloadIfChanged()) {
            apply();
        }
        for (Module m : MODULES) {
            if (m.isEnabled()) {
                m.onTick();
            }
        }
        if (MENU_KEY.isPressed() && mc.currentScreen == null && mc.theWorld != null) {
            mc.displayGuiScreen(new GuiHudEditor());
        }
    }

    @SubscribeEvent
    public void onOverlay(RenderGameOverlayEvent.Post event) {
        if (event.type != RenderGameOverlayEvent.ElementType.ALL) {
            return;
        }
        if (mc.gameSettings.hideGUI || mc.currentScreen instanceof GuiHudEditor || mc.currentScreen instanceof com.novomc.client.gui.GuiModules) {
            return;
        }
        ScaledResolution sr = new ScaledResolution(mc);
        for (HudModule h : hudModules()) {
            if (h.isEnabled() && h.visible()) {
                draw(h, sr, false);
            }
        }
    }

    /** On-screen box of a HUD module: {x, y, width, height}, kept on screen. */
    public static int[] bounds(HudModule h, ScaledResolution sr, boolean preview) {
        // Sized with the HUD font, wherever it's measured from.
        boolean wasHud = Draw.inHud;
        Draw.inHud = true;
        float s = h.scale();
        int w = Math.round(h.width(preview) * s);
        int ht = Math.round(h.height(preview) * s);
        Draw.inHud = wasHud;
        int sw = sr.getScaledWidth();
        int sh = sr.getScaledHeight();
        int x = Math.round(h.x() * sw - (h.def.centered ? w / 2f : 0));
        x = Math.max(0, Math.min(sw - w, x));
        int y = h.stacked() ? stackTop(h, sh, preview) : Math.round(h.y() * sh);
        y = Math.max(0, Math.min(sh - ht, y));
        return new int[] {x, y, w, ht};
    }

    /** Top of a stacked module: below every shown module before it in its column. */
    private static int stackTop(HudModule target, int screenHeight, boolean preview) {
        int y = Math.round(target.def.defaultY * screenHeight);
        for (HudModule m : hudModules()) {
            if (m == target) {
                break;
            }
            boolean shown = m.isEnabled() && (preview || m.visible());
            if (shown && m.stacked() && target.def.stack.equals(m.def.stack)) {
                y += Math.round(m.height(preview) * m.scale()) + 2;
            }
        }
        return y;
    }

    public static void draw(HudModule h, ScaledResolution sr, boolean preview) {
        Draw.inHud = true;
        int[] b = bounds(h, sr, preview);
        GlStateManager.pushMatrix();
        GlStateManager.translate(b[0], b[1], 0);
        float s = h.scale();
        GlStateManager.scale(s, s, 1);
        h.render(preview);
        GlStateManager.popMatrix();
        GlStateManager.color(1, 1, 1, 1);
        Draw.inHud = false;
    }
}
