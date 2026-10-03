package dev.drimoz.laserassembly.client.table;

import dev.drimoz.laserassembly.LaserAssembly;
import dev.drimoz.laserassembly.client.gui.GuiSprites;
import dev.drimoz.laserassembly.client.gui.GuiTheme;
import dev.drimoz.laserassembly.client.gui.SideTab;
import dev.drimoz.laserassembly.content.table.AssemblyTableMenu;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.function.Supplier;

import static dev.drimoz.laserassembly.content.table.AssemblyTableBlockEntity.*;

/**
 * The yellow tab on the left, as on every machine of the family: what the table is doing, and the
 * numbers behind it - heat against what the blocks around it take away, and how many beams arrive.
 */
final class InfoTab extends SideTab {

    private static final int WIDTH = 120;
    private static final int LINES = 4;

    private final AssemblyTableMenu menu;
    private final Supplier<Component> status;

    InfoTab(AssemblyTableMenu menu, Supplier<Component> status) {
        super("info", Side.LEFT, GuiTheme.TAB_INFO);
        this.menu = menu;
        this.status = status;
    }

    @Override
    protected Component title() {
        return text("tab.info");
    }

    @Override
    protected GuiSprites.Icon icon() {
        return GuiSprites.Icon.INFO;
    }

    @Override
    protected int contentWidth() {
        return WIDTH;
    }

    @Override
    protected int contentHeight() {
        return LINES * GuiTheme.LINE * 2 - GuiTheme.LINE / 2;
    }

    @Override
    protected void renderContent(GuiGraphics graphics, Font font, int x, int y, int mouseX, int mouseY) {
        line(graphics, font, x, y, text("tab.info.status"), status.get());
        line(graphics, font, x, y + 2 * GuiTheme.LINE, text("tab.info.heat"), text("tab.info.heat_value", menu.data(DATA_HEAT) / 10));
        line(graphics, font, x, y + 4 * GuiTheme.LINE, text("tab.info.cooling"),
                text("tab.info.cooling_value", String.format("%.1f", menu.data(DATA_COOLING) / 10.0)));
        line(graphics, font, x, y + 6 * GuiTheme.LINE, text("tab.info.beams"), Component.literal(Integer.toString(menu.data(DATA_BEAMS))));
    }

    /** A label, and its value beneath it: tabs are narrow, and a value never wraps under its label. */
    private static void line(GuiGraphics graphics, Font font, int x, int y, Component label, Component value) {
        graphics.drawString(font, label, x, y, GuiTheme.TAB_LABEL, true);
        graphics.drawString(font, value, x + 4, y + GuiTheme.LINE, GuiTheme.TAB_VALUE, true);
    }

    private static Component text(String key, Object... args) {
        return Component.translatable(LaserAssembly.MOD_ID + "." + key, args);
    }
}
