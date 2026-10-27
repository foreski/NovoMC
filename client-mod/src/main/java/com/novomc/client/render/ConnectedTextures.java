package com.novomc.client.render;

import com.novomc.client.mixin.accessor.AbstractResourcePackAccessor;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import javax.imageio.ImageIO;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.resources.IResource;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.client.resources.IResourcePack;
import net.minecraft.client.resources.ResourcePackRepository;
import net.minecraft.client.resources.model.IBakedModel;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.IBlockAccess;
import net.minecraftforge.client.event.TextureStitchEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Connected Textures, inspired by OptiFine/MCPatcher CTM; NovoMC's own code.
 *
 * <p>Glass and stained glass connect out of the box: their 47 border variants are
 * generated from whatever glass texture the active packs provide. Resource packs
 * can also ship their own tiles in the CTM properties format
 * ({@code assets/minecraft/optifine/ctm/**.properties} or {@code mcpatcher/ctm}),
 * with {@code method=ctm}, {@code horizontal} or {@code vertical}.
 */
public final class ConnectedTextures {
    private static final Logger LOG = LogManager.getLogger("NovoMC CTM");
    private static final String[] GLASS = {"glass", "glass_white", "glass_orange", "glass_magenta", "glass_light_blue",
        "glass_yellow", "glass_lime", "glass_pink", "glass_gray", "glass_silver", "glass_cyan", "glass_purple",
        "glass_blue", "glass_brown", "glass_green", "glass_red", "glass_black"};

    // Neighbour bits around a face, in the face's own texture space:
    // 128 64  32
    //  1   *  16
    //  2   4   8
    private static final int L = 1, DL = 2, D = 4, DR = 8, R = 16, UR = 32, U = 64, UL = 128;

    /** Canonical neighbour mask (corners only kept when both edges beside them connect) to CTM template tile. */
    private static final int[] CANONICAL = {0, 0, 1, 3, 4, 12, 5, 5, 7, 15, 16, 1, 17, 2, 20, 4, 21, 7, 23, 29, 28, 13,
        29, 31, 31, 14, 64, 36, 65, 17, 68, 24, 69, 19, 71, 43, 80, 16, 81, 18, 84, 6, 85, 46, 87, 21, 92, 28, 93, 9,
        95, 22, 112, 37, 113, 40, 116, 30, 117, 8, 119, 34, 124, 25, 125, 23, 127, 45, 193, 39, 197, 41, 199, 27, 209,
        42, 213, 20, 215, 10, 221, 35, 223, 44, 241, 38, 245, 11, 247, 32, 253, 33, 255, 26};
    private static final int[] TILE_FOR_MASK = new int[256];
    private static final int[] MASK_FOR_TILE = new int[47];

    /** For each face (EnumFacing order), the world directions of texture-left, down, right and up. */
    private static final EnumFacing[][] AXES = {
        {EnumFacing.WEST, EnumFacing.SOUTH, EnumFacing.EAST, EnumFacing.NORTH}, // down
        {EnumFacing.WEST, EnumFacing.SOUTH, EnumFacing.EAST, EnumFacing.NORTH}, // up
        {EnumFacing.EAST, EnumFacing.DOWN, EnumFacing.WEST, EnumFacing.UP},     // north
        {EnumFacing.WEST, EnumFacing.DOWN, EnumFacing.EAST, EnumFacing.UP},     // south
        {EnumFacing.NORTH, EnumFacing.DOWN, EnumFacing.SOUTH, EnumFacing.UP},   // west
        {EnumFacing.SOUTH, EnumFacing.DOWN, EnumFacing.NORTH, EnumFacing.UP},   // east
    };

    static {
        int[] canon = new int[256];
        for (int i = 0; i < CANONICAL.length; i += 2) {
            canon[CANONICAL[i]] = CANONICAL[i + 1];
            MASK_FOR_TILE[CANONICAL[i + 1]] = CANONICAL[i];
        }
        for (int m = 0; m < 256; m++) {
            TILE_FOR_MASK[m] = canon[canonical(m)];
        }
    }

    private static int canonical(int m) {
        int c = m & (L | D | R | U);
        if ((m & DL) != 0 && (m & L) != 0 && (m & D) != 0) c |= DL;
        if ((m & DR) != 0 && (m & D) != 0 && (m & R) != 0) c |= DR;
        if ((m & UR) != 0 && (m & R) != 0 && (m & U) != 0) c |= UR;
        if ((m & UL) != 0 && (m & U) != 0 && (m & L) != 0) c |= UL;
        return c;
    }

    private static volatile boolean enabled;
    /** Whether the atlas was last stitched with tiles; null before the first stitch. */
    private static Boolean stitchedWith;
    private static volatile Map<TextureAtlasSprite, Override> bySprite = new HashMap<TextureAtlasSprite, Override>();
    private static volatile List<Override> byBlock = new ArrayList<Override>();
    private static final List<Override> pending = new ArrayList<Override>();

    public ConnectedTextures() {}

    public static boolean active() {
        return enabled && stitchedWith == Boolean.TRUE;
    }

    /** Turns tiles on or off; they live in the atlas, so a change re-stitches it. */
    public static void setEnabled(boolean on) {
        enabled = on;
        Boolean stitched = stitchedWith;
        if (stitched == null && atlasLoaded()) {
            // Stitched before this listener was registered: without tiles.
            stitched = false;
        }
        if (stitched != null && stitched != on) {
            Minecraft.getMinecraft().scheduleResourcesRefresh();
        }
    }

    private static boolean atlasLoaded() {
        TextureMap map = Minecraft.getMinecraft().getTextureMapBlocks();
        return map != null && map.getAtlasSprite("minecraft:blocks/stone") != map.getMissingSprite();
    }

    /** One CTM rule: which textures or blocks it matches and its tiles. */
    static final class Override {
        final String method;
        final Set<String> matchTiles = new HashSet<String>();
        final Map<Block, Set<Integer>> matchBlocks = new HashMap<Block, Set<Integer>>();
        final boolean connectTile;
        final Set<EnumFacing> faces;
        final String[] tileNames;
        TextureAtlasSprite[] tiles;
        /** Built-in glass: connect only to the identical block state. */
        final boolean sameState;

        Override(String method, boolean connectTile, Set<EnumFacing> faces, String[] tileNames, boolean sameState) {
            this.method = method;
            this.connectTile = connectTile;
            this.faces = faces;
            this.tileNames = tileNames;
            this.sameState = sameState;
        }

        boolean matches(IBlockState state) {
            Set<Integer> metas = matchBlocks.get(state.getBlock());
            if (metas == null) {
                return false;
            }
            return metas.isEmpty() || metas.contains(state.getBlock().getMetaFromState(state));
        }
    }

    // ---- Stitching ----

    @SubscribeEvent
    public void onStitchPre(TextureStitchEvent.Pre event) {
        stitchedWith = enabled;
        pending.clear();
        if (!enabled) {
            return;
        }
        TextureMap map = event.map;
        try {
            loadPackOverrides();
        } catch (RuntimeException e) {
            LOG.error("Could not read CTM properties", e);
        }
        for (String glass : GLASS) {
            String[] names = new String[47];
            for (int t = 0; t < 47; t++) {
                names[t] = "novomc:ctm/" + glass + "/" + t;
                map.setTextureEntry(names[t], new GlassTile(names[t], glass, MASK_FOR_TILE[t]));
            }
            Override o = new Override("ctm", false, null, names, true);
            o.matchTiles.add("minecraft:blocks/" + glass);
            pending.add(o);
        }
        for (Override o : pending) {
            if (o.tileNames.length > 0 && o.tileNames[0].startsWith("novomc:ctmpack/")) {
                for (String name : o.tileNames) {
                    map.setTextureEntry(name, new PackTile(name));
                }
            }
        }
    }

    @SubscribeEvent
    public void onStitchPost(TextureStitchEvent.Post event) {
        TextureMap map = event.map;
        BlockTweaks.spritesReady(map);
        Map<TextureAtlasSprite, Override> sprites = new HashMap<TextureAtlasSprite, Override>();
        List<Override> blocks = new ArrayList<Override>();
        // Packs first so their own glass tiles win over the generated ones.
        for (Override o : pending) {
            o.tiles = new TextureAtlasSprite[o.tileNames.length];
            for (int i = 0; i < o.tiles.length; i++) {
                o.tiles[i] = map.getAtlasSprite(o.tileNames[i]);
            }
            for (String tile : o.matchTiles) {
                TextureAtlasSprite s = map.getAtlasSprite(tile);
                if (s != map.getMissingSprite() && !sprites.containsKey(s)) {
                    sprites.put(s, o);
                }
            }
            if (!o.matchBlocks.isEmpty()) {
                blocks.add(o);
            }
        }
        bySprite = sprites;
        byBlock = blocks;
    }

    /** Reads CTM properties from every active resource pack, top pack first. */
    private static void loadPackOverrides() {
        ResourcePackRepository repo = Minecraft.getMinecraft().getResourcePackRepository();
        List<IResourcePack> packs = new ArrayList<IResourcePack>();
        if (repo.getResourcePackInstance() != null) {
            packs.add(repo.getResourcePackInstance());
        }
        List<ResourcePackRepository.Entry> entries = repo.getRepositoryEntries();
        for (int i = entries.size() - 1; i >= 0; i--) {
            packs.add(entries.get(i).getResourcePack());
        }
        int id = 0;
        for (IResourcePack pack : packs) {
            if (!(pack instanceof AbstractResourcePackAccessor)) {
                continue;
            }
            for (String path : listProperties(((AbstractResourcePackAccessor) pack).novomc$getFile())) {
                try {
                    Override o = parse(path, id++);
                    if (o != null) {
                        pending.add(o);
                    }
                } catch (IOException | RuntimeException e) {
                    LOG.warn("Skipping {}: {}", path, e.toString());
                }
            }
        }
    }

    private static List<String> listProperties(File file) {
        List<String> out = new ArrayList<String>();
        if (file.isDirectory()) {
            for (String root : new String[] {"assets/minecraft/optifine/ctm", "assets/minecraft/mcpatcher/ctm"}) {
                walk(new File(file, root), root, out);
            }
        } else if (file.isFile()) {
            try (ZipFile zip = new ZipFile(file)) {
                Enumeration<? extends ZipEntry> e = zip.entries();
                while (e.hasMoreElements()) {
                    String name = e.nextElement().getName();
                    if ((name.startsWith("assets/minecraft/optifine/ctm/") || name.startsWith("assets/minecraft/mcpatcher/ctm/"))
                            && name.endsWith(".properties")) {
                        out.add(name);
                    }
                }
            } catch (IOException ignored) {
                // Not a zip pack.
            }
        }
        return out;
    }

    private static void walk(File dir, String rel, List<String> out) {
        File[] files = dir.listFiles();
        if (files == null) {
            return;
        }
        for (File f : files) {
            if (f.isDirectory()) {
                walk(f, rel + "/" + f.getName(), out);
            } else if (f.getName().endsWith(".properties")) {
                out.add(rel + "/" + f.getName());
            }
        }
    }

    private static Override parse(String path, int id) throws IOException {
        // path is assets/minecraft/<dir>/<name>.properties
        String inAssets = path.substring("assets/minecraft/".length());
        String dir = inAssets.substring(0, inAssets.lastIndexOf('/'));
        Properties p = new Properties();
        IResource res = Minecraft.getMinecraft().getResourceManager().getResource(new ResourceLocation("minecraft", inAssets));
        try (InputStream in = res.getInputStream()) {
            p.load(in);
        }
        String method = p.getProperty("method", "ctm").trim();
        if (method.equals("glass")) {
            method = "ctm";
        }
        if (!method.equals("ctm") && !method.equals("horizontal") && !method.equals("vertical")) {
            return null;
        }
        List<String> tiles = new ArrayList<String>();
        for (String t : p.getProperty("tiles", "").trim().split("\\s+")) {
            if (t.isEmpty()) {
                continue;
            }
            int dash = t.indexOf('-');
            if (dash > 0 && t.matches("\\d+-\\d+")) {
                for (int i = Integer.parseInt(t.substring(0, dash)); i <= Integer.parseInt(t.substring(dash + 1)); i++) {
                    tiles.add(dir + "/" + i);
                }
            } else {
                String n = t.endsWith(".png") ? t.substring(0, t.length() - 4) : t;
                tiles.add(n.contains("/") ? n : dir + "/" + n);
            }
        }
        int need = method.equals("ctm") ? 47 : 4;
        if (tiles.size() < need) {
            return null;
        }
        String[] names = new String[need];
        for (int i = 0; i < need; i++) {
            names[i] = "novomc:ctmpack/" + tiles.get(i);
        }
        Set<EnumFacing> faces = null;
        String facesProp = p.getProperty("faces");
        if (facesProp != null) {
            faces = new HashSet<EnumFacing>();
            for (String f : facesProp.trim().toLowerCase().split("\\s+")) {
                if (f.equals("sides")) {
                    for (EnumFacing h : EnumFacing.Plane.HORIZONTAL) faces.add(h);
                } else if (f.equals("all")) {
                    for (EnumFacing a : EnumFacing.VALUES) faces.add(a);
                } else if (f.equals("top")) {
                    faces.add(EnumFacing.UP);
                } else if (f.equals("bottom")) {
                    faces.add(EnumFacing.DOWN);
                } else {
                    EnumFacing ef = EnumFacing.byName(f);
                    if (ef != null) faces.add(ef);
                }
            }
        }
        boolean hasTiles = p.getProperty("matchTiles") != null;
        boolean connectTile = "tile".equals(p.getProperty("connect", hasTiles ? "tile" : "block").trim());
        Override o = new Override(method, connectTile, faces, names, false);
        if (hasTiles) {
            for (String t : p.getProperty("matchTiles").trim().split("\\s+")) {
                o.matchTiles.add(t.contains(":") ? t : "minecraft:blocks/" + t);
            }
        }
        String blocks = p.getProperty("matchBlocks");
        if (blocks == null) {
            // MCPatcher style: block<id>.properties or a file named after the block.
            String file = inAssets.substring(inAssets.lastIndexOf('/') + 1, inAssets.length() - ".properties".length());
            blocks = file.startsWith("block") ? file.substring(5) : null;
            if (blocks == null && !hasTiles) {
                o.matchTiles.add("minecraft:blocks/" + file);
            }
        }
        if (blocks != null) {
            Set<Integer> metas = new HashSet<Integer>();
            String metaProp = p.getProperty("metadata");
            if (metaProp != null) {
                for (String m : metaProp.trim().split("\\s+")) {
                    metas.add(Integer.parseInt(m));
                }
            }
            for (String b : blocks.trim().split("\\s+")) {
                Block block = b.matches("\\d+") ? Block.getBlockById(Integer.parseInt(b)) : Block.getBlockFromName(b);
                if (block != null) {
                    o.matchBlocks.put(block, metas);
                }
            }
        }
        return o;
    }

    // ---- Building ----

    /** Swaps quads of matching textures for the tile their neighbours call for. */
    static IBakedModel apply(IBakedModel model, IBlockState state, IBlockAccess world, BlockPos pos) {
        Map<TextureAtlasSprite, Override> sprites = bySprite;
        List<Override> blocks = byBlock;
        Override blockRule = null;
        for (Override o : blocks) {
            if (o.matches(state)) {
                blockRule = o;
                break;
            }
        }
        TextureAtlasSprite particle = model.getParticleTexture();
        if (blockRule == null && (particle == null || !sprites.containsKey(particle))) {
            return model;
        }
        Quads.Model out = null;
        for (EnumFacing face : EnumFacing.VALUES) {
            List<BakedQuad> quads = model.getFaceQuads(face);
            for (int i = 0; i < quads.size(); i++) {
                BakedQuad q = quads.get(i);
                Override rule = blockRule;
                TextureAtlasSprite from = null;
                if (rule == null) {
                    from = particle;
                    rule = particle != null ? sprites.get(particle) : null;
                    if (rule == null || Quads.spriteOf(q, particle) == null) {
                        continue;
                    }
                } else {
                    from = particle;
                    if (from == null || Quads.spriteOf(q, from) == null) {
                        continue;
                    }
                }
                if (rule.faces != null && !rule.faces.contains(face)) {
                    continue;
                }
                int tile = tileFor(rule, state, from, world, pos, face);
                if (tile < 0 || tile >= rule.tiles.length) {
                    continue;
                }
                if (out == null) {
                    out = new Quads.Model(model);
                }
                out.face(face).set(i, Quads.retexture(q, from, rule.tiles[tile], q.getTintIndex()));
            }
        }
        return out != null ? out : model;
    }

    private static int tileFor(Override rule, IBlockState state, TextureAtlasSprite sprite, IBlockAccess world, BlockPos pos,
                               EnumFacing face) {
        EnumFacing[] axes = AXES[face.ordinal()];
        EnumFacing left = axes[0], down = axes[1], right = axes[2], up = axes[3];
        if (rule.method.equals("horizontal")) {
            if (face.getAxis() == EnumFacing.Axis.Y) {
                return -1;
            }
            int m = (connects(rule, state, sprite, world, pos.offset(left)) ? 1 : 0)
                    | (connects(rule, state, sprite, world, pos.offset(right)) ? 2 : 0);
            return new int[] {3, 2, 0, 1}[m];
        }
        if (rule.method.equals("vertical")) {
            if (face.getAxis() == EnumFacing.Axis.Y) {
                return -1;
            }
            int m = (connects(rule, state, sprite, world, pos.offset(down)) ? 1 : 0)
                    | (connects(rule, state, sprite, world, pos.offset(up)) ? 2 : 0);
            return new int[] {3, 2, 0, 1}[m];
        }
        int m = 0;
        if (connects(rule, state, sprite, world, pos.offset(left))) m |= L;
        if (connects(rule, state, sprite, world, pos.offset(left).offset(down))) m |= DL;
        if (connects(rule, state, sprite, world, pos.offset(down))) m |= D;
        if (connects(rule, state, sprite, world, pos.offset(right).offset(down))) m |= DR;
        if (connects(rule, state, sprite, world, pos.offset(right))) m |= R;
        if (connects(rule, state, sprite, world, pos.offset(right).offset(up))) m |= UR;
        if (connects(rule, state, sprite, world, pos.offset(up))) m |= U;
        if (connects(rule, state, sprite, world, pos.offset(left).offset(up))) m |= UL;
        return TILE_FOR_MASK[m];
    }

    private static boolean connects(Override rule, IBlockState state, TextureAtlasSprite sprite, IBlockAccess world, BlockPos pos) {
        IBlockState other = world.getBlockState(pos);
        if (rule.sameState) {
            return other == state;
        }
        if (rule.connectTile) {
            return Minecraft.getMinecraft().getBlockRendererDispatcher().getBlockModelShapes().getTexture(other) == sprite;
        }
        return other.getBlock() == state.getBlock()
                && other.getBlock().getMetaFromState(other) == state.getBlock().getMetaFromState(state);
    }

    // ---- Tile sprites ----

    private static BufferedImage firstFrame(ResourceLocation file) throws IOException {
        IResourceManager rm = Minecraft.getMinecraft().getResourceManager();
        try (InputStream in = rm.getResource(file).getInputStream()) {
            BufferedImage img = ImageIO.read(in);
            int w = img.getWidth();
            return img.getHeight() > w ? img.getSubimage(0, 0, w, w) : img;
        }
    }

    /** The image as level 0, with empty slots that mipmap generation fills. */
    private static BufferedImage[] levels(BufferedImage img) {
        BufferedImage[] out = new BufferedImage[1 + Minecraft.getMinecraft().gameSettings.mipmapLevels];
        out[0] = img;
        return out;
    }

    /** A pack-supplied tile, read from {@code assets/minecraft/<path>.png}. */
    private static final class PackTile extends TextureAtlasSprite {
        private final String path;

        PackTile(String name) {
            super(name);
            path = name.substring("novomc:ctmpack/".length());
        }

        @java.lang.Override
        public boolean hasCustomLoader(IResourceManager manager, ResourceLocation location) {
            return true;
        }

        @java.lang.Override
        public boolean load(IResourceManager manager, ResourceLocation location) {
            try {
                BufferedImage img = firstFrame(path.contains(":") ? new ResourceLocation(path + ".png")
                        : new ResourceLocation("minecraft", path + ".png"));
                loadSprite(levels(img), null);
                return false;
            } catch (IOException | RuntimeException e) {
                LOG.warn("Missing CTM tile {}", path);
                return true;
            }
        }
    }

    /**
     * A generated glass tile: the pack's glass texture with its frame removed on
     * the sides (and corners) that connect, filled from just inside the frame.
     */
    private static final class GlassTile extends TextureAtlasSprite {
        private final String glass;
        private final int mask;

        GlassTile(String name, String glass, int mask) {
            super(name);
            this.glass = glass;
            this.mask = mask;
        }

        @java.lang.Override
        public boolean hasCustomLoader(IResourceManager manager, ResourceLocation location) {
            return true;
        }

        @java.lang.Override
        public boolean load(IResourceManager manager, ResourceLocation location) {
            try {
                BufferedImage src = firstFrame(new ResourceLocation("minecraft", "textures/blocks/" + glass + ".png"));
                loadSprite(levels(connect(src, mask)), null);
                return false;
            } catch (IOException | RuntimeException e) {
                LOG.warn("Could not build connected {}: {}", glass, e.toString());
                return true;
            }
        }

        private static BufferedImage connect(BufferedImage src, int mask) {
            int n = src.getWidth();
            int b = Math.max(1, n / 16);
            BufferedImage out = new BufferedImage(n, n, BufferedImage.TYPE_INT_ARGB);
            for (int y = 0; y < n; y++) {
                for (int x = 0; x < n; x++) {
                    out.setRGB(x, y, src.getRGB(x, y));
                }
            }
            boolean l = (mask & L) != 0, r = (mask & R) != 0, u = (mask & U) != 0, d = (mask & D) != 0;
            for (int y = 0; y < n; y++) {
                for (int x = 0; x < n; x++) {
                    boolean left = x < b, right = x >= n - b, top = y < b, bottom = y >= n - b;
                    boolean open;
                    if ((left || right) && (top || bottom)) {
                        boolean side = left ? l : r;
                        boolean vert = top ? u : d;
                        int corner = top ? (left ? UL : UR) : (left ? DL : DR);
                        open = side && vert && (mask & corner) != 0;
                    } else if (left || right) {
                        open = left ? l : r;
                    } else if (top || bottom) {
                        open = top ? u : d;
                    } else {
                        continue;
                    }
                    if (open) {
                        // Mirror the frame strip from just inside it.
                        int sx = left ? x + b : right ? x - b : x;
                        int sy = top ? y + b : bottom ? y - b : y;
                        out.setRGB(x, y, src.getRGB(sx, sy));
                    }
                }
            }
            return out;
        }
    }
}
