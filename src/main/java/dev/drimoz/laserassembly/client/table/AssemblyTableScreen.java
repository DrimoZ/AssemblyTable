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
 * Built around the recipe in progress, the subject of the screen as on FactoryIO's machines:
 * <pre>
 *  ┌────────────────────────────────────────┐
 *  │ Assembly Table                       ● │
 *  │ ┏━━┓ Trident                            │  the socket: the running recipe, a click opens the picker
 *  │ ┗━━┛ Step 2 of 2: Welding               │
 *  │ [▬▬▬▬▬▬▬▬|▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬]     │  the steps: a thin strip, a segment per step in its beam's colour
 *  │ ▌ [ ][ ][ ][ ]           [o][o]        │  heat, inputs 4x3, an arrow, outputs 2x2
 *  │ ▌ [ ][ ][ ][ ]   ━━▶     [o][o]        │  the arrow fills with the recipe's progress
 *  │ ▌ [ ][ ][ ][ ]                         │
 *  └────────────────────────────────────────┘
 * </pre>
 * One drawing of progress, the arrow, filling green; the strip only says which
 * steps there are and which are done. Two things filling at once read as two progresses.
 * The other ticked recipes are a count on the socket and a line in its tooltip, not a row of cells:
 * as cells they read as more outputs.
 */
public class AssemblyTableScreen extends AbstractContainerScreen<AssemblyTableMenu> {

    private static final int SOCKET_X = GuiMetrics.column(0);
    private static final int SOCKET_Y = GuiMetrics.CONTENT_TOP;
    private static final int NAME_X = SOCKET_X + GuiMetrics.SOCKET + 4;
    private static final int NAME_WIDTH = GuiMetrics.column(9) - NAME_X;

    private static final int BAR_X = GuiMetrics.column(0);
    private static final int BAR_Y = SOCKET_Y + GuiMetrics.SOCKET + 3;
    private static final int BAR_WIDTH = GuiMetrics.column(9) - BAR_X;
    private static final int BAR_HEIGHT = 5;

    /** Heat is one number to keep an eye on: a thin gauge, centred in column 0. */
    private static final int HEAT_WIDTH = 6;
    private static final int HEAT_X = GuiMetrics.column(0) + (GuiMetrics.SLOT - HEAT_WIDTH) / 2;
    private static final int HEAT_HEIGHT = CONTENT_BOTTOM - WORK_TOP;
    private static final int ARROW_X = GuiMetrics.column(5) + (2 * GuiMetrics.SLOT - 20) / 2;
    private static final int ARROW_Y = WORK_TOP + (3 * GuiMetrics.SLOT - 13) / 2;

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
            // Not renderBackground: on a container screen it also runs renderBg, and the table's
            // cells, lifted above their items, showed through the picker.
            renderTransparentBackground(graphics);
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
        GuiSprites.gauge(graphics, GuiSprites.GAUGE_HEAT, x + HEAT_X, y + WORK_TOP, HEAT_WIDTH, HEAT_HEIGHT,
                menu.data(DATA_HEAT) / 1000f);

        renderSocket(graphics, mouseX, mouseY);
        renderStepBar(graphics);
        renderArrow(graphics, x + ARROW_X, y + ARROW_Y);
        GuiSprites.statusLight(graphics, status().light, x + GuiMetrics.LED_X, y + GuiMetrics.LED_Y);
    }

    /** The running recipe; failing that the first ticked one, waiting; failing that a + to tick some. */
    private void renderSocket(GuiGraphics graphics, int mouseX, int mouseY) {
        int x = leftPos + SOCKET_X, y = topPos + SOCKET_Y;
        GuiSprites.socket(graphics, x, y);
        RecipeHolder<AssemblyRecipe> shown = shownRecipe();
        if (shown == null) {
            GuiSprites.plus(graphics, x + 4, y + 4);
        } else {
            graphics.renderFakeItem(result(shown), x + 5, y + 5);
            if (!shown.id().equals(menu.table().current())) wash(graphics, x + 4, y + 4, GuiTheme.GHOST_WASH);
        }
        if (overSocket(mouseX, mouseY)) wash(graphics, x + 4, y + 4, GuiTheme.HOVER_WASH);

        // How many other recipes wait their turn, in the corner like a stack's count.
        int waiting = queue().size();
        if (waiting > 0) {
            String count = "+" + waiting;
            graphics.pose().pushPose();
            graphics.pose().translate(0, 0, 300);
            graphics.drawString(font, count, x + GuiMetrics.SOCKET - 2 - font.width(count), y + GuiMetrics.SOCKET - 10, 0xFFFFFFFF, true);
            graphics.pose().popPose();
        }
    }

    /**
     * The recipe's steps as a thin strip: a segment per step, as long as its share of the energy, in
     * its beam's colour - bright once reached, dim while still ahead. It says which beams the recipe
     * wants and in what order; how far along it is, the arrow says.
     */
    private void renderStepBar(GuiGraphics graphics) {
        int x = leftPos + BAR_X, y = topPos + BAR_Y;
        GuiSprites.inset(graphics, x, y, BAR_WIDTH, BAR_HEIGHT);
        List<Step> steps = runningSteps();
        int current = menu.data(DATA_STEP);
        int[] edges = segmentEdges(steps);
        for (int i = 0; i < steps.size(); i++) {
            int rgb = BeamColors.rgb(steps.get(i).beam().orElse(null));
            int left = x + 1 + edges[i], right = x + 1 + edges[i + 1];
            graphics.fill(left, y + 1, right, y + BAR_HEIGHT - 1, 0xFF000000 | (i <= current ? rgb : dim(rgb)));
            if (i > 0) graphics.fill(left, y + 1, left + 1, y + BAR_HEIGHT - 1, 0xFF373737);
        }
    }

    /** The recipe's progress across all its steps, in one green: what the step is, the strip and the text say. */
    private void renderArrow(GuiGraphics graphics, int x, int y) {
        List<Step> steps = runningSteps();
        if (steps.isEmpty()) {
            GuiSprites.arrow(graphics, x, y, 0);
            return;
        }
        int current = Math.min(menu.data(DATA_STEP), steps.size() - 1);
        long total = 0, done = 0;
        for (int i = 0; i < steps.size(); i++) {
            total += steps.get(i).energy();
            if (i < current) done += steps.get(i).energy();
        }
        float fill = (done + steps.get(current).energy() * menu.data(DATA_PROGRESS) / 1000f) / total;
        GuiSprites.arrow(graphics, x, y, fill);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, titleLabelX, titleLabelY, GuiTheme.TEXT, false);
        graphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, GuiTheme.TEXT, false);

        RecipeHolder<AssemblyRecipe> shown = shownRecipe();
        Component name = shown == null ? text("gui.add") : result(shown).getHoverName();
        graphics.drawString(font, font.plainSubstrByWidth(name.getString(), NAME_WIDTH), NAME_X, SOCKET_Y + 4, GuiTheme.TEXT, false);

        // Under the name: the step in words while one runs, otherwise what the table is waiting for.
        List<Step> steps = runningSteps();
        Component line = steps.isEmpty() ? status().text
                : text("gui.step", menu.data(DATA_STEP) + 1, steps.size(), beamName(steps.get(Math.min(menu.data(DATA_STEP), steps.size() - 1)).beam().orElse(null)));
        graphics.drawString(font, font.plainSubstrByWidth(line.getString(), NAME_WIDTH), NAME_X, SOCKET_Y + 15, GuiTheme.TEXT_MUTED, false);
    }

    // Tooltips

    private List<Component> tooltipAt(int mouseX, int mouseY) {
        int x = mouseX - leftPos, y = mouseY - topPos;
        if (overSocket(mouseX, mouseY)) {
            RecipeHolder<AssemblyRecipe> shown = shownRecipe();
            List<Component> lines = new ArrayList<>(shown == null ? List.of() : describe(shown, null));
            List<ResourceLocation> queue = queue();
            if (!queue.isEmpty()) {
                List<String> names = new ArrayList<>();
                for (ResourceLocation id : queue) names.add(byId.containsKey(id) ? result(byId.get(id)).getHoverName().getString() : id.toString());
                lines.add(text("gui.next", String.join(", ", names)).withStyle(ChatFormatting.YELLOW));
            }
            lines.add(text("gui.choose").withStyle(ChatFormatting.DARK_GRAY));
            return lines;
        }
        List<Step> steps = runningSteps();
        if (y >= BAR_Y && y < BAR_Y + BAR_HEIGHT && x >= BAR_X && x < BAR_X + BAR_WIDTH && !steps.isEmpty()) {
            int[] edges = segmentEdges(steps);
            for (int i = 0; i < steps.size(); i++) {
                if (x - BAR_X - 1 < edges[i + 1] || i == steps.size() - 1) return stepTooltip(steps.get(i), i, steps.size());
            }
        }
        if (x >= HEAT_X && x < HEAT_X + HEAT_WIDTH && y >= WORK_TOP && y < CONTENT_BOTTOM) {
            return List.of(text("gui.heat", menu.data(DATA_HEAT) / 10));
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
        int current = menu.data(DATA_STEP);
        if (index == current) lines.add(text("gui.step.progress", menu.data(DATA_PROGRESS) / 10).withStyle(ChatFormatting.GREEN));
        else if (index < current) lines.add(text("gui.step.done").withStyle(ChatFormatting.GREEN));
        return lines;
    }

    /**
     * What a recipe makes, takes and goes through. Shared with the picker. {@code ticked} adds what a
     * click would do; null when a click does something else.
     */
    static List<Component> describe(RecipeHolder<AssemblyRecipe> holder, @Nullable Boolean ticked) {
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
        if (ticked != null) lines.add(text(ticked ? "gui.recipe.untick" : "gui.recipe.tick").withStyle(ChatFormatting.DARK_GRAY));
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
        if (overSocket(mouseX, mouseY)) {
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

    private @Nullable RecipeHolder<AssemblyRecipe> shownRecipe() {
        ResourceLocation current = menu.table().current();
        if (current != null && byId.containsKey(current)) return byId.get(current);
        List<ResourceLocation> ticked = menu.table().selected();
        return ticked.isEmpty() ? null : byId.get(ticked.getFirst());
    }

    /** Ticked recipes after the running one, wrapping round: the order they take their turn. */
    private List<ResourceLocation> queue() {
        List<ResourceLocation> ticked = menu.table().selected();
        ResourceLocation shown = shownRecipe() == null ? null : shownRecipe().id();
        int start = ticked.indexOf(shown) + 1;
        List<ResourceLocation> queue = new ArrayList<>(ticked.size());
        for (int i = 0; i < ticked.size(); i++) {
            ResourceLocation id = ticked.get((start + i) % ticked.size());
            if (!id.equals(shown)) queue.add(id);
        }
        return queue;
    }

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

    /** Segment boundaries inside the bar, each step as long as its share of the recipe's energy. */
    private static int[] segmentEdges(List<Step> steps) {
        int[] edges = new int[steps.size() + 1];
        long total = 0;
        for (Step step : steps) total += step.energy();
        long sum = 0;
        for (int i = 0; i < steps.size(); i++) {
            sum += steps.get(i).energy();
            edges[i + 1] = (int) ((BAR_WIDTH - 2) * sum / total);
        }
        return edges;
    }

    /** A beam's colour, dimmed: the part of a step still to do. */
    private static int dim(int rgb) {
        return ((rgb >> 16 & 0xFF) * 2 / 5) << 16 | ((rgb >> 8 & 0xFF) * 2 / 5) << 8 | (rgb & 0xFF) * 2 / 5;
    }

    private boolean overSocket(double mouseX, double mouseY) {
        double x = mouseX - leftPos - SOCKET_X, y = mouseY - topPos - SOCKET_Y;
        return x >= 0 && x < GuiMetrics.SOCKET && y >= 0 && y < GuiMetrics.SOCKET;
    }

    /** Lifted above the item it covers, or it lands underneath. */
    static void wash(GuiGraphics graphics, int frameX, int frameY, int argb) {
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 200);
        graphics.fill(frameX + 1, frameY + 1, frameX + 17, frameY + 17, argb);
        graphics.pose().popPose();
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
