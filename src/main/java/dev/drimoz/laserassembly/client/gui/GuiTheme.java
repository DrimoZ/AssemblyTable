package dev.drimoz.laserassembly.client.gui;

/**
 * The palette of every screen in the mod - FactoryIO's, so the mods read as one family.
 *
 * <p>On the window (vanilla light grey): dark text, no shadow. A colour means one thing: green says
 * "this works", red says "this will not work without you" - never red for decoration.
 */
public final class GuiTheme {

    public static final int TEXT = 0xFF404040;
    public static final int TEXT_MUTED = 0xFF6E6E6E;

    // Inside a tab (a saturated tint): light text, shadowed.

    public static final int TAB_LABEL = 0xFFD8D8D8;
    public static final int TAB_VALUE = 0xFFFFFFFF;
    public static final int TAB_MUTED = 0xFFB0B0B0;
    /** Line height inside tabs. */
    public static final int LINE = 10;

    // Tab colour is function: on the left what informs, on the right what is set.

    public static final int TAB_INFO = 0xFFC89420;
    public static final int TAB_LASERS = 0xFFB8423A;
    public static final int TAB_FACES = 0xFF3C6FC8;

    /** The green of a current choice: the step in progress. FactoryIO's selected-slot rim. */
    public static final int SELECTED = 0xFF3FBF3F;

    /** Under the mouse: what a click would land on. */
    public static final int HOVER_WASH = 0x60FFFFFF;
    /** Over a ticked recipe the table cannot run right now: still chosen, waiting. */
    public static final int GHOST_WASH = 0xA0C6C6C6;

    /** FactoryIO's five shared states: the band's light says the same thing on every machine. */
    public enum Status {
        /** Running. */
        WORKING("led_working"),
        /** Ready, but something has to arrive: a beam, ingredients, room in the outputs. */
        WAITING("led_waiting"),
        /** Running, but held back - here, by heat. */
        BLOCKED("led_blocked"),
        /** Stopped until you act: overload, overheat. */
        PROBLEM("led_problem"),
        /** Off on purpose: nothing ticked, or a redstone signal. */
        OFF("led_off");

        final String sprite;

        Status(String sprite) {
            this.sprite = sprite;
        }
    }

    private GuiTheme() {}
}
