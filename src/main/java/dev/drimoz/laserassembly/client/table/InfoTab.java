package dev.drimoz.laserassembly.client.table;

import dev.drimoz.laserassembly.LaserAssembly;
import dev.drimoz.laserassembly.client.gui.GuiSprites;
import dev.drimoz.laserassembly.client.gui.GuiTheme;
import dev.drimoz.laserassembly.client.gui.SideTab;
import dev.drimoz.laserassembly.content.table.AssemblyTableMenu;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

import java.util.function.Supplier;
import dev.drimoz.laserassembly.client.gui.GuiTheme.Status;

import static dev.drimoz.laserassembly.content.table.AssemblyTableBlockEntity.*;

/**
 * The yellow tab on the left, as on every machine of the family: what the table is doing, and the
 * numbers behind it - heat against what the blocks around it take away, and how many beams arrive.
 */
final class InfoTab extends SideTab {

    private static final int WIDTH = 120;
    /** A label and its value, then FactoryIO's 3-pixel gap before the next. */
    private static final int ROW = 2 * GuiTheme.LINE + 3;
    private static final int ROWS = 5;

    private final AssemblyTableMenu menu;
    private final Supplier<Status> light;
    private final Supplier<Component> status;

    InfoTab(AssemblyTableMenu menu, Supplier<Status> light, Supplier<Component> status) {
        super("info", Side.LEFT, GuiTheme.TAB_INFO);
        this.menu = menu;
        this.light = light;
        this.status = status;
    }

    @Override
    protected Component title() {
        return text("tab.info");
    }

    @Override
    protected void renderIcon(GuiGraphicsExtractor graphics, int x, int y) {
        GuiSprites.icon(graphics, GuiSprites.Icon.INFO, x, y);
    }

    @Override
    protected int contentWidth() {
        return WIDTH;
    }

    @Override
    protected int contentHeight() {
        return ROWS * ROW - 3;
    }

    @Override
    protected void renderContent(GuiGraphicsExtractor graphics, Font font, int x, int y, int mouseX, int mouseY) {
        line(graphics, font, x, y, text("tab.info.status"), status.get(), light.get().tabColour);
        line(graphics, font, x, y + ROW, text("tab.info.heat"), text("tab.info.heat_value", menu.data(DATA_HEAT) / 10), GuiTheme.TAB_VALUE);
        line(graphics, font, x, y + 2 * ROW, text("tab.info.cooling"),
                text("tab.info.cooling_value", String.format("%.1f", menu.data(DATA_COOLING) / 10.0)), GuiTheme.TAB_VALUE);
        line(graphics, font, x, y + 3 * ROW, text("tab.info.beams"), Component.literal(Integer.toString(menu.data(DATA_BEAMS))), GuiTheme.TAB_VALUE);
        int speed = menu.data(DATA_SPEED);
        line(graphics, font, x, y + 4 * ROW, text("tab.info.speed"), speed == 0 ? text("tab.info.speed_none")
                : text("tab.info.speed_value", String.format("%.2f", speed / 100.0)), GuiTheme.TAB_VALUE);
    }

    /** A label, and its value beneath it, as FactoryIO's tabs: tabs are narrow, and a value never wraps. */
    private static void line(GuiGraphicsExtractor graphics, Font font, int x, int y, Component label, Component value, int colour) {
        graphics.text(font, label, x, y, GuiTheme.TAB_LABEL, true);
        graphics.text(font, value, x, y + GuiTheme.LINE, colour, true);
    }

    private static Component text(String key, Object... args) {
        return Component.translatable(LaserAssembly.MOD_ID + "." + key, args);
    }
}
