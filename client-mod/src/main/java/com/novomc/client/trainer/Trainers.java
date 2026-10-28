package com.novomc.client.trainer;

import com.novomc.client.gui.GuiTrainers;
import com.novomc.client.util.Draw;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.world.GameRules;
import net.minecraft.world.WorldServer;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.WorldType;
import net.minecraftforge.client.event.MouseEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.InputEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.input.Keyboard;

/**
 * Novo Client's trainers: practice drills in a private singleplayer world
 * ({@code saves/novo-trainer}). Starting a drill opens (or creates) that
 * world, builds the drill's arena and runs it; scores go to
 * {@link TrainerScores}. In the trainer world, R restarts and T picks
 * another drill.
 */
public final class Trainers {
    public static final Trainers INSTANCE = new Trainers();
    public static final String FOLDER = "novo-trainer";
    private static final int COUNTDOWN_TICKS = 60;

    private enum State { IDLE, COUNTDOWN, RUNNING, RESULTS }

    private final Minecraft mc = Minecraft.getMinecraft();
    private volatile Drill active;
    private volatile State state = State.IDLE;
    private String pendingId;
    private int countdown;
    private float lastScore;
    private boolean newBest;

    private Trainers() {}

    /** The drills that run in the trainer world (the click test is a screen of its own). */
    public static List<Drill> drills() {
        List<Drill> list = new ArrayList<Drill>();
        list.add(new AimDrill());
        list.add(new BridgeDrill());
        list.add(new PotDrill());
        return list;
    }

    private static Drill create(String id) {
        for (Drill d : drills()) {
            if (d.id.equals(id)) {
                return d;
            }
        }
        throw new IllegalArgumentException("No drill " + id);
    }

    public boolean inTrainerWorld() {
        IntegratedServer server = mc.getIntegratedServer();
        return mc.theWorld != null && server != null && FOLDER.equals(server.getFolderName());
    }

    /** Starts a drill, opening the trainer world first if needed. */
    public void start(String id) {
        pendingId = id;
        state = State.IDLE;
        active = null;
        if (inTrainerWorld()) {
            mc.displayGuiScreen(null);
            return;
        }
        if (mc.theWorld != null) {
            mc.theWorld.sendQuittingDisconnectingPacket();
            mc.loadWorld((WorldClient) null);
        }
        WorldSettings ws = new WorldSettings(0L, WorldSettings.GameType.SURVIVAL, false, false, WorldType.FLAT);
        ws.enableCommands();
        mc.launchIntegratedServer(FOLDER, "Novo Trainer", ws);
    }

    private void begin(String id) {
        final Drill drill = create(id);
        final IntegratedServer server = mc.getIntegratedServer();
        final java.util.UUID uuid = mc.thePlayer.getUniqueID();
        server.addScheduledTask(new Runnable() {
            public void run() {
                WorldServer world = server.worldServerForDimension(0);
                GameRules rules = world.getGameRules();
                rules.setOrCreateGameRule("doDaylightCycle", "false");
                rules.setOrCreateGameRule("doMobSpawning", "false");
                rules.setOrCreateGameRule("keepInventory", "true");
                world.setWorldTime(6000);
                // Leftover targets from an earlier run.
                for (Entity e : new ArrayList<Entity>(world.loadedEntityList)) {
                    if (!(e instanceof EntityPlayer)) {
                        e.setDead();
                    }
                }
                EntityPlayerMP player = server.getConfigurationManager().getPlayerByUUID(uuid);
                if (player == null) {
                    return;
                }
                player.setGameType(WorldSettings.GameType.SURVIVAL);
                player.setHealth(player.getMaxHealth());
                player.getFoodStats().addStats(20, 20f);
                player.fallDistance = 0;
                drill.setup(world, player);
            }
        });
        active = drill;
        countdown = COUNTDOWN_TICKS;
        state = drill instanceof AimDrill ? State.COUNTDOWN : State.RUNNING;
    }

    private void finish() {
        Drill drill = active;
        if (drill instanceof BridgeDrill && ((BridgeDrill) drill).fell()) {
            begin(drill.id);
            return;
        }
        lastScore = drill.score();
        newBest = TrainerScores.record(drill.id, lastScore, drill.lowerIsBetter);
        state = State.RESULTS;
    }

    // ---- Events ----

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (!inTrainerWorld()) {
            if (mc.theWorld == null && pendingId == null) {
                state = State.IDLE;
                active = null;
            }
            return;
        }
        if (pendingId != null && mc.thePlayer != null) {
            String id = pendingId;
            pendingId = null;
            begin(id);
            return;
        }
        if (state == State.COUNTDOWN && --countdown <= 0) {
            state = State.RUNNING;
        } else if (state == State.RUNNING && active.tick()) {
            finish();
        }
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        Drill drill = active;
        IntegratedServer server = mc.getIntegratedServer();
        if (event.phase == TickEvent.Phase.END && drill != null && state != State.IDLE && server != null
                && FOLDER.equals(server.getFolderName())) {
            drill.serverTick(server.worldServerForDimension(0));
        }
    }

    /** Server side: a hit landed on something. */
    @SubscribeEvent
    public void onHurt(LivingHurtEvent event) {
        Drill drill = active;
        if (drill instanceof AimDrill && state == State.RUNNING && !event.entity.worldObj.isRemote) {
            ((AimDrill) drill).landed(event.entity.getEntityId());
        }
    }

    /** Client side: the player attacked an entity. */
    @SubscribeEvent
    public void onAttack(AttackEntityEvent event) {
        Drill drill = active;
        if (drill instanceof AimDrill && state == State.RUNNING && event.entityPlayer.worldObj.isRemote) {
            ((AimDrill) drill).attacked(event.target.getEntityId());
        }
    }

    @SubscribeEvent
    public void onMouse(MouseEvent event) {
        Drill drill = active;
        if (event.button == 0 && event.buttonstate && drill instanceof AimDrill && state == State.RUNNING && mc.currentScreen == null) {
            ((AimDrill) drill).swung();
        }
    }

    @SubscribeEvent
    public void onKey(InputEvent.KeyInputEvent event) {
        if (!inTrainerWorld() || mc.currentScreen != null || !Keyboard.getEventKeyState()) {
            return;
        }
        if (Keyboard.getEventKey() == Keyboard.KEY_R && active != null) {
            begin(active.id);
        } else if (Keyboard.getEventKey() == Keyboard.KEY_T) {
            mc.displayGuiScreen(new GuiTrainers(null));
        }
    }

    @SubscribeEvent
    public void onOverlay(RenderGameOverlayEvent.Post event) {
        Drill drill = active;
        if (event.type != RenderGameOverlayEvent.ElementType.ALL || drill == null || !inTrainerWorld()) {
            return;
        }
        ScaledResolution sr = new ScaledResolution(mc);
        int cx = sr.getScaledWidth() / 2;
        List<String> lines = new ArrayList<String>();
        String title = drill.name;
        if (state == State.COUNTDOWN) {
            lines.add("Starting in " + (countdown / 20 + 1) + "...");
        } else if (state == State.RUNNING) {
            lines.addAll(drill.status());
        } else if (state == State.RESULTS) {
            title = drill.name + (newBest ? "  ·  NEW BEST!" : "");
            lines.add(drill.format(lastScore));
            lines.addAll(drill.details());
            Float best = TrainerScores.best(drill.id);
            if (best != null && !newBest) {
                lines.add("Best: " + drill.format(best));
            }
        }
        lines.add(state == State.RESULTS ? "R  retry   ·   T  other drills" : "R  restart   ·   T  drills");
        int w = Draw.width(title) + 24;
        for (String l : lines) {
            w = Math.max(w, Draw.width(l) + 24);
        }
        int h = 22 + lines.size() * 11;
        float x = cx - w / 2f;
        float y = 8;
        Draw.round(x, y, x + w, y + h, 8, 0xE00C1019);
        Draw.roundOutline(x, y, x + w, y + h, 8, newBest && state == State.RESULTS ? 0xCC4ADE80 : 0x995B8CFF);
        Draw.text(title, cx - Draw.width(title) / 2f, y + 7, newBest && state == State.RESULTS ? 0xFF4ADE80 : 0xFF22D3EE);
        for (int i = 0; i < lines.size(); i++) {
            String l = lines.get(i);
            boolean hint = i == lines.size() - 1;
            Draw.text(l, cx - Draw.width(l) / 2f, y + 20 + i * 11, hint ? 0xFF8A93A6 : 0xFFE8ECF4);
        }
    }
}
