import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

/**
 * The logo: the assembly table in the isometric view the game gives blocks in an inventory, three
 * beams - cutting, welding, engraving - meeting on its lit focus. On the plate Portable Beacons and
 * Immaterial Drawers use, so the mods' icons read as a set in a launcher.
 *
 * <p>The block is drawn from the mod's own textures, texture pixel by texture pixel, lit like a
 * block - top brightest, left mid, right darkest - so the logo changes with them. Run it after
 * {@code GenerateTextures.java}.
 *
 * <p>512 x 512: the mod list, CurseForge's project avatar and the GitHub social preview all scale it
 * down from there.
 *
 * <pre>java tools/GenerateLogo.java</pre>
 */
public final class GenerateLogo {

    private static final String BLOCKS = "src/main/resources/assets/laserassembly/textures/block/";
    private static final int SIZE = 512;
    /** Screen pixels per texture pixel. */
    private static final double K = 11;
    private static final double COS = Math.cos(Math.toRadians(30));
    private static final double SIN = 0.5;

    private static final Color PLATE_TOP = new Color(0x2B3038);
    private static final Color PLATE_BOTTOM = new Color(0x111316);
    /** The beam types' colours, as in their datapack entries. */
    private static final int[] BEAMS = {0xFF8C1A, 0x3C8CFF, 0x3CE05A};

    private static double cx;
    private static double cy;

    public static void main(String[] args) throws IOException {
        BufferedImage top = ImageIO.read(new File(BLOCKS + "assembly_table_top.png"));
        BufferedImage glow = ImageIO.read(new File(BLOCKS + "assembly_table_glow.png"));
        BufferedImage side = ImageIO.read(new File(BLOCKS + "assembly_table_side.png"));
        // The focus lit, as while the table works.
        Graphics2D lit = top.createGraphics();
        lit.drawImage(glow, 0, 0, null);
        lit.dispose();

        BufferedImage out = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        RoundRectangle2D plate = new RoundRectangle2D.Double(16, 16, SIZE - 32, SIZE - 32, 104, 104);
        g.setPaint(new GradientPaint(0, 16, PLATE_TOP, 0, SIZE - 16, PLATE_BOTTOM));
        g.fill(plate);
        g.setClip(plate);
        grid(g);

        // The block's origin: the centre of its base, placed so block and beams sit centred.
        cx = SIZE / 2.0;
        cy = SIZE / 2.0 + 150;

        shadow(g);
        box(g, -8, 0, -8, 8, 16, 8, top, side);

        // The beams come in from above, out of frame, and meet just over the focus.
        double[] focus = project(0, 16.5, 0);
        double[][] sources = {{-30, 20}, {SIZE + 30, 40}, {SIZE / 2.0 + 30, -30}};
        for (int i = 0; i < BEAMS.length; i++) beam(g, sources[i][0], sources[i][1], focus[0], focus[1], BEAMS[i]);
        glow(g, focus[0], focus[1], 70, new Color(0xFFE0A0), 200);
        glow(g, focus[0], focus[1], 18, Color.WHITE, 255);

        g.setClip(null);
        g.setStroke(new BasicStroke(3f));
        g.setColor(new Color(255, 255, 255, 34));
        g.draw(plate);
        g.dispose();
        ImageIO.write(out, "PNG", new File("src/main/resources/logo.png"));
        System.out.println("Logo written.");
    }

    /** A beam as the game draws one: a faint wide glow round a white-hot core. */
    private static void beam(Graphics2D g, double x0, double y0, double x1, double y1, int rgb) {
        Color c = new Color(rgb);
        float[] widths = {22, 12, 6};
        int[] alphas = {50, 110, 255};
        for (int i = 0; i < widths.length; i++) {
            g.setStroke(new BasicStroke(widths[i], BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), alphas[i]));
            g.draw(new Line2D.Double(x0, y0, x1, y1));
        }
        g.setStroke(new BasicStroke(2.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(new Color(255, 255, 255, 220));
        g.draw(new Line2D.Double(x0, y0, x1, y1));
    }

    /** A block drawn by its three visible faces, each from its texture, lit like a block. */
    private static void box(Graphics2D g, double x0, double y0, double z0, double x1, double y1, double z1,
                            BufferedImage top, BufferedImage side) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        for (int u = 0; u < 16; u++) {
            for (int v = 0; v < 16; v++) {
                double x = x0 + u, y = y1 - v - 1, z = z0 + u;
                // Left face (z = z1): u along x, v down from the top.
                g.setColor(shade(new Color(side.getRGB(u, v)), 0.82));
                g.fill(quad(x, y, z1, x + 1, y, z1, x + 1, y + 1, z1, x, y + 1, z1));
                // Right face (x = x1): u along -z.
                g.setColor(shade(new Color(side.getRGB(u, v)), 0.6));
                double zr = z1 - u - 1;
                g.fill(quad(x1, y, zr, x1, y, zr + 1, x1, y + 1, zr + 1, x1, y + 1, zr));
                // Top (y = y1).
                g.setColor(new Color(top.getRGB(u, v)));
                g.fill(quad(x0 + u, y1, z0 + v, x0 + u + 1, y1, z0 + v, x0 + u + 1, y1, z0 + v + 1, x0 + u, y1, z0 + v + 1));
            }
        }
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    }

    private static double[] project(double x, double y, double z) {
        return new double[]{cx + (x - z) * K * COS, cy + (x + z) * K * SIN - y * K};
    }

    private static Polygon quad(double... xyz) {
        Polygon p = new Polygon();
        for (int i = 0; i < xyz.length; i += 3) {
            double[] s = project(xyz[i], xyz[i + 1], xyz[i + 2]);
            p.addPoint((int) Math.round(s[0]), (int) Math.round(s[1]));
        }
        return p;
    }

    private static void shadow(Graphics2D g) {
        double[] c = project(0, 0, 0);
        float r = (float) (14 * K);
        g.setPaint(new RadialGradientPaint(new Point2D.Double(c[0], c[1]), r, new float[]{0f, 1f},
                new Color[]{new Color(0, 0, 0, 150), new Color(0, 0, 0, 0)}));
        g.fill(new Ellipse2D.Double(c[0] - r, c[1] - r * 0.5, r * 2, r));
    }

    private static void glow(Graphics2D g, double x, double y, float radius, Color colour, int alpha) {
        g.setPaint(new RadialGradientPaint(new Point2D.Double(x, y), radius, new float[]{0f, 1f},
                new Color[]{new Color(colour.getRed(), colour.getGreen(), colour.getBlue(), alpha),
                        new Color(colour.getRed(), colour.getGreen(), colour.getBlue(), 0)}));
        g.fill(new Ellipse2D.Double(x - radius, y - radius, radius * 2, radius * 2));
    }

    private static void grid(Graphics2D g) {
        g.setColor(new Color(255, 255, 255, 12));
        for (int i = 16; i < SIZE; i += 32) {
            g.drawLine(i, 0, i, SIZE);
            g.drawLine(0, i, SIZE, i);
        }
    }

    private static Color shade(Color c, double f) {
        return new Color((int) (c.getRed() * f), (int) (c.getGreen() * f), (int) (c.getBlue() * f));
    }

    private GenerateLogo() {}
}
