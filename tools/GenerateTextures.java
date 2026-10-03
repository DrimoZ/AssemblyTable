import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

/**
 * Generates the block and item textures, in Portable Beacons' way: drawn by code, so a colour or a
 * proportion is changed here and every texture follows.
 *
 * <p>Both blocks share one steel panel - dark rim, light bevel top-left, shadow bottom-right - so
 * the table and its lasers read as one machine. What sets each face apart is drawn on the panel:
 * the table's focus, where the beams meet; the laser's socket; vents on the sides. What moves or
 * lights up - the laser's head, the focus at work - is drawn by the renderers over these.
 *
 * <p>The lens and the augment are tinted from their registry entry, so each keeps its coloured part
 * on a greyscale layer of its own, and its metal on another, untinted: a tinted frame turned the
 * whole item one flat colour. The augment is drawn in FactoryIO's module family - a dark casing
 * round a screen - so it reads as a part you fit into something.
 *
 * <pre>java tools/GenerateTextures.java</pre>
 */
public final class GenerateTextures {

    private static final String ROOT = "src/main/resources/assets/laserassembly/textures/";

    private static final int RIM = 0x23262B;
    private static final int BEVEL = 0x8A939E;
    private static final int SHADOW = 0x3A4048;
    private static final int PLATE = 0x5E6670;
    private static final int DARK = 0x1A1C20;
    private static final int BOLT = 0xA9B2BC;

    public static void main(String[] args) throws IOException {
        new File(ROOT + "block").mkdirs();
        new File(ROOT + "item").mkdirs();

        write("block/assembly_table_top", tableTop());
        write("block/assembly_table_side", tableSide());
        write("block/assembly_table_bottom", bolted(panel()));
        write("block/assembly_table_glow", tableGlow());
        write("block/laser_top", laserTop());
        write("block/laser_side", laserSide());
        write("block/laser_bottom", bolted(panel()));
        write("block/laser_head", laserHead());
        write("block/laser_glow", laserGlow());

        write("item/lens_glass", lensGlass());
        write("item/lens_rim", lensRim());
        write("item/augment_casing", augmentCasing());
        write("item/augment_screen", augmentScreen());
        System.out.println("Textures written.");
    }

    // ------------------------------------------------------------------ blocks

    private static BufferedImage panel() {
        BufferedImage image = blank();
        fill(image, 0, 0, 16, 16, RIM);
        fill(image, 1, 1, 14, 14, PLATE);
        fill(image, 1, 1, 14, 1, BEVEL);
        fill(image, 1, 1, 1, 14, BEVEL);
        fill(image, 1, 14, 14, 1, SHADOW);
        fill(image, 14, 1, 1, 14, SHADOW);
        return image;
    }

    private static BufferedImage bolted(BufferedImage image) {
        for (int x : new int[]{3, 12}) {
            for (int y : new int[]{3, 12}) {
                set(image, x, y, BOLT);
                set(image, x + 1, y + 1, SHADOW);
            }
        }
        return image;
    }

    /** The focus at the centre, where every beam meets, framed by a recessed ring. Dim: it lights up through the glow. */
    private static BufferedImage tableTop() {
        BufferedImage image = bolted(panel());
        disc(image, 6.0, SHADOW);
        disc(image, 5.0, DARK);
        disc(image, 4.0, 0x3A2A1C);
        disc(image, 2.6, 0x4A3420);
        disc(image, 1.5, 0x5A3E22);
        return image;
    }

    /** The focus lit, drawn over the top at full brightness while the table works; clear elsewhere. */
    private static BufferedImage tableGlow() {
        BufferedImage image = blank();
        disc(image, 4.0, 0xB06A20);
        disc(image, 2.6, 0xE89A40);
        disc(image, 1.5, 0xFFC870);
        disc(image, 0.8, 0xFFF0C0);
        return image;
    }

    /** A workbench's apron: a plate over a vented band, on a darker foot. */
    private static BufferedImage tableSide() {
        BufferedImage image = panel();
        for (int y = 5; y <= 9; y += 2) {
            fill(image, 3, y, 10, 1, DARK);
            fill(image, 3, y + 1, 10, 1, BEVEL);
        }
        fill(image, 1, 12, 14, 2, SHADOW);
        fill(image, 1, 12, 14, 1, PLATE);
        set(image, 3, 3, BOLT);
        set(image, 12, 3, BOLT);
        return image;
    }

    /** The base's top: the socket the head turns in. */
    private static BufferedImage laserTop() {
        BufferedImage image = bolted(panel());
        disc(image, 5.5, SHADOW);
        disc(image, 4.5, DARK);
        return image;
    }

    /** The base is 7 high: its sides read rows 9 to 15 - a bevel, vents for the heat, the rim. */
    private static BufferedImage laserSide() {
        BufferedImage image = blank();
        fill(image, 0, 0, 16, 16, RIM);
        fill(image, 1, 9, 14, 1, BEVEL);
        fill(image, 1, 10, 14, 4, PLATE);
        fill(image, 1, 14, 14, 1, SHADOW);
        for (int x = 3; x <= 12; x += 2) fill(image, x, 11, 1, 2, DARK);
        return image;
    }

    /**
     * The head, read by UV region: body side (0,0) 6x6 with a copper band, body cap (6,0) 6x6,
     * nozzle side (0,6) 4x2, nozzle tip (4,6) 4x4 - dark, the glow is drawn over it when firing.
     */
    private static BufferedImage laserHead() {
        BufferedImage image = blank();
        fill(image, 0, 0, 6, 6, PLATE);
        fill(image, 0, 0, 6, 1, BEVEL);
        fill(image, 0, 0, 1, 6, BEVEL);
        fill(image, 0, 5, 6, 1, SHADOW);
        fill(image, 5, 0, 1, 6, SHADOW);
        fill(image, 1, 3, 4, 1, 0xB87333);
        fill(image, 6, 0, 6, 6, SHADOW);
        fill(image, 7, 1, 4, 4, PLATE);
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

    /** Greyscale, lit from the top-left, so the beam colour shades rather than flattens. */
    private static BufferedImage lensGlass() {
        BufferedImage image = blank();
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                if (distance(x, y) > 5.2) continue;
                int grey = (int) Math.round(235 - 9 * (x + y - 8) / 2.0);
                set(image, x, y, grey << 16 | grey << 8 | grey);
            }
        }
        set(image, 5, 5, 0xFFFFFF);
        set(image, 6, 5, 0xFFFFFF);
        set(image, 5, 6, 0xFFFFFF);
        return image;
    }

    private static BufferedImage lensRim() {
        BufferedImage image = blank();
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                double d = distance(x, y);
                if (d > 5.2 && d <= 7.0) set(image, x, y, x + y < 15 ? BEVEL : SHADOW);
            }
        }
        return image;
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
            ".kDDcDDDDDDcDDk.",
            ".kSSSSSSSSSSSSk.",
            "..kkkkkkkkkkkk..",
            "................"};

    private static BufferedImage augmentCasing() {
        BufferedImage image = blank();
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                int colour = switch (CASING[y].charAt(x)) {
                    case 'k' -> 0x1B1B1B;
                    case 'L' -> 0x6A6A6A;
                    case 'D' -> 0x3E3E3E;
                    case 'S' -> 0x2A2A2A;
                    case 'K' -> 0x101010;
                    case 'c' -> 0xB87333;
                    default -> -1;
                };
                if (colour >= 0) set(image, x, y, colour);
            }
        }
        return image;
    }

    /**
     * The screen, x 4..11 and y 4..9: mid-greys lit from the top, crossed by a white beam. A
     * full-white screen made pale colours swallow what is drawn on it, as in Portable Beacons.
     */
    private static BufferedImage augmentScreen() {
        BufferedImage image = blank();
        int[] rows = {0xB4B4B4, 0xA0A0A0, 0x969696, 0x8C8C8C, 0x828282, 0x787878};
        for (int y = 0; y < rows.length; y++) fill(image, 4, 4 + y, 8, 1, rows[y]);
        for (int x = 4; x < 12; x++) set(image, x, 9 - (x - 4) * 5 / 7, 0xFFFFFF);
        return image;
    }

    // ------------------------------------------------------------------ plumbing

    private static double distance(int x, int y) {
        return Math.hypot(x - 7.5, y - 7.5);
    }

    /** A disc centred on the face, so rings are drawn largest first. */
    private static void disc(BufferedImage image, double radius, int rgb) {
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                if (distance(x, y) <= radius) set(image, x, y, rgb);
            }
        }
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
