import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.ConvolveOp;
import java.awt.image.Kernel;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipFile;

/**
 * CurseForge and Modrinth art in Minecraft's font, on the game's own captures - Portable Beacons'
 * {@code tools/Banners.java}, so the mods' pages share one look. No game version is written on any
 * image: the art stays true across ports.
 *
 * <pre>
 *   java tools/Banners.java run/screenshots/store run/store-art
 * </pre>
 *
 * The captures are named by what they show - {@code table}, {@code faces}, {@code control},
 * {@code lasers_tooltip}, {@code laser}, and {@code world} for the table at work in the world when
 * there is one - taken at 2560x1369, GUI scale 2: the crops below are in those pixels, and at scale 2
 * they are kept 1:1, so no pixel blurs. The font comes from the game jar ModDevGradle already built:
 * it is not committed. CurseForge's description editor refuses any image wider than 850 px.
 */
public class Banners {

    static final int W = 850;
    static final Color INK = new Color(14, 15, 18);
    /** The cutting beam's orange: the mod's accent, as on the logo. */
    static final Color ACCENT = new Color(0xFF8C1A);
    static final Color SUB = new Color(0xFFD9A8);
    static final String ITEMS = "src/main/resources/assets/laserassembly/textures/item/";

    static BufferedImage font;
    static final int[] widths = new int[256];

    public static void main(String[] args) throws Exception {
        File shots = new File(args[0]), out = new File(args[1]);
        out.mkdirs();
        loadFont();
        // The world shot is the best backdrop; until there is one, the table's screen stands in.
        String backdrop = new File(shots, "world.png").exists() ? "world" : "table";

        banner(shots, out, backdrop);
        header(shots, out, "header_lenses", backdrop, 0.5, "Lenses and steps", "Cut, weld, engrave - one step at a time");
        header(shots, out, "header_heat", backdrop, 0.3, "Intensity and heat", "Every beam judged on its own");
        header(shots, out, "header_screen", "table", 0.4, "One screen", "Recipes, lasers, faces and redstone");
        header(shots, out, "header_automation", "faces", 0.5, "Automation", "Faces, auto import and export, comparator");
        header(shots, out, "header_data", "laser", 0.5, "For pack makers", "Recipes, beams, augments and coolants in JSON");

        itemSheet(out, "items_lenses", "Lenses", 3, "cut_lens", "weld_lens", "engrave_lens");
        itemSheet(out, "items_augments", "Augments", 3, "regulator_augment", "barrel_augment",
                "range_augment_1", "range_augment_2", "efficiency_augment_1", "efficiency_augment_2");

        // The window with the tab worth showing beside it; at most 850 wide.
        gui(shots, out, "screen_lasers", "table", 1104, 468, 1750, 900);
        gui(shots, out, "screen_info", "table", 836, 468, 1462, 900);
        gui(shots, out, "screen_faces", "faces", 1104, 468, 1730, 900);
        gui(shots, out, "screen_control", "control", 1104, 468, 1730, 900);
        gui(shots, out, "screen_lasers_tooltip", "lasers_tooltip", 1104, 468, 1830, 900);
        gui(shots, out, "screen_laser", "laser", 1100, 492, 1462, 878);
    }

    /** The top banner: the title large, the logo beside it, on a capture. */
    static void banner(File shots, File out, String shot) throws Exception {
        int h = 280;
        BufferedImage b = blur(cover(ImageIO.read(new File(shots, shot + ".png")), W, h, 0.4));
        Graphics2D g = b.createGraphics();
        g.setColor(alpha(INK, 170));
        g.fillRect(0, 0, W, h);
        g.setPaint(new GradientPaint(0, 0, alpha(INK, 235), W * 0.8f, 0, alpha(INK, 60)));
        g.fillRect(0, 0, W, h);
        // The logo is drawn at 512 for this: smoothing, unlike the pixel art, only helps it shrink.
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.drawImage(ImageIO.read(new File("src/main/resources/logo.png")), W - 24 - 232, 24, 232, 232, null);
        int x = 34;
        text(g, "Laser Assembly", x, 52, 6, Color.WHITE);
        text(g, "A workshop of lasers.", x + 2, 128, 3, new Color(236, 236, 240));
        text(g, "Lenses, steps, intensity and heat.", x + 2, 172, 2, SUB);
        text(g, "NeoForge", x + 2, 226, 2, alpha(new Color(220, 220, 220), 220));
        g.dispose();
        ImageIO.write(b, "png", new File(out, "banner.png"));
    }

    /** A section header: a blurred slice of a capture behind a title. */
    static void header(File shots, File out, String name, String shot, double fy, String title, String subtitle) throws Exception {
        int h = 100;
        BufferedImage img = blur(cover(ImageIO.read(new File(shots, shot + ".png")), W, h, fy));
        Graphics2D g = img.createGraphics();
        g.setColor(alpha(INK, 150));
        g.fillRect(0, 0, W, h);
        g.setPaint(new GradientPaint(0, 0, alpha(INK, 210), W * 0.75f, 0, alpha(INK, 30)));
        g.fillRect(0, 0, W, h);
        g.setColor(ACCENT);
        g.fillRect(0, 0, 6, h);
        text(g, title, 28, 20, 4, Color.WHITE);
        text(g, subtitle, 30, 64, 2, SUB);
        g.dispose();
        ImageIO.write(img, "png", new File(out, name + ".png"));
    }

    /** The repository's item textures, enlarged without smoothing, with their English name. */
    static void itemSheet(File out, String name, String title, int cols, String... ids) throws Exception {
        String lang = Files.readString(Path.of("src/main/resources/assets/laserassembly/lang/en_us.json"));
        int cell = W / cols, icon = 80, rowH = icon + 44, top = 64;
        int rows = (ids.length + cols - 1) / cols;
        BufferedImage img = new BufferedImage(W, top + rows * rowH + 12, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setPaint(new GradientPaint(0, 0, new Color(40, 44, 52), 0, img.getHeight(), new Color(18, 19, 23)));
        g.fillRect(0, 0, W, img.getHeight());
        g.setColor(ACCENT);
        g.fillRect(0, 0, 6, img.getHeight());
        text(g, title, 28, 18, 3, Color.WHITE);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        for (int i = 0; i < ids.length; i++) {
            int cx = (i % cols) * cell, cy = top + (i / cols) * rowH;
            g.setColor(new Color(255, 255, 255, 14));
            g.fillRect(cx + (cell - icon) / 2 - 8, cy - 4, icon + 16, icon + 8);
            g.drawImage(ImageIO.read(new File(ITEMS + ids[i] + ".png")), cx + (cell - icon) / 2, cy, icon, icon, null);
            String label = label(lang, ids[i]);
            int scale = width(label) * 2 > cell - 12 ? 1 : 2;
            text(g, label, cx + (cell - width(label) * scale) / 2, cy + icon + 12, scale, SUB);
        }
        g.dispose();
        ImageIO.write(img, "png", new File(out, name + ".png"));
    }

    static String label(String lang, String id) {
        var m = java.util.regex.Pattern.compile("\"item\\.laserassembly\\." + id + "\"\\s*:\\s*\"([^\"]*)\"").matcher(lang);
        return m.find() ? m.group(1) : id;
    }

    /** One capture, cut to the window and its tab, 1:1. */
    static void gui(File shots, File out, String name, String shot, int x0, int y0, int x1, int y1) throws Exception {
        File in = new File(shots, shot + ".png");
        if (!in.exists()) return;
        BufferedImage capture = ImageIO.read(in);
        BufferedImage cut = capture.getSubimage(x0, y0, Math.min(x1, capture.getWidth()) - x0, Math.min(y1, capture.getHeight()) - y0);
        ImageIO.write(cut, "png", new File(out, name + ".png"));
    }

    static int width(String s) {
        int w = 0;
        for (char ch : s.toCharArray()) w += widths[ch] + 1;
        return w;
    }

    // Minecraft's font: 8-pixel cells, one pixel between letters, a shadow at a quarter of the colour.

    static void loadFont() throws Exception {
        File[] jars = new File("build/moddev/artifacts").listFiles((d, n) -> n.contains("minecraft-resources") && n.endsWith(".jar"));
        if (jars == null || jars.length == 0) throw new IllegalStateException("no game jar under build/moddev/artifacts: build once first");
        try (ZipFile zip = new ZipFile(jars[0])) {
            font = ImageIO.read(zip.getInputStream(zip.getEntry("assets/minecraft/textures/font/ascii.png")));
        }
        for (int c = 0; c < 256; c++) {
            int gx = (c % 16) * 8, gy = (c / 16) * 8, w = 0;
            for (int x = 7; x >= 0 && w == 0; x--)
                for (int y = 0; y < 8; y++) if ((font.getRGB(gx + x, gy + y) >>> 24) > 0) { w = x + 1; break; }
            widths[c] = c == ' ' ? 4 : w;
        }
    }

    static void text(Graphics2D g, String s, int x, int y, int scale, Color c) {
        glyphs(g, s, x + scale, y + scale, scale, new Color(c.getRed() / 4, c.getGreen() / 4, c.getBlue() / 4, c.getAlpha()));
        glyphs(g, s, x, y, scale, c);
    }

    static void glyphs(Graphics2D g, String s, int x, int y, int scale, Color c) {
        g.setColor(c);
        for (char ch : s.toCharArray()) {
            int gx = (ch % 16) * 8, gy = (ch / 16) * 8;
            for (int yy = 0; yy < 8; yy++)
                for (int xx = 0; xx < 8; xx++)
                    if ((font.getRGB(gx + xx, gy + yy) >>> 24) > 0) g.fillRect(x + xx * scale, y + yy * scale, scale, scale);
            x += (widths[ch] + 1) * scale;
        }
    }

    // Images

    static BufferedImage cover(BufferedImage src, int w, int h, double fy) {
        BufferedImage o = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        double s = Math.max(w / (double) src.getWidth(), h / (double) src.getHeight());
        int sw = (int) (src.getWidth() * s), sh = (int) (src.getHeight() * s);
        Graphics2D g = o.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.drawImage(src, (w - sw) / 2, (int) (-(sh - h) * fy), sw, sh, null);
        g.dispose();
        return o;
    }

    static BufferedImage blur(BufferedImage src) {
        int r = 5, n = (2 * r + 1) * (2 * r + 1);
        float[] k = new float[n];
        java.util.Arrays.fill(k, 1f / n);
        BufferedImage padded = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_RGB);
        new ConvolveOp(new Kernel(2 * r + 1, 2 * r + 1, k), ConvolveOp.EDGE_NO_OP, null).filter(src, padded);
        return padded;
    }

    static Color alpha(Color c, int a) {
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), a);
    }
}
