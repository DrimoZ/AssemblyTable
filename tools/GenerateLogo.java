import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Point2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

/**
 * The logo, in the family's art direction - Portable Beacons' and Immaterial Drawers': an object in
 * the isometric view the game gives blocks in an inventory, drawn in texture pixels and lit like a
 * block (top brightest, left mid, right darkest), with a soft glow, on the same night-blue plate.
 *
 * <p>The object is the mod at work, drawn from its own textures and models so the logo follows them:
 * the assembly table, its focus lit, three lasers around it turned to it, and three beams - cutting, welding,
 * engraving - meeting on its focus, where the game aims them. Run it after {@code GenerateTextures.java}.
 *
 * <p>512 x 512: the mod list, CurseForge's project avatar and the GitHub social preview all scale it
 * down from there.
 *
 * <pre>java tools/GenerateLogo.java</pre>
 */
public final class GenerateLogo {

    private static final String TEXTURES = "src/main/resources/assets/laserassembly/textures/";
    private static final int SIZE = 512;
    /** Screen pixels per texture pixel. */
    private static final double K = 6.3;
    private static final double COS = Math.cos(Math.toRadians(30));
    private static final double SIN = 0.5;

    private static final Color PLATE_TOP = new Color(0x2A2244);
    private static final Color PLATE_BOTTOM = new Color(0x100D1C);
    private static final int CUT = 0xFF8C1A, WELD = 0x3C8CFF, ENGRAVE = 0x3CE05A;

    private static double cx;
    private static double cy;

    public static void main(String[] args) throws IOException {
        BufferedImage tableTop = read("block/assembly_table_top"), tableSide = read("block/assembly_table_side");
        BufferedImage laserTop = read("block/laser_top"), laserSide = read("block/laser_side"), head = read("block/laser_head");
        // The focus lit, as while the table works.
        Graphics2D lit = tableTop.createGraphics();
        lit.drawImage(read("block/assembly_table_glow"), 0, 0, null);
        lit.dispose();

        BufferedImage out = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        RoundRectangle2D plate = new RoundRectangle2D.Double(16, 16, SIZE - 32, SIZE - 32, 104, 104);
        g.setPaint(new GradientPaint(0, 16, PLATE_TOP, 0, SIZE - 16, PLATE_BOTTOM));
        g.fill(plate);
        g.setClip(plate);
        grid(g);

        // The scene's origin: the centre of the table's base, placed so the whole scene is centred.
        cx = SIZE / 2.0;
        cy = SIZE / 2.0 + 106;

        // Where the beams land, as in the game: the middle of the table's top.
        double[] focus = {0, 12.2, 0};
        glow(g, project(0, 12, 0), 260, new Color(0x5CE1FF), 60);
        shadow(g);

        // Three lasers about two blocks away, back to front, each head turned to the table.
        double[][] lasers = {{-28, -28}, {-30, 2}, {2, -30}};
        int[] beams = {ENGRAVE, CUT, WELD};
        double[][] tips = new double[3][];
        for (int i = 0; i < 3; i++) tips[i] = laser(g, lasers[i][0], lasers[i][1], focus, laserTop, laserSide, head, beams[i]);

        table(g, tableTop, tableSide);
        for (int i = 0; i < 3; i++) beam(g, tips[i], focus, beams[i]);

        // The light where they meet.
        double[] at = project(focus[0], focus[1], focus[2]);
        glow(g, at, 110, new Color(0xB47CFF), 120);
        glow(g, at, 26, Color.WHITE, 200);
        sparkle(g, at, 3);

        g.setClip(null);
        g.setStroke(new BasicStroke(3f));
        g.setColor(new Color(255, 255, 255, 34));
        g.draw(plate);
        g.dispose();
        ImageIO.write(out, "PNG", new File("src/main/resources/logo.png"));
        System.out.println("Logo written.");
    }

    // ------------------------------------------------------------------ the objects

    private static BufferedImage read(String texture) throws IOException {
        return ImageIO.read(new File(TEXTURES + texture + ".png"));
    }

    /**
     * The table as assembly_table.json builds it, each part reading its own region of the sheets: the
     * plinth, the body set in by one, the crown round the sunken plate, the crystal. Drawn back to
     * front: the crown's back bars, the plate and crystal, then its front bars.
     */
    private static void table(Graphics2D g, BufferedImage top, BufferedImage side) {
        box(g, -8, 0, -8, 8, 2, 8, (face, u, v) -> new Color(face == 0 ? top.getRGB(u, v) : side.getRGB(u, 14 + v)));
        box(g, -7, 2, -7, 7, 10, 7, (face, u, v) -> new Color(face == 0 ? top.getRGB(1 + u, 1 + v) : side.getRGB(1 + u, 6 + v)));
        box(g, -8, 10, -8, 8, 12, -5, (face, u, v) -> new Color(face == 0 ? top.getRGB(u, v) : side.getRGB(u, 4 + v)));
        box(g, -8, 10, -5, -5, 12, 5, (face, u, v) -> new Color(face == 0 ? top.getRGB(u, 3 + v) : side.getRGB(u, 4 + v)));
        box(g, -5, 10, -5, 5, 11, 5, (face, u, v) -> new Color(top.getRGB(3 + u, 3 + v)));
        box(g, -2, 11, -2, 2, 12, 2, (face, u, v) -> new Color(face == 0 ? top.getRGB(6 + u, 6 + v) : side.getRGB(6 + u, 3)));
        box(g, 5, 10, -5, 8, 12, 5, (face, u, v) -> new Color(face == 0 ? top.getRGB(13 + u, 3 + v) : side.getRGB(u, 4 + v)));
        box(g, -8, 10, 5, 8, 12, 8, (face, u, v) -> new Color(face == 0 ? top.getRGB(u, 13 + v) : side.getRGB(u, 4 + v)));
    }

    /**
     * The laser block as the game builds and turns it: plinth, turret and lit collar 7 high, then the
     * head on its pivot - body, band, barrel, muzzle - leaning towards {@code target} as far as the
     * renderer lets it, 50 degrees, the muzzle lit in its beam's colour. Returns the muzzle's tip,
     * where the beam leaves.
     */
    private static double[] laser(Graphics2D g, double x, double z, double[] target, BufferedImage top, BufferedImage side,
                                  BufferedImage head, int beam) {
        box(g, x - 7, 0, z - 7, x + 7, 2, z + 7, (face, u, v) -> new Color(face == 0 ? top.getRGB(1 + u, 1 + v) : side.getRGB(1 + u, 14 + v)));
        box(g, x - 4, 2, z - 4, x + 4, 6, z + 4, (face, u, v) -> new Color(face == 0 ? top.getRGB(4 + u, 4 + v) : side.getRGB(4 + u, 10 + v)));
        box(g, x - 5, 6, z - 5, x + 5, 7, z + 5, (face, u, v) -> new Color(face == 0 ? top.getRGB(3 + u, 3 + v) : side.getRGB(3 + u, 9)));
        double[] pivot = {x, 10, z};
        aim(pivot, target);
        // The body in two, round its band: drawn whole, the band's top painted over the body above it.
        box(g, x - 3, 7, z - 3, x + 3, 9, z + 3, (face, u, v) -> new Color(head.getRGB(u, 3 + v)));
        box(g, x - 4, 9, z - 4, x + 4, 10, z + 4, (face, u, v) -> new Color(face == 0 ? head.getRGB(u, 8 + v) : head.getRGB(u, 6)));
        box(g, x - 3, 10, z - 3, x + 3, 12, z + 3, (face, u, v) -> new Color(face == 0 ? head.getRGB(6 + u, v) : head.getRGB(u, v)));
        box(g, x - 1, 12, z - 1, x + 1, 14, z + 1, (face, u, v) -> new Color(head.getRGB(12 + u, v)));
        box(g, x - 2, 14, z - 2, x + 2, 15, z + 2, (face, u, v) -> face == 0
                ? (u >= 1 && u <= 2 && v >= 1 && v <= 2 ? Color.WHITE : new Color(beam))
                : new Color(head.getRGB(8 + u, 6)));
        double[] tip = turn(x, 15, z);
        rotation = null;
        glow(g, project(tip[0], tip[1], tip[2]), 34, new Color(beam), 200);
        return tip;
    }

    /** A rotation about {@code pivot} taking up towards {@code target}, at most 50 degrees: Rodrigues'. */
    private static void aim(double[] pivot, double[] target) {
        double dx = target[0] - pivot[0], dy = target[1] - pivot[1], dz = target[2] - pivot[2];
        double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
        double angle = Math.min(Math.acos(dy / length), Math.toRadians(50));
        // The axis: up crossed with the direction, (dz, 0, -dx), normalised.
        double ax = dz, az = -dx, n = Math.hypot(ax, az);
        ax /= n;
        az /= n;
        double c = Math.cos(angle), s = Math.sin(angle), t = 1 - c;
        rotation = new double[]{
                t * ax * ax + c, -s * az, t * ax * az,
                s * az, c, -s * ax,
                t * ax * az, s * ax, t * az * az + c};
        rotationPivot = pivot;
    }

    private static double[] rotation;
    private static double[] rotationPivot;

    private static double[] turn(double x, double y, double z) {
        if (rotation == null) return new double[]{x, y, z};
        double px = x - rotationPivot[0], py = y - rotationPivot[1], pz = z - rotationPivot[2];
        double[] m = rotation;
        return new double[]{
                rotationPivot[0] + m[0] * px + m[1] * py + m[2] * pz,
                rotationPivot[1] + m[3] * px + m[4] * py + m[5] * pz,
                rotationPivot[2] + m[6] * px + m[7] * py + m[8] * pz};
    }

    /** A four-point star in texture pixels, as the game draws its sparkles. */
    private static void sparkle(Graphics2D g, double[] at, int arm) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        int px = (int) Math.round(K * 0.8);
        for (int i = -arm; i <= arm; i++) {
            g.setColor(Math.abs(i) <= 1 ? Color.WHITE : new Color(255, 236, 190, 230 - 30 * Math.abs(i)));
            g.fillRect((int) (at[0] + i * px - px / 2.0), (int) (at[1] - px / 2.0), px, px);
            g.fillRect((int) (at[0] - px / 2.0), (int) (at[1] + i * px - px / 2.0), px, px);
        }
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    }

    // ------------------------------------------------------------------ drawing

    private interface Texture {
        /** @param face 0 top, 1 left (+z), 2 right (+x); u, v in texture pixels from the face's corner */
        Color at(int face, int u, int v);
    }

    /** A box drawn by its three visible faces, each in texture pixels, lit like a block. */
    private static void box(Graphics2D g, double x0, double y0, double z0, double x1, double y1, double z1, Texture texture) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        // Left face (z = z1): u along x, v down from the top.
        for (double x = x0; x < x1; x++)
            for (double y = y0; y < y1; y++) {
                g.setColor(shade(texture.at(1, (int) (x - x0), (int) (y1 - y - 1)), 0.82));
                g.fill(quad(x, y, z1, x + 1, y, z1, x + 1, y + 1, z1, x, y + 1, z1));
            }
        // Right face (x = x1): u along -z.
        for (double z = z0; z < z1; z++)
            for (double y = y0; y < y1; y++) {
                g.setColor(shade(texture.at(2, (int) (z1 - z - 1), (int) (y1 - y - 1)), 0.62));
                g.fill(quad(x1, y, z, x1, y, z + 1, x1, y + 1, z + 1, x1, y + 1, z));
            }
        // Top (y = y1).
        for (double x = x0; x < x1; x++)
            for (double z = z0; z < z1; z++) {
                g.setColor(texture.at(0, (int) (x - x0), (int) (z - z0)));
                g.fill(quad(x, y1, z, x + 1, y1, z, x + 1, y1, z + 1, x, y1, z + 1));
            }
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    }

    /**
     * A beam as the game draws one: a straight band in its colour round a white core, unsmoothed so its
     * edges step like the blocks', over a soft glow.
     */
    private static void beam(Graphics2D g, double[] from, double[] to, int rgb) {
        Color c = new Color(rgb);
        double[] a = project(from[0], from[1], from[2]), b = project(to[0], to[1], to[2]);
        double length = Math.hypot(b[0] - a[0], b[1] - a[1]);
        for (double t = 0; t <= 1; t += 18 / length) {
            glow(g, new double[]{a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t}, 24, c, 34);
        }
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        java.awt.geom.Line2D line = new java.awt.geom.Line2D.Double(a[0], a[1], b[0], b[1]);
        g.setStroke(new BasicStroke((float) (K * 1.1), BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER));
        g.setColor(c);
        g.draw(line);
        g.setStroke(new BasicStroke((float) (K * 0.45), BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER));
        g.setColor(new Color(255, 255, 255));
        g.draw(line);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    }

    private static double[] project(double x, double y, double z) {
        return new double[]{cx + (x - z) * K * COS, cy + (x + z) * K * SIN - y * K};
    }

    private static Polygon quad(double... xyz) {
        Polygon p = new Polygon();
        for (int i = 0; i < xyz.length; i += 3) {
            double[] w = turn(xyz[i], xyz[i + 1], xyz[i + 2]);
            double[] s = project(w[0], w[1], w[2]);
            p.addPoint((int) Math.round(s[0]), (int) Math.round(s[1]));
        }
        return p;
    }

    private static void shadow(Graphics2D g) {
        double[] c = project(0, 0, 0);
        float r = (float) (17 * K);
        g.setPaint(new RadialGradientPaint(new Point2D.Double(c[0], c[1]), r, new float[]{0f, 1f},
                new Color[]{new Color(0, 0, 0, 150), new Color(0, 0, 0, 0)}));
        g.fill(new Ellipse2D.Double(c[0] - r, c[1] - r * 0.5, r * 2, r));
    }

    private static void glow(Graphics2D g, double[] at, float radius, Color c, int alpha) {
        g.setPaint(new RadialGradientPaint(new Point2D.Double(at[0], at[1]), radius, new float[]{0f, 1f},
                new Color[]{new Color(c.getRed(), c.getGreen(), c.getBlue(), alpha), new Color(c.getRed(), c.getGreen(), c.getBlue(), 0)}));
        g.fill(new Ellipse2D.Double(at[0] - radius, at[1] - radius, radius * 2, radius * 2));
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
