package dev.drimoz.laserassembly.client.table;

import dev.drimoz.laserassembly.LaserAssembly;
import dev.drimoz.laserassembly.client.gui.GuiSprites;
import dev.drimoz.laserassembly.client.gui.GuiTheme;
import dev.drimoz.laserassembly.client.gui.SideTab;
import dev.drimoz.laserassembly.content.beam.BeamType;
import dev.drimoz.laserassembly.content.beam.LensItem;
import dev.drimoz.laserassembly.content.laser.LaserBlockEntity;
import dev.drimoz.laserassembly.content.table.AssemblyTableMenu;
import dev.drimoz.laserassembly.registry.ModBlocks;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.IntConsumer;

/**
 * The red tab on the right: every laser aiming at the table, its lens, its intensity and how far it
 * stands - and the intensity set from here, one laser or all at once, without walking to each. Lenses
 * and augments are items: they still go in the laser itself.
 */
final class LasersTab extends SideTab {

    private static final int WIDTH = 112;
    private static final int ROW = 18;
    private static final int MAX_ROWS = 6;
    private static final int BUTTON = 11;
    private static final int MINUS_X = WIDTH - 2 * BUTTON - 2;
    private static final int PLUS_X = WIDTH - BUTTON;

    private final AssemblyTableMenu menu;
    private final IntConsumer click;

    LasersTab(AssemblyTableMenu menu, IntConsumer click) {
        super("lasers", Side.RIGHT, GuiTheme.TAB_LASERS);
        this.menu = menu;
        this.click = click;
    }

    @Override
    protected Component title() {
        return text("tab.lasers");
    }

    @Override
    protected GuiSprites.Icon icon() {
        return GuiSprites.Icon.LASERS;
    }

    @Override
    protected int contentWidth() {
        return WIDTH;
    }

    @Override
    protected int contentHeight() {
        return (1 + Math.max(1, Math.min(lasers().size(), MAX_ROWS))) * ROW;
    }

    @Override
    protected void renderContent(GuiGraphics graphics, Font font, int x, int y, int mouseX, int mouseY) {
        List<BlockPos> lasers = lasers();
        graphics.drawString(font, text("tab.lasers.all"), x, y + 5, GuiTheme.TAB_LABEL, true);
        buttons(graphics, font, x, y, mouseX, mouseY);
        if (lasers.isEmpty()) {
            graphics.drawString(font, text("tab.lasers.none"), x, y + ROW + 5, GuiTheme.TAB_MUTED, true);
            return;
        }
        for (int i = 0; i < Math.min(lasers.size(), MAX_ROWS); i++) {
            int rowY = y + (i + 1) * ROW;
            LaserBlockEntity laser = laser(lasers.get(i));
            if (laser == null) continue;
            graphics.renderItem(lensStack(laser.beam()), x, rowY + 1);
            graphics.drawString(font, Integer.toString(laser.intensity()), x + 20, rowY + 5, GuiTheme.TAB_VALUE, true);
            int distance = (int) Math.round(Math.sqrt(laser.getBlockPos().distSqr(menu.table().getBlockPos())));
            graphics.drawString(font, text("tab.lasers.distance", distance), x + 32, rowY + 5, GuiTheme.TAB_MUTED, true);
            // A Regulator sets the intensity itself: buttons here would fight it.
            if (laser.regulated()) graphics.drawString(font, text("tab.lasers.auto"), x + MINUS_X, rowY + 5, GuiTheme.TAB_LABEL, true);
            else buttons(graphics, font, x, rowY, mouseX, mouseY);
        }
        if (lasers.size() > MAX_ROWS) {
            graphics.drawString(font, "+" + (lasers.size() - MAX_ROWS), x + WIDTH - 12, y + ROW * MAX_ROWS + 5, GuiTheme.TAB_MUTED, true);
        }
    }

    private static void buttons(GuiGraphics graphics, Font font, int x, int rowY, int mouseX, int mouseY) {
        button(graphics, font, x + MINUS_X, rowY + 3, "-", mouseX, mouseY);
        button(graphics, font, x + PLUS_X, rowY + 3, "+", mouseX, mouseY);
    }

    private static void button(GuiGraphics graphics, Font font, int x, int y, String label, int mouseX, int mouseY) {
        GuiSprites.button(graphics, x, y, BUTTON, BUTTON, inside(mouseX, mouseY, x, y));
        graphics.drawString(font, label, x + (BUTTON - font.width(label) + 1) / 2, y + 2, GuiTheme.TEXT, false);
    }

    @Override
    protected boolean contentClicked(int x, int y, double mouseX, double mouseY) {
        List<BlockPos> lasers = lasers();
        for (int row = 0; row <= Math.min(lasers.size(), MAX_ROWS); row++) {
            int rowY = y + row * ROW + 3;
            for (int up = 0; up < 2; up++) {
                if (!inside(mouseX, mouseY, x + (up == 0 ? MINUS_X : PLUS_X), rowY)) continue;
                if (row > 0) {
                    LaserBlockEntity laser = laser(lasers.get(row - 1));
                    if (laser == null || laser.regulated()) return true;
                }
                click.accept(row == 0 ? AssemblyTableMenu.BUTTON_LASERS_ALL + up : AssemblyTableMenu.BUTTON_LASER + 2 * (row - 1) + up);
                return true;
            }
        }
        return false;
    }

    @Override
    protected List<Component> contentTooltip(int x, int y, int mouseX, int mouseY) {
        List<BlockPos> lasers = lasers();
        int row = (mouseY - y) / ROW - 1;
        if (mouseX >= x && mouseX < x + 18 && row >= 0 && row < Math.min(lasers.size(), MAX_ROWS)) {
            LaserBlockEntity laser = laser(lasers.get(row));
            if (laser != null) {
                BlockPos pos = laser.getBlockPos();
                Component beam = laser.beam() == null ? text("beam.raw") : BeamType.name(laser.beam());
                return List.of(beam, text("tab.lasers.position", pos.getX(), pos.getY(), pos.getZ()));
            }
        }
        return List.of();
    }

    private List<BlockPos> lasers() {
        return menu.table().lasers();
    }

    private @Nullable LaserBlockEntity laser(BlockPos pos) {
        return menu.table().getLevel().getBlockEntity(pos) instanceof LaserBlockEntity laser ? laser : null;
    }

    /** The lens it fires, or the laser itself for a raw beam: something to recognise it by. */
    private static ItemStack lensStack(@Nullable ResourceLocation beam) {
        return beam == null ? new ItemStack(ModBlocks.LASER_ITEM.get()) : LensItem.of(ResourceKey.create(BeamType.REGISTRY, beam));
    }

    private static boolean inside(double mouseX, double mouseY, int x, int y) {
        return mouseX >= x && mouseX < x + BUTTON && mouseY >= y && mouseY < y + BUTTON;
    }

    private static Component text(String key, Object... args) {
        return Component.translatable(LaserAssembly.MOD_ID + "." + key, args);
    }
}
