import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Map;

/**
 * Generates the block and item textures, in Portable Beacons' way: drawn by code, so a colour or a
 * proportion is changed here and every texture follows.
 *
 * <p>Both blocks share one steel panel - outline, lit top and left, dark bottom and right - so
 * the table and its lasers read as one machine. What sets each face apart is drawn on the panel:
 * the table's focus, where the beams meet; the laser's socket; vents on the sides. What moves or
 * lights up - the laser's head, the focus at work - is drawn by the renderers over these.
 *
 * <p>The augments are drawn in FactoryIO's module family - a dark casing round a screen - so an
 * augment reads as a part you fit into something.
 *
 * <pre>java tools/GenerateTextures.java</pre>
 */
public final class GenerateTextures {

    private static final String ROOT = "src/main/resources/assets/laserassembly/textures/";

    private static final int RIM = 0x2C3038;
    private static final int BEVEL = 0x8A939E;
    private static final int BEVEL_LIGHT = 0x9AA3AE;
    private static final int SHADOW = 0x3A4048;
    private static final int PLATE = 0x5E6670;
    private static final int PLATE_DARK = 0x4A515A;
    private static final int DARK = 0x1A1C20;
    /** The copper band every block of the mod wears: the table, the laser's base and its head. Same as {@link #STEEL}'s. */
    private static final int COPPER = 0xD08A45;
    private static final int COPPER_DARK = 0x8E5426;

    public static void main(String[] args) throws IOException {
        new File(ROOT + "block").mkdirs();
        new File(ROOT + "item").mkdirs();

        write("block/assembly_table_top", tableTop());
        write("block/assembly_table_side", tableSide());
        write("block/assembly_table_bottom", panel());
        write("block/assembly_table_glow", tableGlow());
        write("block/laser_top", laserTop());
        write("block/laser_side", laserSide());
        write("block/laser_bottom", panel());
        write("block/laser_head", laserHead());
        write("block/laser_glow", laserGlow());

        writeItems();
        System.out.println("Textures written.");
    }

    // ------------------------------------------------------------------ blocks

    /*
     * Portable Beacons' way: each face is ASCII art over a palette, flat, three tones per material -
     * a lit edge, the body, a dark edge - and the round parts are drawn by hand, pixel by pixel. The
     * computed discs and the scattered grain they replaced looked soft beside the mods' other blocks.
     */

    private static final Map<Character, Integer> STEEL = Map.ofEntries(
            Map.entry('k', 0x24272D),   // outline
            Map.entry('H', 0x9AA3AE),   // lit edge
            Map.entry('M', 0x646C77),   // body
            Map.entry('d', 0x474D56),   // dark edge
            Map.entry('b', 0xD0D7DE),   // bolt
            Map.entry('c', 0xD08A45),   // copper, lit
            Map.entry('C', 0x8E5426),   // copper, dark
            Map.entry('v', 0x121417),   // vent slit
            Map.entry('l', 0x7A838E),   // vent lip, catching the light
            Map.entry('x', 0x1C1E22),   // foot
            Map.entry('o', 0x17191D),   // dark well
            Map.entry('f', 0x4A3420),   // the focus, unlit
            Map.entry('F', 0x5E4228));  // its heart, unlit

    /** A bolted steel plate: outline, lit top and left, dark bottom and right, a bolt in each corner. */
    private static final String[] PANEL = {
            "kkkkkkkkkkkkkkkk",
            "kHHHHHHHHHHHHHHk",
            "kHbMMMMMMMMMMbdk",
            "kHMMMMMMMMMMMMdk",
            "kHMMMMMMMMMMMMdk",
            "kHMMMMMMMMMMMMdk",
            "kHMMMMMMMMMMMMdk",
            "kHMMMMMMMMMMMMdk",
            "kHMMMMMMMMMMMMdk",
            "kHMMMMMMMMMMMMdk",
            "kHMMMMMMMMMMMMdk",
            "kHMMMMMMMMMMMMdk",
            "kHMMMMMMMMMMMMdk",
            "kHbMMMMMMMMMMbdk",
            "kddddddddddddddk",
            "kkkkkkkkkkkkkkkk"};

    /**
     * The table's focus, 10x10 at the panel's centre: a copper ring - {@code r}, lit top-left and dark
     * bottom-right - round a dark well, the focus in it. Dim: it lights up through the glow.
     */
    private static final String[] FOCUS = {
            "..kkkkkk..",
            ".krrrrrrk.",
            "krroooorrk",
            "kroffffork",
            "krofFFfork",
            "krofFFfork",
            "kroffffork",
            "krroooorrk",
            ".krrrrrrk.",
            "..kkkkkk.."};

    /** The laser's socket: the same ring round an empty well, where the head turns. */
    private static final String[] SOCKET = {
            "..kkkkkk..",
            ".krrrrrrk.",
            "krroooorrk",
            "kroooooork",
            "kroooooork",
            "kroooooork",
            "kroooooork",
            "krroooorrk",
            ".krrrrrrk.",
            "..kkkkkk.."};

    /** The focus lit, over the top at full brightness while the table works; clear elsewhere. */
    private static final Map<Character, Integer> GLOW = Map.of('o', 0x6A3A12, 'f', 0xE8862A, 'F', 0xFFE7A8);

    /**
     * A workbench's apron, top to bottom: a bolted plate, the copper band every block of the mod
     * wears, a vented panel, a foot.
     */
    private static final String[] TABLE_SIDE = {
            "kHHHHHHHHHHHHHHk",
            "kMbMMMMMMMMMMbMk",
            "kMMMMMMMMMMMMMMk",
            "kddddddddddddddk",
            "cccccccccccccccc",
            "CCCCCCCCCCCCCCCC",
            "kddddddddddddddk",
            "kddvvvvvvvvvvddk",
            "kddllllllllllddk",
            "kddvvvvvvvvvvddk",
            "kddllllllllllddk",
            "kddvvvvvvvvvvddk",
            "kddllllllllllddk",
            "kddddddddddddddk",
            "kkkkkkkkkkkkkkkk",
            "xxxxxxxxxxxxxxxx"};

    /** The base is 7 high: its sides read rows 9 to 15 - the lit edge, the copper band, vents, the foot. */
    private static final String[] LASER_SIDE = {
            "kddddddddddddddk",
            "kddddddddddddddk",
            "kddddddddddddddk",
            "kddddddddddddddk",
            "kddddddddddddddk",
            "kddddddddddddddk",
            "kddddddddddddddk",
            "kddddddddddddddk",
            "kddddddddddddddk",
            "kHHHHHHHHHHHHHHk",
            "kMbMMMMMMMMMMbMk",
            "cccccccccccccccc",
            "CCCCCCCCCCCCCCCC",
            "kddvdvdvdvdvdddk",
            "kkkkkkkkkkkkkkkk",
            "xxxxxxxxxxxxxxxx"};

    private static BufferedImage panel() {
        return ascii(PANEL, STEEL);
    }

    private static BufferedImage tableTop() {
        return overlay(panel(), FOCUS, STEEL);
    }

    private static BufferedImage tableGlow() {
        return overlay(blank(), FOCUS, GLOW);
    }

    private static BufferedImage tableSide() {
        return ascii(TABLE_SIDE, STEEL);
    }

    private static BufferedImage laserTop() {
        return overlay(panel(), SOCKET, STEEL);
    }

    private static BufferedImage laserSide() {
        return ascii(LASER_SIDE, STEEL);
    }

    /** A 16x16 face from its rows; {@code .} is left clear. Refuses a row of the wrong width or an unknown letter. */
    private static BufferedImage ascii(String[] rows, Map<Character, Integer> palette) {
        if (rows.length != 16) throw new IllegalArgumentException(rows.length + " rows");
        return overlay(blank(), rows, palette);
    }

    /**
     * {@code art} centred on {@code image}. {@code r} is the copper ring, lit on the top-left half of
     * the face and dark on the other, like every bevel of the mod; a letter the palette lacks is
     * skipped, which is how the glow keeps only the focus.
     */
    private static BufferedImage overlay(BufferedImage image, String[] art, Map<Character, Integer> palette) {
        int size = art.length, at = (16 - size) / 2;
        for (int y = 0; y < size; y++) {
            if (art[y].length() != size) throw new IllegalArgumentException("row " + y + " is " + art[y].length() + " wide");
            for (int x = 0; x < size; x++) {
                char ch = art[y].charAt(x);
                if (ch == '.') continue;
                int px = at + x, py = at + y;
                if (ch == 'r') ch = px + py < 15 ? 'c' : 'C';
                Integer rgb = palette.get(ch);
                if (rgb != null) set(image, px, py, rgb);
                else if (palette == STEEL) throw new IllegalArgumentException("unknown colour '" + ch + "' in row " + y);
            }
        }
        return image;
    }

    /**
     * The head, read by UV region: body side (0,0) 6x6 with a copper band, body cap (6,0) 6x6,
     * nozzle side (0,6) 4x2, nozzle tip (4,6) 4x4 - dark, the glow is drawn over it when firing.
     */
    private static BufferedImage laserHead() {
        BufferedImage image = blank();
        fill(image, 0, 0, 6, 6, SHADOW);
        fill(image, 0, 0, 6, 1, BEVEL_LIGHT);
        fill(image, 0, 1, 6, 1, PLATE);
        fill(image, 0, 2, 6, 1, COPPER);
        fill(image, 0, 3, 6, 1, COPPER_DARK);
        fill(image, 0, 5, 6, 1, RIM);
        fill(image, 6, 0, 6, 6, RIM);
        fill(image, 7, 1, 4, 4, PLATE);
        fill(image, 7, 1, 4, 1, BEVEL);
        fill(image, 8, 2, 2, 2, DARK);
        fill(image, 0, 6, 4, 2, DARK);
        fill(image, 0, 6, 4, 1, SHADOW);
        fill(image, 4, 6, 4, 4, SHADOW);
        fill(image, 5, 7, 2, 2, DARK);
        return image;
    }

    /** Greyscale, white at the heart: tinted with the beam's colour, so the heart stays hot. */
    private static BufferedImage laserGlow() {
        BufferedImage image = blank();
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                int grey = (int) Math.round(255 - 12 * Math.max(0, distance(x, y) - 2));
                set(image, x, y, grey << 16 | grey << 8 | grey);
            }
        }
        return image;
    }

    // ------------------------------------------------------------------ items

    /*
     * One texture per item, its colour baked in: each lens and augment is its own item, so it has
     * its own texture, and an item a pack adds brings its own. The colours are the datapack
     * entries' - kept in step by hand, they change rarely.
     */

    private static void writeItems() throws IOException {
        lens("cut_lens", 0xFF8C1A);
        lens("weld_lens", 0x3C8CFF);
        lens("engrave_lens", 0x3CE05A);

        augment("regulator_augment", 0xE8D44A, 0, "regulator");
        // Darker than the others: the three lens colours sit on it, and a pale screen washed them out.
        augment("barrel_augment", 0x6E6E8C, 0, "barrel");
        augment("range_augment_1", 0x4AC8E8, 1, "range");
        augment("range_augment_2", 0x2A8CD8, 2, "range");
        augment("efficiency_augment_1", 0x6AD86A, 1, "efficiency");
        augment("efficiency_augment_2", 0x2EA84A, 2, "efficiency");
    }

    /**
     * Glass in the beam's colour set in a steel ring: the ring lit top-left like the blocks' bevel,
     * the glass darker at its edge and brighter towards its heart, a curved glint along its upper
     * left and a small one opposite. A flat disc with a white dot read as a marble, not a lens.
     */
    private static void lens(String name, int colour) throws IOException {
        BufferedImage image = blank();
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                double d = distance(x, y);
                boolean lit = x + y < 15;
                if (d <= 4.9) {
                    // Brighter towards the centre and the top-left, never washed out to white.
                    set(image, x, y, shade(colour, 1.2 - 0.06 * d - 0.025 * (x + y - 15)));
                } else if (d <= 5.8) {
                    set(image, x, y, shade(colour, lit ? 0.55 : 0.4));
                } else if (d <= 6.8) {
                    set(image, x, y, lit ? BEVEL_LIGHT : PLATE_DARK);
                } else if (d <= 7.6) {
                    set(image, x, y, RIM);
                }
            }
        }
        for (int[] p : new int[][]{{4, 7}, {4, 6}, {5, 5}, {6, 4}, {7, 4}}) set(image, p[0], p[1], shade(0xFFFFFF, 0.97));
        set(image, 5, 6, shade(colour, 1.6));
        set(image, 10, 10, shade(colour, 1.5));
        write("item/" + name, image);
    }

    private static final String[] CASING = {
            "................",
            "..kkkkkkkkkkkk..",
            ".kLLLLLLLLLLLLk.",
            ".kLKKKKKKKKKKDk.",
            ".kLK........KDk.",
            ".kLK........KDk.",
            ".kLK........KDk.",
            ".kLK........KDk.",
            ".kLK........KDk.",
            ".kLK........KDk.",
            ".kLKKKKKKKKKKDk.",
            ".kDDDDDDDDDDDDk.",
            ".kDD11DDDD22DDk.",
            ".kSSSSSSSSSSSSk.",
            "..kkkkkkkkkkkk..",
            "................"};

    /**
     * 8x6 glyphs on the screen: shape carries the meaning, so two close colours stay apart. {@code w}
     * is white; {@code o}, {@code b}, {@code g} are the three lenses' own colours; see {@link #GLYPH_COLOURS}.
     */
    private static final Map<String, String[]> GLYPHS = Map.of(
            // Two mixer sliders at different settings: it sets its own intensity. The gauge it
            // replaced read as a scribble at 16 pixels.
            "regulator", new String[]{".d....d.", "www...d.", "www...d.", ".d...www", ".d...www", ".d....d."},
            // The three lenses, in their colours, as a revolver's chambers: it carries more lenses.
            "barrel", new String[]{"...oo...", "...oo...", "........", ".bb..gg.", ".bb..gg.", "........"},
            // Double chevron: reaches further.
            "range", new String[]{"ww..ww..", ".ww..ww.", "..ww..ww", ".ww..ww.", "ww..ww..", "........"},
            // A lightning bolt: the energy it saves. The arrow down it replaced said "less" of nothing.
            "efficiency", new String[]{"...ww...", "..ww....", ".wwwwww.", "....ww..", "...ww...", "..w....."});

    /** {@code d} is a slider's dark track, under its white knob. */
    private static final Map<Character, Integer> GLYPH_COLOURS = Map.of(
            'w', 0xFFFFFF, 'd', 0x2A2614, 'o', 0xFF8C1A, 'b', 0x3C8CFF, 'g', 0x3CE05A);

    /**
     * FactoryIO's module: a dark casing round a screen in the augment's colour, lit from the top,
     * a white glyph on it, and tier pips along the foot - lit for each tier, so Range I and II differ
     * by more than a shade. The screen is the colour's mid-tones: a full colour drowned the glyph.
     */
    private static void augment(String name, int colour, int tier, String glyph) throws IOException {
        BufferedImage image = blank();
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                int rgb = switch (CASING[y].charAt(x)) {
                    case 'k' -> 0x1B1B1B;
                    case 'L' -> 0x6A6A6A;
                    case 'D' -> 0x3E3E3E;
                    case 'S' -> 0x2A2A2A;
                    case 'K' -> 0x101010;
                    case '1' -> tier >= 1 ? 0xF7D44A : 0x262626;
                    case '2' -> tier >= 2 ? 0xF7D44A : 0x262626;
                    default -> -1;
                };
                if (rgb >= 0) set(image, x, y, rgb);
            }
        }
        double[] rows = {0.71, 0.63, 0.59, 0.55, 0.51, 0.47};
        String[] art = GLYPHS.get(glyph);
        for (int y = 0; y < rows.length; y++) {
            for (int x = 0; x < 8; x++) {
                Integer ink = GLYPH_COLOURS.get(art[y].charAt(x));
                set(image, 4 + x, 4 + y, ink != null ? ink : shade(colour, rows[y] * 1.4));
            }
        }
        write("item/" + name, image);
    }

    /** The colour scaled by {@code factor}, each channel clamped. */
    private static int shade(int rgb, double factor) {
        int r = (int) Math.min(255, ((rgb >> 16) & 255) * factor);
        int g = (int) Math.min(255, ((rgb >> 8) & 255) * factor);
        int b = (int) Math.min(255, (rgb & 255) * factor);
        return r << 16 | g << 8 | b;
    }

    // ------------------------------------------------------------------ plumbing

    private static double distance(int x, int y) {
        return Math.hypot(x - 7.5, y - 7.5);
    }


    private static BufferedImage blank() {
        return new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
    }

    private static void set(BufferedImage image, int x, int y, int rgb) {
        image.setRGB(x, y, 0xFF000000 | rgb);
    }

    private static void fill(BufferedImage image, int x, int y, int w, int h, int rgb) {
        for (int px = x; px < x + w; px++) {
            for (int py = y; py < y + h; py++) set(image, px, py, rgb);
        }
    }

    private static void write(String name, BufferedImage image) throws IOException {
        ImageIO.write(image, "PNG", new File(ROOT + name + ".png"));
    }

    private GenerateTextures() {}
}
