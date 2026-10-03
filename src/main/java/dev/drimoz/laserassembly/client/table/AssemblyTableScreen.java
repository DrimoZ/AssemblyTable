package dev.drimoz.laserassembly.client.table;

import dev.drimoz.laserassembly.LaserAssembly;
import dev.drimoz.laserassembly.ServerConfig;
import dev.drimoz.laserassembly.client.BeamColors;
import dev.drimoz.laserassembly.client.gui.GuiSprites;
import dev.drimoz.laserassembly.client.gui.GuiTheme;
import dev.drimoz.laserassembly.content.beam.BeamType;
import dev.drimoz.laserassembly.content.table.AssemblyTableBlock;
import dev.drimoz.laserassembly.content.table.AssemblyTableBlockEntity;
import dev.drimoz.laserassembly.content.table.AssemblyTableMenu;
import dev.drimoz.laserassembly.core.Assembly;
import dev.drimoz.laserassembly.core.Step;
import dev.drimoz.laserassembly.gui.GuiMetrics;
import dev.drimoz.laserassembly.recipe.AssemblyRecipe;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.neoforge.common.crafting.SizedIngredient;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static dev.drimoz.laserassembly.content.table.AssemblyTableBlockEntity.*;
import static dev.drimoz.laserassembly.content.table.AssemblyTableMenu.*;

/**
 * <pre>
 *  ┌────────────────────────────────────────┐
 *  │ Assembly Table                       ● │
 *  │ ▌ [ ][ ][ ] ▌  [r][r][r][r]            │  heat, inputs 3x4, progress; ticked recipes 4x2,
 *  │ ▌ [ ][ ][ ] ▌  [r][r][+][ ]            │  the running one rimmed, a + for the picker
 *  │ ▌ [ ][ ][ ] ▌  [▮2][▮4][▮1]            │  the running recipe's steps, the current rimmed
 *  │ ▌ [ ][ ][ ] ▌  [o][o][o][o]            │  outputs
 *  └────────────────────────────────────────┘
 * </pre>
 * Choosing recipes happens in {@link RecipePicker}, a modal over the whole screen: the grid that
 * used to live here could not hold a modpack's worth of recipes.
 */
public class AssemblyTableScreen extends AbstractContainerScreen<AssemblyTableMenu> {

    private static final int CONTENT_HEIGHT = CONTENT_BOTTOM - GuiMetrics.CONTENT_TOP;
    /**
     * The table holds no energy - lasers bring it - so column 0, the energy column on every other
     * machine, shows what it does hold: heat. Column 4 is the progress of the current step.
     */
    private static final int PROGRESS_WIDTH = 8;
    private static final int PROGRESS_X = GuiMetrics.column(4) + (GuiMetrics.SLOT - PROGRESS_WIDTH) / 2;

    private static final int TICKED_COLUMNS = 4;
    private static final int TICKED_CELLS = TICKED_COLUMNS * 2;
    private static final int STEPS_Y = GuiMetrics.row(2) + 1;
    private static final int CHIP_WIDTH = 24;
    private static final int CHIP_HEIGHT = 16;

    private final Map<ResourceLocation, RecipeHolder<AssemblyRecipe>> byId = new HashMap<>();
    private RecipePicker picker;

    public AssemblyTableScreen(AssemblyTableMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = GuiMetrics.WIDTH;
        imageHeight = GuiMetrics.height(CONTENT_BOTTOM);
        titleLabelX = GuiMetrics.MARGIN;
        titleLabelY = GuiMetrics.TITLE_Y;
        inventoryLabelX = GuiMetrics.MARGIN;
        inventoryLabelY = GuiMetrics.inventoryLabelY(CONTENT_BOTTOM);
        for (RecipeHolder<AssemblyRecipe> recipe : menu.recipes()) byId.put(recipe.id(), recipe);
    }

    @Override
    protected void init() {
        super.init();
        picker = new RecipePicker(font, imageHeight, menu.recipes(), () -> menu.table().selected(), this::toggle);
        addWidget(picker.search());
    }

    private void toggle(RecipeHolder<AssemblyRecipe> recipe) {
        minecraft.gameMode.handleInventoryButtonClick(menu.containerId, menu.recipes().indexOf(recipe));
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }

    // Drawing

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (picker.isOpen()) {
            renderBackground(graphics, mouseX, mouseY, partialTick);
            picker.render(graphics, leftPos, topPos, imageHeight, mouseX, mouseY, partialTick);
            return;
        }
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
        List<Component> tooltip = tooltipAt(mouseX, mouseY);
        if (!tooltip.isEmpty()) graphics.renderComponentTooltip(font, tooltip, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x = leftPos, y = topPos;
        GuiSprites.panel(graphics, x, y, imageWidth, imageHeight);
        for (Slot slot : menu.slots) GuiSprites.slot(graphics, x + slot.x - 1, y + slot.y - 1, false);

        int top = y + GuiMetrics.CONTENT_TOP;
        GuiSprites.gauge(graphics, GuiSprites.GAUGE_HEAT, x + GuiMetrics.GAUGE_X, top, GuiMetrics.GAUGE_WIDTH, CONTENT_HEIGHT,
                menu.data(DATA_HEAT) / 1000f);
        GuiSprites.gauge(graphics, GuiSprites.GAUGE_PROGRESS, x + PROGRESS_X, top, PROGRESS_WIDTH, CONTENT_HEIGHT, menu.data(DATA_PROGRESS) / 1000f);

        renderTicked(graphics, mouseX, mouseY);
        renderSteps(graphics);
        GuiSprites.statusLight(graphics, status().light, x + GuiMetrics.LED_X, y + GuiMetrics.LED_Y);
    }

    /** The ticked recipes in the order they take turns; the running one rimmed, the others waiting. */
    private void renderTicked(GuiGraphics graphics, int mouseX, int mouseY) {
        List<ResourceLocation> ticked = menu.table().selected();
        int hovered = tickedCellAt(mouseX, mouseY);
        int shown = Math.min(ticked.size(), TICKED_CELLS - 1);
        for (int cell = 0; cell < TICKED_CELLS; cell++) {
            int cellX = leftPos + tickedX(cell), cellY = topPos + tickedY(cell);
            RecipeHolder<AssemblyRecipe> recipe = cell < shown ? byId.get(ticked.get(cell)) : null;
            boolean running = recipe != null && recipe.id().equals(menu.table().current());
            GuiSprites.slot(graphics, cellX, cellY, running);
            if (recipe != null) {
                graphics.renderFakeItem(result(recipe), cellX + 1, cellY + 1);
                if (!running) wash(graphics, cellX, cellY, GuiTheme.GHOST_WASH);
            } else if (cell == shown) {
                GuiSprites.plus(graphics, cellX, cellY);
            }
            if (cell == hovered && cell <= shown) wash(graphics, cellX, cellY, GuiTheme.HOVER_WASH);
        }
    }

    /** One chip per step of the running recipe: its beam's colour and minimum intensity. */
    private void renderSteps(GuiGraphics graphics) {
        List<Step> steps = runningSteps();
        int current = menu.data(DATA_STEP);
        for (int i = 0; i < steps.size(); i++) {
            Step step = steps.get(i);
            int chipX = leftPos + GuiMetrics.column(GRID_COLUMN) + i * CHIP_WIDTH, chipY = topPos + STEPS_Y;
            GuiSprites.inset(graphics, chipX, chipY, CHIP_WIDTH - 2, CHIP_HEIGHT);
            graphics.fill(chipX + 2, chipY + 2, chipX + 6, chipY + CHIP_HEIGHT - 2, 0xFF000000 | BeamColors.rgb(step.beam().orElse(null)));
            graphics.drawString(font, intensity(step.thresholds().min()), chipX + 9, chipY + 4, GuiTheme.TEXT, false);
            if (i == current) outline(graphics, chipX - 1, chipY - 1, CHIP_WIDTH, CHIP_HEIGHT + 2, GuiTheme.SELECTED);
            else if (i < current) wash(graphics, chipX - 1, chipY - 1, GuiTheme.GHOST_WASH);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, titleLabelX, titleLabelY, GuiTheme.TEXT, false);
        graphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, GuiTheme.TEXT, false);
    }

    // Tooltips

    private List<Component> tooltipAt(int mouseX, int mouseY) {
        int x = mouseX - leftPos, y = mouseY - topPos;
        int cell = tickedCellAt(mouseX, mouseY);
        List<ResourceLocation> ticked = menu.table().selected();
        int shown = Math.min(ticked.size(), TICKED_CELLS - 1);
        if (cell >= 0 && cell < shown) {
            RecipeHolder<AssemblyRecipe> recipe = byId.get(ticked.get(cell));
            return recipe == null ? List.of(Component.literal(ticked.get(cell).toString())) : describe(recipe, true);
        }
        if (cell == shown) {
            List<Component> lines = new ArrayList<>(List.of(text("gui.add")));
            if (ticked.size() > shown) lines.add(text("gui.more", ticked.size() - shown).withStyle(ChatFormatting.GRAY));
            return lines;
        }
        int chip = (x - GuiMetrics.column(GRID_COLUMN)) / CHIP_WIDTH;
        List<Step> steps = runningSteps();
        if (y >= STEPS_Y && y < STEPS_Y + CHIP_HEIGHT && x >= GuiMetrics.column(GRID_COLUMN) && chip < steps.size()) {
            return stepTooltip(steps.get(chip), chip, steps.size());
        }
        if (inColumn(x, y, GuiMetrics.GAUGE_X, GuiMetrics.GAUGE_WIDTH)) return List.of(text("gui.heat", menu.data(DATA_HEAT) / 10));
        if (inColumn(x, y, PROGRESS_X, PROGRESS_WIDTH) && menu.data(DATA_STEPS) > 0) {
            return List.of(text("gui.progress", menu.data(DATA_STEP) + 1, menu.data(DATA_STEPS), menu.data(DATA_PROGRESS) / 10));
        }
        if (x >= GuiMetrics.LED_X && x < GuiMetrics.LED_X + GuiMetrics.LED_SIZE && y >= GuiMetrics.LED_Y && y < GuiMetrics.LED_Y + GuiMetrics.LED_SIZE) {
            return List.of(status().text);
        }
        return List.of();
    }

    private List<Component> stepTooltip(Step step, int index, int count) {
        List<Component> lines = new ArrayList<>();
        lines.add(text("gui.step", index + 1, count, beamName(step.beam().orElse(null))));
        lines.add(text("gui.step.window", intensity(step.thresholds().min()), intensity(step.thresholds().max()),
                intensity(step.thresholds().heatAbove())).withStyle(ChatFormatting.GRAY));
        lines.add(text("gui.recipe.energy", step.energy()).withStyle(ChatFormatting.GRAY));
        if (step.strict()) lines.add(text("gui.step.strict").withStyle(ChatFormatting.GOLD));
        return lines;
    }

    /** What a recipe makes, takes and goes through. Shared with the picker. */
    static List<Component> describe(RecipeHolder<AssemblyRecipe> holder, boolean ticked) {
        AssemblyRecipe recipe = holder.value();
        List<Component> lines = new ArrayList<>();
        lines.add(result(holder).getHoverName());
        for (SizedIngredient sized : recipe.ingredients()) {
            ItemStack[] options = sized.ingredient().getItems();
            if (options.length > 0) lines.add(text("gui.recipe.ingredient", sized.count(), options[0].getHoverName()).withStyle(ChatFormatting.GRAY));
        }
        try {
            for (Step step : recipe.steps(ServerConfig.tuning())) {
                lines.add(text("gui.recipe.step", beamName(step.beam().orElse(null)), step.energy(), intensity(step.thresholds().min()))
                        .withStyle(ChatFormatting.DARK_AQUA));
            }
        } catch (IllegalArgumentException e) {
            // Unusable under this server's config: the table says so through its status light.
        }
        lines.add(text(ticked ? "gui.recipe.untick" : "gui.recipe.tick").withStyle(ChatFormatting.DARK_GRAY));
        return lines;
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
        if (picker.isOpen()) return picker.mouseClicked(leftPos, topPos, mouseX, mouseY, button);
        List<ResourceLocation> ticked = menu.table().selected();
        int shown = Math.min(ticked.size(), TICKED_CELLS - 1);
        int cell = tickedCellAt(mouseX, mouseY);
        if (cell >= 0 && cell < shown && byId.containsKey(ticked.get(cell))) {
            toggle(byId.get(ticked.get(cell)));
            return true;
        }
        if (cell == shown) {
            picker.open();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        return picker.isOpen() || super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        return picker.isOpen() || super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (!picker.isOpen()) return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        picker.mouseScrolled(scrollY);
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return picker.isOpen() ? picker.keyPressed(keyCode, scanCode, modifiers) : super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        return picker.isOpen() ? picker.charTyped(codePoint, modifiers) : super.charTyped(codePoint, modifiers);
    }

    // Inner work

    private List<Step> runningSteps() {
        ResourceLocation current = menu.table().current();
        RecipeHolder<AssemblyRecipe> recipe = current == null ? null : byId.get(current);
        if (recipe == null) return List.of();
        try {
            return recipe.value().steps(ServerConfig.tuning());
        } catch (IllegalArgumentException e) {
            return List.of();
        }
    }

    private static int tickedX(int cell) {
        return GuiMetrics.column(GRID_COLUMN + cell % TICKED_COLUMNS);
    }

    private static int tickedY(int cell) {
        return GuiMetrics.row(cell / TICKED_COLUMNS);
    }

    /** The ticked-recipe cell under the mouse, or -1. */
    private int tickedCellAt(double mouseX, double mouseY) {
        int x = (int) mouseX - leftPos - GuiMetrics.column(GRID_COLUMN), y = (int) mouseY - topPos - GuiMetrics.CONTENT_TOP;
        if (x < 0 || y < 0 || x >= TICKED_COLUMNS * GuiMetrics.SLOT || y >= 2 * GuiMetrics.SLOT) return -1;
        return (y / GuiMetrics.SLOT) * TICKED_COLUMNS + x / GuiMetrics.SLOT;
    }

    private static boolean inColumn(int x, int y, int left, int width) {
        return x >= left && x < left + width && y >= GuiMetrics.CONTENT_TOP && y < CONTENT_BOTTOM;
    }

    /** Lifted above the item it covers, or it lands underneath. */
    static void wash(GuiGraphics graphics, int frameX, int frameY, int argb) {
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 200);
        graphics.fill(frameX + 1, frameY + 1, frameX + 17, frameY + 17, argb);
        graphics.pose().popPose();
    }

    private static void outline(GuiGraphics graphics, int x, int y, int width, int height, int argb) {
        graphics.fill(x, y, x + width, y + 1, argb);
        graphics.fill(x, y + height - 1, x + width, y + height, argb);
        graphics.fill(x, y, x + 1, y + height, argb);
        graphics.fill(x + width - 1, y, x + width, y + height, argb);
    }

    private static ItemStack result(RecipeHolder<AssemblyRecipe> recipe) {
        return recipe.value().getResultItem(Minecraft.getInstance().level.registryAccess());
    }

    private static Component beamName(@Nullable ResourceLocation beam) {
        return beam == null ? text("beam.raw") : BeamType.name(beam);
    }

    private static String intensity(double value) {
        return value == Math.rint(value) ? Integer.toString((int) value) : String.format("%.1f", value);
    }

    private static MutableComponent text(String key, Object... args) {
        return Component.translatable(LaserAssembly.MOD_ID + "." + key, args);
    }
}
