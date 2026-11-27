package dev.drimoz.laserassembly.client.gui;

import net.minecraft.client.renderer.RenderPipelines;
import dev.drimoz.laserassembly.LaserAssembly;
import dev.drimoz.laserassembly.gui.GuiMetrics;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

/**
 * How each piece of the screen is drawn - FactoryIO's pieces, as GUI sprites written by
 * {@code tools/GenerateGuiSprites.java}. Each sprite carries its own scaling in its mcmeta, so no
 * class here knows a texture coordinate.
 */
public final class GuiSprites {

    private static final Identifier PANEL = id("panel");
    private static final Identifier INSET = id("inset");
    private static final Identifier SLOT = id("slot");
    private static final Identifier SLOT_SELECTED = id("slot_selected");
    private static final Identifier SLOT_DISABLED = id("slot_disabled");
    private static final Identifier GAUGE_EMPTY = id("gauge_empty");
    private static final Identifier SOCKET = id("socket");
    private static final Identifier TAB = id("tab");
    private static final Identifier SWITCH_ON = id("switch_on");
    private static final Identifier SWITCH_OFF = id("switch_off");
    private static final Identifier ARROW = id("arrow");
    private static final Identifier ARROW_LIT = id("arrow_lit");
    private static final Identifier FIELD = id("field");
    private static final Identifier PLUS = id("icon_plus");
    private static final Identifier BUTTON = id("button");
    private static final Identifier BUTTON_HOVER = id("button_hover");

    public static final Identifier GAUGE_ENERGY = id("gauge_energy");
    public static final Identifier GAUGE_HEAT = id("gauge_heat");
    public static final Identifier GAUGE_PROGRESS = id("gauge_progress");

    /** The main window, in the tone of vanilla containers. */
    public static void panel(GuiGraphicsExtractor graphics, int x, int y, int width, int height) {
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, PANEL, x, y, width, height);
    }

    /** The frame of a slot whose top-left frame corner is at {@code (x, y)}. */
    public static void slot(GuiGraphicsExtractor graphics, int x, int y, boolean selected) {
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, selected ? SLOT_SELECTED : SLOT, x, y, GuiMetrics.SLOT, GuiMetrics.SLOT);
    }

    /**
     * A vertical gauge filled from the bottom. Both layers are tiled from the gauge's top and the lit
     * one is clipped, so its segments stay put while the level moves.
     */
    public static void gauge(GuiGraphicsExtractor graphics, Identifier lit, int x, int y, int width, int height, float fill) {
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, INSET, x, y, width, height);
        int innerX = x + 1, innerY = y + 1, innerW = width - 2, innerH = height - 2;
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, GAUGE_EMPTY, innerX, innerY, innerW, innerH);
        int filled = Math.round(innerH * Math.clamp(fill, 0.0F, 1.0F));
        if (filled <= 0) return;
        graphics.enableScissor(innerX, innerY + innerH - filled, innerX + innerW, innerY + innerH);
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, lit, innerX, innerY, innerW, innerH);
        graphics.disableScissor();
    }

    /** Darker and hatched: a slot that will take nothing in the current configuration. */
    public static void disabledSlot(GuiGraphicsExtractor graphics, int x, int y) {
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, SLOT_DISABLED, x, y, GuiMetrics.SLOT, GuiMetrics.SLOT);
    }

    /** The large socket for what the screen is about: here, the recipe in progress. */
    public static void socket(GuiGraphicsExtractor graphics, int x, int y) {
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, SOCKET, x, y, GuiMetrics.SOCKET, GuiMetrics.SOCKET);
    }

    /** FactoryIO's 20x13 arrow, from inputs to outputs, filled green from the left over {@code fill} of its length. */
    public static void arrow(GuiGraphicsExtractor graphics, int x, int y, float fill) {
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, ARROW, x, y, 20, 13);
        int lit = Math.round(20 * Math.clamp(fill, 0f, 1f));
        if (lit <= 0) return;
        graphics.enableScissor(x, y, x + lit, y + 13);
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, ARROW_LIT, x, y, 20, 13);
        graphics.disableScissor();
    }

    /** A side tab, tinted the colour of its function. */
    public static void tab(GuiGraphicsExtractor graphics, int x, int y, int width, int height, int argb) {
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, TAB, x, y, width, height, argb);
    }

    public static final int SWITCH_W = 14;
    public static final int SWITCH_H = 8;

    /** A slide switch: green, knob right, when on. */
    public static void toggle(GuiGraphicsExtractor graphics, int x, int y, boolean on) {
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, on ? SWITCH_ON : SWITCH_OFF, x, y, SWITCH_W, SWITCH_H);
    }

    public static final int ICON_SIZE = 12;

    public enum Icon {
        INFO("icon_info");

        final Identifier sprite;

        Icon(String name) {
            this.sprite = id(name);
        }
    }

    public static void icon(GuiGraphicsExtractor graphics, Icon icon, int x, int y) {
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, icon.sprite, x, y, ICON_SIZE, ICON_SIZE);
    }

    /**
     * An item as a 12x12 icon - a hopper for faces, redstone for control: a block players know reads
     * faster than a pictogram of ours. Only the info tab, which no item stands for, keeps a sprite.
     */
    public static void icon(GuiGraphicsExtractor graphics, ItemStack item, int x, int y) {
        graphics.pose().pushMatrix();
        graphics.pose().translate(x, y);
        graphics.pose().scale(ICON_SIZE / 16F, ICON_SIZE / 16F);
        graphics.item(item, 0, 0);
        graphics.pose().popMatrix();
    }

    /** A one-pixel recess: gauges, a scrollbar's track, the step bar. */
    public static void inset(GuiGraphicsExtractor graphics, int x, int y, int width, int height) {
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, INSET, x, y, width, height);
    }

    /** A text field: a dark recess where white text reads. */
    public static void field(GuiGraphicsExtractor graphics, int x, int y, int width, int height) {
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, FIELD, x, y, width, height);
    }

    /** The 12x12 plus, centred in a slot whose frame starts at {@code (x, y)}. */
    public static void plus(GuiGraphicsExtractor graphics, int x, int y) {
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, PLUS, x + 3, y + 3, 12, 12);
    }

    public static void button(GuiGraphicsExtractor graphics, int x, int y, int width, int height, boolean hovered) {
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, hovered ? BUTTON_HOVER : BUTTON, x, y, width, height);
    }

    public static void statusLight(GuiGraphicsExtractor graphics, GuiTheme.Status status, int x, int y) {
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, id(status.sprite), x, y, GuiMetrics.LED_SIZE, GuiMetrics.LED_SIZE);
    }

    private static Identifier id(String name) {
        return Identifier.fromNamespaceAndPath(LaserAssembly.MOD_ID, name);
    }

    private GuiSprites() {}
}
