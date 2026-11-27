import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Map;

/**
 * Generates the item textures and the laser's glow, in Portable Beacons' way: drawn by code, so a
 * colour or a proportion is changed here and every texture follows.
 *
 * <p>Not the blocks: their faces are painted by hand, pixel by pixel, and the PNGs are the source.
 * Computed panels and discs looked soft and flat beside AE2's and Create's machines.
 *
 * <p>The augments are drawn in FactoryIO's module family - a dark casing round a screen - so an
 * augment reads as a part you fit into something.
 *
 * <pre>java tools/GenerateTextures.java</pre>
 */
public final class GenerateTextures {

    private static final String ROOT = "src/main/resources/assets/laserassembly/textures/";

    /** The lenses' ring, in the blocks' gunmetal. */
    private static final int RIM = 0x15161C;
    private static final int BEVEL_LIGHT = 0x5C6278;
    private static final int PLATE_DARK = 0x2E3240;

    public static void main(String[] args) throws IOException {
        new File(ROOT + "block").mkdirs();
        new File(ROOT + "item").mkdirs();

        write("block/laser_glow", laserGlow());

        writeItems();
        System.out.println("Textures written.");
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


    private static void write(String name, BufferedImage image) throws IOException {
        ImageIO.write(image, "PNG", new File(ROOT + name + ".png"));
    }

    private GenerateTextures() {}
}
