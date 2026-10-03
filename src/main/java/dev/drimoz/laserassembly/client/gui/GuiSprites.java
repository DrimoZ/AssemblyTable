package dev.drimoz.laserassembly.client.gui;

import dev.drimoz.laserassembly.LaserAssembly;
import dev.drimoz.laserassembly.gui.GuiMetrics;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/**
 * How each piece of the screen is drawn - FactoryIO's pieces, as GUI sprites written by
 * {@code tools/GenerateGuiSprites.java}. Each sprite carries its own scaling in its mcmeta, so no
 * class here knows a texture coordinate.
 */
public final class GuiSprites {

    private static final ResourceLocation PANEL = id("panel");
    private static final ResourceLocation INSET = id("inset");
    private static final ResourceLocation SLOT = id("slot");
    private static final ResourceLocation SLOT_SELECTED = id("slot_selected");
    private static final ResourceLocation GAUGE_EMPTY = id("gauge_empty");
    private static final ResourceLocation SOCKET = id("socket");
    private static final ResourceLocation ARROW = id("arrow");
    private static final ResourceLocation BAR = id("bar");
    private static final ResourceLocation FIELD = id("field");
    private static final ResourceLocation PLUS = id("icon_plus");
    private static final ResourceLocation BUTTON = id("button");
    private static final ResourceLocation BUTTON_HOVER = id("button_hover");

    public static final ResourceLocation GAUGE_ENERGY = id("gauge_energy");
    public static final ResourceLocation GAUGE_HEAT = id("gauge_heat");
    public static final ResourceLocation GAUGE_PROGRESS = id("gauge_progress");

    /** The main window, in the tone of vanilla containers. */
    public static void panel(GuiGraphics graphics, int x, int y, int width, int height) {
        graphics.blitSprite(PANEL, x, y, width, height);
    }

    /** The frame of a slot whose top-left frame corner is at {@code (x, y)}. */
    public static void slot(GuiGraphics graphics, int x, int y, boolean selected) {
        graphics.blitSprite(selected ? SLOT_SELECTED : SLOT, x, y, GuiMetrics.SLOT, GuiMetrics.SLOT);
    }

    /**
     * A vertical gauge filled from the bottom. Both layers are tiled from the gauge's top and the lit
     * one is clipped, so its segments stay put while the level moves.
     */
    public static void gauge(GuiGraphics graphics, ResourceLocation lit, int x, int y, int width, int height, float fill) {
        graphics.blitSprite(INSET, x, y, width, height);
        int innerX = x + 1, innerY = y + 1, innerW = width - 2, innerH = height - 2;
        graphics.blitSprite(GAUGE_EMPTY, innerX, innerY, innerW, innerH);
        int filled = Math.round(innerH * Math.clamp(fill, 0.0F, 1.0F));
        if (filled <= 0) return;
        graphics.enableScissor(innerX, innerY + innerH - filled, innerX + innerW, innerY + innerH);
        graphics.blitSprite(lit, innerX, innerY, innerW, innerH);
        graphics.disableScissor();
    }

    /** The large socket for what the screen is about: here, the recipe in progress. */
    public static void socket(GuiGraphics graphics, int x, int y) {
        graphics.blitSprite(SOCKET, x, y, GuiMetrics.SOCKET, GuiMetrics.SOCKET);
    }

    /** FactoryIO's 20x13 arrow, from inputs to outputs. */
    public static void arrow(GuiGraphics graphics, int x, int y) {
        graphics.blitSprite(ARROW, x, y, 20, 13);
    }

    /**
     * A run of the step bar's segments in one colour. 1.21.1 sprites take no tint of their own: the
     * shader colour carries it, and is put back.
     */
    public static void barSegments(GuiGraphics graphics, int x, int y, int width, int height, int rgb) {
        if (width <= 0) return;
        graphics.setColor((rgb >> 16 & 0xFF) / 255f, (rgb >> 8 & 0xFF) / 255f, (rgb & 0xFF) / 255f, 1f);
        graphics.blitSprite(BAR, x, y, width, height);
        graphics.setColor(1f, 1f, 1f, 1f);
    }

    /** A one-pixel recess: gauges, a scrollbar's track, the step bar. */
    public static void inset(GuiGraphics graphics, int x, int y, int width, int height) {
        graphics.blitSprite(INSET, x, y, width, height);
    }

    /** A text field: a dark recess where white text reads. */
    public static void field(GuiGraphics graphics, int x, int y, int width, int height) {
        graphics.blitSprite(FIELD, x, y, width, height);
    }

    /** The 12x12 plus, centred in a slot whose frame starts at {@code (x, y)}. */
    public static void plus(GuiGraphics graphics, int x, int y) {
        graphics.blitSprite(PLUS, x + 3, y + 3, 12, 12);
    }

    public static void button(GuiGraphics graphics, int x, int y, int width, int height, boolean hovered) {
        graphics.blitSprite(hovered ? BUTTON_HOVER : BUTTON, x, y, width, height);
    }

    public static void statusLight(GuiGraphics graphics, GuiTheme.Status status, int x, int y) {
        graphics.blitSprite(id(status.sprite), x, y, GuiMetrics.LED_SIZE, GuiMetrics.LED_SIZE);
    }

    private static ResourceLocation id(String name) {
        return ResourceLocation.fromNamespaceAndPath(LaserAssembly.MOD_ID, name);
    }

    private GuiSprites() {}
}
