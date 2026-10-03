package dev.drimoz.laserassembly.client.table;

import dev.drimoz.laserassembly.LaserAssembly;
import dev.drimoz.laserassembly.client.gui.GuiSprites;
import dev.drimoz.laserassembly.client.gui.GuiTheme;
import dev.drimoz.laserassembly.gui.GuiMetrics;
import dev.drimoz.laserassembly.recipe.AssemblyRecipe;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Choosing recipes: a modal window over the <b>whole</b> table screen, as in FactoryIO's crafter -
 * nothing of the table shows through or takes a click while it is open, and the grid gets the room.
 *
 * <pre>
 *  ┌────────────────────────────────────────┐
 *  │ Choose recipes               12 recipes │
 *  │ [ search…                            ] │
 *  │ [r][r][r][r][r][r][r][r]  ▒            │  8 columns, on the inventory's
 *  │ …                          ▒            │  scrollbar in the 9th
 *  │ Click: tick or untick · Esc: close     │
 *  └────────────────────────────────────────┘
 * </pre>
 *
 * A click ticks or unticks and leaves it open: a table runs several recipes in turn, and choosing
 * them one window at a time would be a chore.
 */
final class RecipePicker {

    private static final int COLUMNS = 8;
    private static final int FIELD_Y = 18;
    private static final int FIELD_HEIGHT = 14;
    private static final int GRID_TOP = 36;
    private static final int TRACK_X = GuiMetrics.column(COLUMNS) + 4;
    private static final int TRACK_WIDTH = 10;

    private final Font font;
    private final EditBox search;
    private final List<RecipeHolder<AssemblyRecipe>> recipes;
    private final Supplier<List<ResourceLocation>> ticked;
    private final Consumer<RecipeHolder<AssemblyRecipe>> toggle;
    private final int rows;
    private final int footerY;

    private List<RecipeHolder<AssemblyRecipe>> shown;
    private int scroll;
    private boolean open;

    RecipePicker(Font font, int height, List<RecipeHolder<AssemblyRecipe>> recipes,
                 Supplier<List<ResourceLocation>> ticked, Consumer<RecipeHolder<AssemblyRecipe>> toggle) {
        this.font = font;
        this.recipes = recipes;
        this.shown = recipes;
        this.ticked = ticked;
        this.toggle = toggle;
        this.rows = (height - GRID_TOP - GuiMetrics.SLOT) / GuiMetrics.SLOT;
        this.footerY = GRID_TOP + rows * GuiMetrics.SLOT + 4;

        search = new EditBox(font, 0, 0, GuiMetrics.WIDTH - 2 * GuiMetrics.MARGIN - 6, 10, text("gui.picker.search"));
        search.setBordered(false);
        search.setTextColor(0xFFFFFFFF);
        search.setHint(text("gui.picker.search").withStyle(ChatFormatting.DARK_GRAY));
        search.setMaxLength(50);
        search.setResponder(query -> filter());
        search.visible = false;
    }

    /** The field, which the screen declares as a child so that it receives typing. */
    EditBox search() {
        return search;
    }

    boolean isOpen() {
        return open;
    }

    void open() {
        open = true;
        search.visible = true;
        search.setValue("");
        search.setFocused(true);
        filter();
    }

    void close() {
        open = false;
        search.visible = false;
        search.setFocused(false);
    }

    // Drawing

    void render(GuiGraphics graphics, int left, int top, int height, int mouseX, int mouseY, float partialTick) {
        GuiSprites.panel(graphics, left, top, GuiMetrics.WIDTH, height);
        graphics.drawString(font, text("gui.picker.title"), left + GuiMetrics.MARGIN, top + GuiMetrics.TITLE_Y, GuiTheme.TEXT, false);
        Component count = text("gui.picker.count", shown.size());
        graphics.drawString(font, count, left + GuiMetrics.WIDTH - GuiMetrics.MARGIN - font.width(count), top + GuiMetrics.TITLE_Y,
                GuiTheme.TEXT_MUTED, false);

        GuiSprites.field(graphics, left + GuiMetrics.MARGIN - 1, top + FIELD_Y, GuiMetrics.WIDTH - 2 * GuiMetrics.MARGIN + 2, FIELD_HEIGHT);
        search.setX(left + GuiMetrics.MARGIN + 3);
        search.setY(top + FIELD_Y + 3);
        search.render(graphics, mouseX, mouseY, partialTick);

        List<ResourceLocation> chosen = ticked.get();
        RecipeHolder<AssemblyRecipe> hovered = recipeAt(left, top, mouseX, mouseY);
        for (int cell = 0; cell < COLUMNS * rows; cell++) {
            int index = scroll * COLUMNS + cell;
            int x = left + GuiMetrics.column(cell % COLUMNS), y = top + GRID_TOP + (cell / COLUMNS) * GuiMetrics.SLOT;
            // Drawn in full even when empty: an empty grid still reads as a grid.
            RecipeHolder<AssemblyRecipe> recipe = index < shown.size() ? shown.get(index) : null;
            GuiSprites.slot(graphics, x, y, recipe != null && chosen.contains(recipe.id()));
            if (recipe == null) continue;
            graphics.renderItem(result(recipe), x + 1, y + 1);
            if (recipe == hovered) AssemblyTableScreen.wash(graphics, x, y, GuiTheme.HOVER_WASH);
        }

        if (shown.isEmpty()) {
            Component none = text("gui.picker.none");
            graphics.drawString(font, none, left + (GuiMetrics.WIDTH - font.width(none)) / 2, top + GRID_TOP + 30, GuiTheme.TEXT_MUTED, false);
        }
        renderScrollbar(graphics, left, top);
        graphics.drawString(font, text("gui.picker.help"), left + GuiMetrics.MARGIN, top + footerY, GuiTheme.TEXT_MUTED, false);

        // Last, over everything: nothing may draw in front of a tooltip.
        if (hovered != null) {
            graphics.renderComponentTooltip(font, AssemblyTableScreen.describe(hovered, chosen.contains(hovered.id())), mouseX, mouseY);
        }
    }

    private void renderScrollbar(GuiGraphics graphics, int left, int top) {
        int trackHeight = rows * GuiMetrics.SLOT;
        GuiSprites.inset(graphics, left + TRACK_X, top + GRID_TOP, TRACK_WIDTH, trackHeight);
        // Nothing to scroll: the track stays, empty - a full-height thumb would invite a drag.
        if (maxScroll() == 0) return;
        int totalRows = Mth.positiveCeilDiv(shown.size(), COLUMNS);
        int thumb = Math.max(12, (trackHeight - 2) * rows / totalRows);
        int thumbY = (trackHeight - 2 - thumb) * scroll / maxScroll();
        GuiSprites.button(graphics, left + TRACK_X + 1, top + GRID_TOP + 1 + thumbY, TRACK_WIDTH - 2, thumb, false);
    }

    // Input

    /** Always true while open: the modal takes every click. */
    boolean mouseClicked(int left, int top, double mouseX, double mouseY, int button) {
        if (search.mouseClicked(mouseX, mouseY, button)) return true;
        RecipeHolder<AssemblyRecipe> clicked = recipeAt(left, top, mouseX, mouseY);
        if (clicked != null && button == GLFW.GLFW_MOUSE_BUTTON_LEFT) toggle.accept(clicked);
        return true;
    }

    void mouseScrolled(double delta) {
        scroll = Mth.clamp(scroll - (int) Math.signum(delta), 0, maxScroll());
    }

    /** Escape closes; every other key goes to the search - "E" is typed rather than closing the screen. */
    boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            close();
            return true;
        }
        search.keyPressed(keyCode, scanCode, modifiers);
        return true;
    }

    boolean charTyped(char codePoint, int modifiers) {
        return search.charTyped(codePoint, modifiers);
    }

    // Inner work

    private void filter() {
        String query = search.getValue().trim().toLowerCase(Locale.ROOT);
        if (query.isEmpty()) {
            shown = recipes;
        } else {
            List<RecipeHolder<AssemblyRecipe>> matching = new ArrayList<>();
            for (RecipeHolder<AssemblyRecipe> recipe : recipes) {
                String name = result(recipe).getHoverName().getString().toLowerCase(Locale.ROOT);
                if (name.contains(query) || recipe.id().toString().contains(query)) matching.add(recipe);
            }
            shown = matching;
        }
        scroll = 0;
    }

    private int maxScroll() {
        return Math.max(0, Mth.positiveCeilDiv(shown.size(), COLUMNS) - rows);
    }

    private @Nullable RecipeHolder<AssemblyRecipe> recipeAt(int left, int top, double mouseX, double mouseY) {
        double gridX = mouseX - left - GuiMetrics.column(0), gridY = mouseY - top - GRID_TOP;
        if (gridX < 0 || gridY < 0 || gridX >= COLUMNS * GuiMetrics.SLOT || gridY >= rows * GuiMetrics.SLOT) return null;
        int index = (scroll + (int) gridY / GuiMetrics.SLOT) * COLUMNS + (int) gridX / GuiMetrics.SLOT;
        return index < shown.size() ? shown.get(index) : null;
    }

    private static ItemStack result(RecipeHolder<AssemblyRecipe> recipe) {
        return recipe.value().getResultItem(Minecraft.getInstance().level.registryAccess());
    }

    private static net.minecraft.network.chat.MutableComponent text(String key, Object... args) {
        return Component.translatable(LaserAssembly.MOD_ID + "." + key, args);
    }
}
