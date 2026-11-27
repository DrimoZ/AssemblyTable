import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

/**
 * Generates the lenses and the laser's glow: a lens is a disc of glass in its beam's colour, which
 * code shades more evenly than a hand could.
 *
 * <p>Not the blocks nor the augments: those are painted by hand, pixel by pixel, in AE2's spirit,
 * and the PNGs are the source. Computed panels and screens looked soft and flat beside AE2's and
 * Create's machines.
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
     * One texture per lens, its colour baked in: each lens is its own item, so it has
     * its own texture, and an item a pack adds brings its own. The colours are the datapack
     * entries' - kept in step by hand, they change rarely.
     */

    private static void writeItems() throws IOException {
        lens("cut_lens", 0xFF8C1A);
        lens("weld_lens", 0x3C8CFF);
        lens("engrave_lens", 0x3CE05A);
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
