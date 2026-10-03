package dev.drimoz.laserassembly.client.table;

import dev.drimoz.laserassembly.LaserAssembly;
import dev.drimoz.laserassembly.ServerConfig;
import dev.drimoz.laserassembly.client.gui.GuiSprites;
import dev.drimoz.laserassembly.client.gui.GuiTheme;
import dev.drimoz.laserassembly.content.table.AssemblyTableBlock;
import dev.drimoz.laserassembly.content.table.AssemblyTableBlockEntity;
import dev.drimoz.laserassembly.content.table.AssemblyTableMenu;
import dev.drimoz.laserassembly.core.Assembly;
import dev.drimoz.laserassembly.core.Step;
import dev.drimoz.laserassembly.gui.GuiMetrics;
import dev.drimoz.laserassembly.recipe.AssemblyRecipe;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.neoforge.common.crafting.SizedIngredient;

import java.util.ArrayList;
import java.util.List;

import static dev.drimoz.laserassembly.content.table.AssemblyTableBlockEntity.*;
import static dev.drimoz.laserassembly.content.table.AssemblyTableMenu.*;

public class AssemblyTableScreen extends AbstractContainerScreen<AssemblyTableMenu> {

    private static final int CONTENT_HEIGHT = CONTENT_BOTTOM - GuiMetrics.CONTENT_TOP;
    /**
     * The table holds no energy - lasers bring it - so column 0, the energy column on every other
     * machine, shows what it does hold: heat. Column 4 is the progress of the current step.
     */
    private static final int PROGRESS_WIDTH = 8;
    private static final int PROGRESS_X = GuiMetrics.column(4) + (GuiMetrics.SLOT - PROGRESS_WIDTH) / 2;

    /** First visible row of the recipe grid. */
    private int scroll;

    public AssemblyTableScreen(AssemblyTableMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = GuiMetrics.WIDTH;
        imageHeight = GuiMetrics.height(CONTENT_BOTTOM);
        titleLabelX = GuiMetrics.MARGIN;
        titleLabelY = GuiMetrics.TITLE_Y;
        inventoryLabelX = GuiMetrics.MARGIN;
        inventoryLabelY = GuiMetrics.inventoryLabelY(CONTENT_BOTTOM);
    }

    // Drawing

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x = leftPos, y = topPos;
        GuiSprites.panel(graphics, x, y, imageWidth, imageHeight);
        for (Slot slot : menu.slots) GuiSprites.slot(graphics, x + slot.x - 1, y + slot.y - 1, false);

        int top = y + GuiMetrics.CONTENT_TOP;
        GuiSprites.gauge(graphics, GuiSprites.GAUGE_HEAT, x + GuiMetrics.GAUGE_X, top, GuiMetrics.GAUGE_WIDTH, CONTENT_HEIGHT,
                menu.data(DATA_HEAT) / 1000f);
        GuiSprites.gauge(graphics, GuiSprites.GAUGE_PROGRESS, x + PROGRESS_X, top, PROGRESS_WIDTH, CONTENT_HEIGHT, menu.data(DATA_PROGRESS) / 1000f);

        List<RecipeHolder<AssemblyRecipe>> recipes = menu.recipes();
        List<ResourceLocation> selected = menu.table().selected();
        int hovered = cellAt(mouseX, mouseY);
        for (int row = 0; row < GRID_ROWS; row++) {
            for (int col = 0; col < GRID_COLUMNS; col++) {
                int index = (scroll + row) * GRID_COLUMNS + col;
                int cellX = x + GuiMetrics.column(GRID_COLUMN + col), cellY = y + GuiMetrics.row(row);
                RecipeHolder<AssemblyRecipe> recipe = index < recipes.size() ? recipes.get(index) : null;
                boolean ticked = recipe != null && selected.contains(recipe.id());
                GuiSprites.slot(graphics, cellX, cellY, ticked);
                if (recipe == null) continue;
                graphics.renderFakeItem(recipe.value().getResultItem(minecraft.level.registryAccess()), cellX + 1, cellY + 1);
                // Ticked but not the one running: chosen, waiting its turn or its ingredients.
                if (ticked && !recipe.id().equals(menu.table().current())) wash(graphics, cellX, cellY, GuiTheme.GHOST_WASH);
                if (index == hovered) wash(graphics, cellX, cellY, GuiTheme.HOVER_WASH);
            }
        }

        GuiSprites.statusLight(graphics, status().light, x + GuiMetrics.LED_X, y + GuiMetrics.LED_Y);
    }

    /** Lifted above the item it covers, or it lands underneath. */
    private static void wash(GuiGraphics graphics, int cellX, int cellY, int argb) {
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 200);
        graphics.fill(cellX + 1, cellY + 1, cellX + 17, cellY + 17, argb);
        graphics.pose().popPose();
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, titleLabelX, titleLabelY, GuiTheme.TEXT, false);
        graphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, GuiTheme.TEXT, false);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);

        int x = mouseX - leftPos, y = mouseY - topPos;
        int cell = cellAt(mouseX, mouseY);
        if (cell >= 0) {
            graphics.renderComponentTooltip(font, recipeTooltip(menu.recipes().get(cell)), mouseX, mouseY);
        } else if (inColumn(x, y, GuiMetrics.GAUGE_X, GuiMetrics.GAUGE_WIDTH)) {
            graphics.renderTooltip(font, text("gui.heat", menu.data(DATA_HEAT) / 10), mouseX, mouseY);
        } else if (inColumn(x, y, PROGRESS_X, PROGRESS_WIDTH) && menu.data(DATA_STEPS) > 0) {
            graphics.renderTooltip(font, text("gui.progress", menu.data(DATA_STEP) + 1, menu.data(DATA_STEPS),
                    menu.data(DATA_PROGRESS) / 10), mouseX, mouseY);
        } else if (x >= GuiMetrics.LED_X && x < GuiMetrics.LED_X + GuiMetrics.LED_SIZE
                && y >= GuiMetrics.LED_Y && y < GuiMetrics.LED_Y + GuiMetrics.LED_SIZE) {
            graphics.renderTooltip(font, status().text, mouseX, mouseY);
        }
    }

    private List<Component> recipeTooltip(RecipeHolder<AssemblyRecipe> holder) {
        AssemblyRecipe recipe = holder.value();
        List<Component> lines = new ArrayList<>();
        lines.add(recipe.getResultItem(minecraft.level.registryAccess()).getHoverName());
        for (SizedIngredient sized : recipe.ingredients()) {
            ItemStack[] options = sized.ingredient().getItems();
            if (options.length > 0) lines.add(text("gui.recipe.ingredient", sized.count(), options[0].getHoverName()).withStyle(ChatFormatting.GRAY));
        }
        try {
            for (Step step : recipe.steps(ServerConfig.tuning())) {
                Component beam = step.beam().map(id -> Component.translatable("beam." + id.getNamespace() + "." + id.getPath()))
                        .orElse(text("beam.raw"));
                lines.add(text("gui.recipe.step", beam, step.energy(), formatIntensity(step.thresholds().min())));
            }
        } catch (IllegalArgumentException e) {
            // Unusable under this server's config: the table says so through its status light.
        }
        lines.add(text(menu.table().selected().contains(holder.id()) ? "gui.recipe.untick" : "gui.recipe.tick"));
        return lines;
    }

    private static String formatIntensity(double value) {
        return value == Math.rint(value) ? Integer.toString((int) value) : String.format("%.1f", value);
    }

    // Status

    private record Shown(GuiTheme.Status light, Component text) {}

    /** The band's light: what the table is doing, or the one thing stopping it. */
    private Shown status() {
        AssemblyTableBlockEntity table = menu.table();
        if (table.getBlockState().getValue(AssemblyTableBlock.POWERED)) return new Shown(GuiTheme.Status.OFF, text("status.paused"));
        if (table.selected().isEmpty()) return new Shown(GuiTheme.Status.OFF, text("status.nothing_ticked"));
        if (table.current() == null) return new Shown(GuiTheme.Status.WAITING, text("status.cannot_run"));
        return switch (Assembly.Status.values()[menu.data(DATA_STATUS)]) {
            case WORKING, DONE -> new Shown(GuiTheme.Status.WORKING, text("status.working"));
            case SLOWED -> new Shown(GuiTheme.Status.BLOCKED, text("status.slowed"));
            case IDLE -> new Shown(GuiTheme.Status.WAITING, text("status.no_beam"));
            case TOO_WEAK -> new Shown(GuiTheme.Status.WAITING, text("status.too_weak"));
            case OVERLOAD -> new Shown(GuiTheme.Status.PROBLEM, text("status.overload"));
            case OVERHEATED -> new Shown(GuiTheme.Status.PROBLEM, text("status.overheated"));
        };
    }

    // Input

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int cell = cellAt(mouseX, mouseY);
        if (cell < 0) return super.mouseClicked(mouseX, mouseY, button);
        minecraft.gameMode.handleInventoryButtonClick(menu.containerId, cell);
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int rows = (menu.recipes().size() + GRID_COLUMNS - 1) / GRID_COLUMNS;
        if (rows <= GRID_ROWS) return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        scroll = Math.clamp(scroll - (int) Math.signum(scrollY), 0, rows - GRID_ROWS);
        return true;
    }

    /** The recipe index under the mouse, or -1. */
    private int cellAt(double mouseX, double mouseY) {
        int x = (int) mouseX - leftPos - GuiMetrics.column(GRID_COLUMN);
        int y = (int) mouseY - topPos - GuiMetrics.CONTENT_TOP;
        if (x < 0 || y < 0 || x >= GRID_COLUMNS * GuiMetrics.SLOT || y >= GRID_ROWS * GuiMetrics.SLOT) return -1;
        int index = (scroll + y / GuiMetrics.SLOT) * GRID_COLUMNS + x / GuiMetrics.SLOT;
        return index < menu.recipes().size() ? index : -1;
    }

    private static boolean inColumn(int x, int y, int left, int width) {
        return x >= left && x < left + width && y >= GuiMetrics.CONTENT_TOP && y < CONTENT_BOTTOM;
    }

    private static net.minecraft.network.chat.MutableComponent text(String key, Object... args) {
        return Component.translatable(LaserAssembly.MOD_ID + "." + key, args);
    }
}
