package dev.drimoz.laserassembly.gui;

/**
 * The grid every part of the screen is laid on - FactoryIO's and Portable Beacons', so the mods'
 * screens read as one family.
 *
 * <p>Outside {@code client/} because the menu needs it: a slot's position is fixed when the menu is
 * built, on both sides. Colours live in {@code client/gui/GuiTheme}.
 *
 * <p><b>Everything aligns to the player inventory's columns</b>: slot frames at {@code 7 + 18 n}.
 * Energy takes column 0, the machine's own content starts at column 1.
 */
public final class GuiMetrics {

    public static final int WIDTH = 176;
    public static final int MARGIN = 8;
    /** The band: the title, and on the right the status light. */
    public static final int TITLE_Y = 7;
    public static final int CONTENT_TOP = 20;

    public static final int SLOT = 18;
    /** The large socket for what the screen is about. */
    public static final int SOCKET = 26;

    /** Left edge of the slot frame in inventory column n. */
    public static int column(int n) {
        return MARGIN - 1 + n * SLOT;
    }

    /** Top edge of the slot frame in content row n. */
    public static int row(int n) {
        return CONTENT_TOP + n * SLOT;
    }

    public static final int GAUGE_WIDTH = 14;
    public static final int GAUGE_X = column(0) + (SLOT - GAUGE_WIDTH) / 2;

    public static final int LED_SIZE = 7;
    public static final int LED_X = WIDTH - MARGIN - LED_SIZE;
    public static final int LED_Y = TITLE_Y;

    // Player inventory, below the content. Item positions, not frame positions.

    public static int inventoryY(int contentBottom) {
        return contentBottom + 15;
    }

    public static int inventoryLabelY(int contentBottom) {
        return inventoryY(contentBottom) - 11;
    }

    public static int hotbarY(int contentBottom) {
        return inventoryY(contentBottom) + 58;
    }

    public static int height(int contentBottom) {
        return hotbarY(contentBottom) + SLOT + 6;
    }

    private GuiMetrics() {}
}
