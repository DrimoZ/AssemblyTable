package dev.drimoz.laserassembly.client.table;

import dev.drimoz.laserassembly.LaserAssembly;
import dev.drimoz.laserassembly.client.gui.GuiSprites;
import dev.drimoz.laserassembly.client.gui.GuiTheme;
import dev.drimoz.laserassembly.client.gui.SideTab;
import dev.drimoz.laserassembly.content.table.AssemblyTableMenu;
import dev.drimoz.laserassembly.content.table.FaceMode;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;
import java.util.function.IntConsumer;

import static dev.drimoz.laserassembly.content.table.AssemblyTableBlockEntity.DATA_AUTO;
import static dev.drimoz.laserassembly.content.table.AssemblyTableBlockEntity.DATA_FACES;

/**
 * The blue tab on the right, as Thermal does it: what each face lets pipes and hoppers do, and
 * whether the table pulls its ingredients and pushes its results by itself. The top is not listed:
 * it is the face of the assembly on display, always open and never automatic.
 */
final class FacesTab extends SideTab {

    private static final ItemStack ICON = new ItemStack(Items.HOPPER);

    private static final Direction[] FACES = {Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST, Direction.DOWN};
    private static final int WIDTH = 116;
    private static final int ROW = 14;
    private static final int BUTTON_X = 52;
    private static final int BUTTON_W = 62;
    private static final int BUTTON_H = 12;
    private static final int TOGGLES_Y = FACES.length * ROW + 4;

    private final AssemblyTableMenu menu;
    private final IntConsumer click;

    FacesTab(AssemblyTableMenu menu, IntConsumer click) {
        super("faces", Side.RIGHT, GuiTheme.TAB_FACES);
        this.menu = menu;
        this.click = click;
    }

    @Override
    protected Component title() {
        return text("tab.faces");
    }

    @Override
    protected void renderIcon(GuiGraphicsExtractor graphics, int x, int y) {
        GuiSprites.icon(graphics, ICON, x, y);
    }

    @Override
    protected int contentWidth() {
        return WIDTH;
    }

    @Override
    protected int contentHeight() {
        return TOGGLES_Y + 2 * ROW;
    }

    @Override
    protected void renderContent(GuiGraphicsExtractor graphics, Font font, int x, int y, int mouseX, int mouseY) {
        for (int i = 0; i < FACES.length; i++) {
            int rowY = y + i * ROW;
            graphics.text(font, text("face." + FACES[i].getName()), x, rowY + 2, GuiTheme.TAB_LABEL, true);
            boolean hovered = inside(mouseX, mouseY, x + BUTTON_X, rowY, BUTTON_W, BUTTON_H);
            GuiSprites.button(graphics, x + BUTTON_X, rowY, BUTTON_W, BUTTON_H, hovered);
            FaceMode mode = mode(FACES[i]);
            Component label = text("face.mode." + mode.name().toLowerCase());
            graphics.text(font, label, x + BUTTON_X + (BUTTON_W - font.width(label)) / 2, rowY + 2, colour(mode), false);
        }
        toggleRow(graphics, font, x, y + TOGGLES_Y, text("face.auto_import"), (menu.data(DATA_AUTO) & 1) != 0);
        toggleRow(graphics, font, x, y + TOGGLES_Y + ROW, text("face.auto_export"), (menu.data(DATA_AUTO) & 2) != 0);
    }

    private static void toggleRow(GuiGraphicsExtractor graphics, Font font, int x, int y, Component label, boolean on) {
        graphics.text(font, label, x, y + 2, GuiTheme.TAB_LABEL, true);
        GuiSprites.toggle(graphics, x + WIDTH - GuiSprites.SWITCH_W, y + 2, on);
    }

    @Override
    protected boolean contentClicked(int x, int y, double mouseX, double mouseY) {
        for (int i = 0; i < FACES.length; i++) {
            if (inside(mouseX, mouseY, x + BUTTON_X, y + i * ROW, BUTTON_W, BUTTON_H)) {
                click.accept(AssemblyTableMenu.BUTTON_FACE + FACES[i].ordinal());
                return true;
            }
        }
        for (int i = 0; i < 2; i++) {
            if (inside(mouseX, mouseY, x, y + TOGGLES_Y + i * ROW, WIDTH, ROW)) {
                click.accept(i == 0 ? AssemblyTableMenu.BUTTON_AUTO_IMPORT : AssemblyTableMenu.BUTTON_AUTO_EXPORT);
                return true;
            }
        }
        return false;
    }

    @Override
    protected List<Component> contentTooltip(int x, int y, int mouseX, int mouseY) {
        if (inside(mouseX, mouseY, x, y + TOGGLES_Y, WIDTH, ROW)) return List.of(text("face.auto_import.hint"));
        if (inside(mouseX, mouseY, x, y + TOGGLES_Y + ROW, WIDTH, ROW)) return List.of(text("face.auto_export.hint"));
        return List.of();
    }

    private FaceMode mode(Direction face) {
        return FaceMode.values()[(menu.data(DATA_FACES) >> (2 * face.ordinal())) & 3];
    }

    /** Dark enough to read on a button face: off grey, in blue, out amber, both green. */
    private static int colour(FaceMode mode) {
        return switch (mode) {
            case NONE -> 0xFF6E6E6E;
            case INPUT -> 0xFF2A5AB0;
            case OUTPUT -> 0xFFB06010;
            case BOTH -> 0xFF2E8B2E;
        };
    }

    private static boolean inside(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    private static Component text(String key) {
        return Component.translatable(LaserAssembly.MOD_ID + "." + key);
    }
}
