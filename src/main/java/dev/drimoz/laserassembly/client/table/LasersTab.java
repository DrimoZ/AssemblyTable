package dev.drimoz.laserassembly.client.table;

import dev.drimoz.laserassembly.LaserAssembly;
import dev.drimoz.laserassembly.client.BeamColors;
import dev.drimoz.laserassembly.client.gui.GuiSprites;
import dev.drimoz.laserassembly.client.gui.GuiTheme;
import dev.drimoz.laserassembly.client.gui.SideTab;
import dev.drimoz.laserassembly.content.beam.BeamType;
import dev.drimoz.laserassembly.content.laser.LaserBlockEntity;
import dev.drimoz.laserassembly.content.table.AssemblyTableMenu;
import dev.drimoz.laserassembly.registry.ModBlocks;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;

/**
 * The red tab on the right: every laser aiming at the table, its lenses and its intensity - and the
 * intensity set from here without walking to each, the top row for every laser at once, as its label
 * says. Where a laser stands is in its tooltip: a bare distance beside the intensity read as a second
 * setting. Lenses and augments are items: they still go in the laser itself.
 */
final class LasersTab extends SideTab {

    private static final ItemStack ICON = new ItemStack(ModBlocks.LASER_ITEM.get());

    private static final int WIDTH = 132;
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
    protected void renderIcon(GuiGraphics graphics, int x, int y) {
        GuiSprites.icon(graphics, ICON, x, y);
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
            graphics.renderItem(new ItemStack(ModBlocks.LASER_ITEM.get()), x, rowY + 1);
            GuiSprites.statusLight(graphics, light(laser.state()), x + 19, rowY + 1);
            // A dot per lens it carries, in its beam's colour; the one firing ringed in white.
            List<ResourceLocation> lenses = laser.lenses();
            for (int l = 0; l < lenses.size(); l++) {
                int dotX = x + 19 + l * 6, dotY = rowY + 11;
                if (laser.firing() && lenses.get(l).equals(laser.beam())) graphics.fill(dotX - 1, dotY - 1, dotX + 5, dotY + 5, 0xFFFFFFFF);
                graphics.fill(dotX, dotY, dotX + 4, dotY + 4, 0xFF000000 | BeamColors.rgb(lenses.get(l)));
            }
            // A Regulator sets the intensity itself: buttons here would fight it, so it reads as a value.
            if (laser.regulated()) {
                graphics.drawString(font, text("tab.lasers.auto", laser.intensity()), x + 44, rowY + 5, GuiTheme.TAB_MUTED, true);
            } else {
                graphics.drawString(font, text("tab.lasers.intensity", laser.intensity()), x + 44, rowY + 5, GuiTheme.TAB_VALUE, true);
                buttons(graphics, font, x, rowY, mouseX, mouseY);
            }
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
        if (mouseX >= x && mouseX < x + MINUS_X && row >= 0 && row < Math.min(lasers.size(), MAX_ROWS)) {
            LaserBlockEntity laser = laser(lasers.get(row));
            if (laser != null) {
                BlockPos pos = laser.getBlockPos();
                List<Component> lines = new ArrayList<>(List.of(text("laser." + stateKey(laser.state()))));
                for (ResourceLocation lens : laser.lenses()) lines.add(Component.literal("■ ").withColor(0xFF000000 | BeamColors.rgb(lens)).append(BeamType.name(lens)));
                if (laser.lenses().isEmpty()) lines.add(text("beam.raw"));
                int distance = (int) Math.round(Math.sqrt(pos.distSqr(menu.table().getBlockPos())));
                lines.add(text("tab.lasers.distance", distance).append(" · ")
                        .append(text("tab.lasers.position", pos.getX(), pos.getY(), pos.getZ())).withStyle(ChatFormatting.GRAY));
                return lines;
            }
        }
        return List.of();
    }

    private static GuiTheme.Status light(int state) {
        return switch (state) {
            case LaserBlockEntity.STATE_FIRING -> GuiTheme.Status.WORKING;
            case LaserBlockEntity.STATE_HOLDING -> GuiTheme.Status.WAITING;
            case LaserBlockEntity.STATE_TOO_WEAK, LaserBlockEntity.STATE_TOO_STRONG, LaserBlockEntity.STATE_WRONG_BEAM -> GuiTheme.Status.BLOCKED;
            default -> GuiTheme.Status.PROBLEM;
        };
    }

    private static String stateKey(int state) {
        return switch (state) {
            case LaserBlockEntity.STATE_FIRING -> "firing";
            case LaserBlockEntity.STATE_HOLDING -> "holding";
            case LaserBlockEntity.STATE_NO_POWER -> "no_power";
            case LaserBlockEntity.STATE_TOO_WEAK -> "too_weak";
            case LaserBlockEntity.STATE_TOO_STRONG -> "too_strong";
            case LaserBlockEntity.STATE_WRONG_BEAM -> "wrong_beam";
            default -> "no_target";
        };
    }

    private List<BlockPos> lasers() {
        return menu.table().lasers();
    }

    private @Nullable LaserBlockEntity laser(BlockPos pos) {
        return menu.table().getLevel().getBlockEntity(pos) instanceof LaserBlockEntity laser ? laser : null;
    }

    private static boolean inside(double mouseX, double mouseY, int x, int y) {
        return mouseX >= x && mouseX < x + BUTTON && mouseY >= y && mouseY < y + BUTTON;
    }

    private static MutableComponent text(String key, Object... args) {
        return Component.translatable(LaserAssembly.MOD_ID + "." + key, args);
    }
}
