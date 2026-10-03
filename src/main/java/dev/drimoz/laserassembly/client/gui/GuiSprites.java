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

    public static void statusLight(GuiGraphics graphics, GuiTheme.Status status, int x, int y) {
        graphics.blitSprite(id(status.sprite), x, y, GuiMetrics.LED_SIZE, GuiMetrics.LED_SIZE);
    }

    private static ResourceLocation id(String name) {
        return ResourceLocation.fromNamespaceAndPath(LaserAssembly.MOD_ID, name);
    }

    private GuiSprites() {}
}
