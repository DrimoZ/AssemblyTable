package dev.drimoz.laserassembly.client.table;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.drimoz.laserassembly.content.table.AssemblyTableBlockEntity;
import dev.drimoz.laserassembly.recipe.AssemblyRecipe;
import net.minecraft.client.Minecraft;
import dev.drimoz.laserassembly.LaserAssembly;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.crafting.SizedIngredient;

/**
 * The assembly on display: over the plate, the running recipe's ingredients in a ring. While the
 * table works they turn and close in on the centre, where the result grows, and start over; at rest
 * they hang still. Driven by the game clock, not by the craft's real progress - that would need a
 * packet every tick, and the screen already shows the true figure.
 */
public class AssemblyTableRenderer implements BlockEntityRenderer<AssemblyTableBlockEntity> {

    /** Ticks for the ring to close once. */
    private static final float CYCLE = 60;
    private static final float RADIUS = 0.32F;
    private static final float HEIGHT = 1.35F;

    public static final ModelResourceLocation GLOW = ModelResourceLocation.standalone(LaserAssembly.id("block/assembly_table_glow"));

    private final ItemRenderer items;
    private final ModelBlockRenderer models;

    public AssemblyTableRenderer(BlockEntityRendererProvider.Context context) {
        this.items = context.getItemRenderer();
        this.models = context.getBlockRenderDispatcher().getModelRenderer();
    }

    @Override
    public void render(AssemblyTableBlockEntity table, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light, int overlay) {
        if (table.working()) {
            // The focus lit at full brightness, so a working table reads from across a dark room.
            models.renderModel(poseStack.last(), buffers.getBuffer(Sheets.cutoutBlockSheet()), null,
                    Minecraft.getInstance().getModelManager().getModel(GLOW), 1, 1, 1, LightTexture.FULL_BRIGHT, overlay,
                    ModelData.EMPTY, RenderType.cutout());
        }
        ResourceLocation current = table.current();
        Level level = table.getLevel();
        if (current == null || level == null) return;
        AssemblyRecipe recipe = level.getRecipeManager().byKey(current)
                .map(holder -> holder.value() instanceof AssemblyRecipe assembly ? assembly : null).orElse(null);
        if (recipe == null) return;

        float time = level.getGameTime() + partialTick;
        // 0 at the start of a cycle, 1 as the ingredients meet; still at the outer ring when idle.
        float closing = table.working() ? (time % CYCLE) / CYCLE : 0;
        float radius = RADIUS * (1 - closing * closing);
        float spin = table.working() ? time * 3 : 0;
        int lit = LevelRenderer.getLightColor(level, table.getBlockPos().above());

        int count = recipe.ingredients().size();
        for (int i = 0; i < count; i++) {
            SizedIngredient sized = recipe.ingredients().get(i);
            ItemStack[] options = sized.ingredient().getItems();
            if (options.length == 0) continue;
            float angle = (360F / count) * i + spin;
            float bob = Mth.sin((time + i * 7) * 0.1F) * 0.03F;
            poseStack.pushPose();
            poseStack.translate(0.5, HEIGHT + bob, 0.5);
            poseStack.mulPose(Axis.YP.rotationDegrees(angle));
            poseStack.translate(radius, 0, 0);
            poseStack.scale(0.35F, 0.35F, 0.35F);
            items.renderStatic(options[0], ItemDisplayContext.GROUND, lit, overlay, poseStack, buffers, level, i);
            poseStack.popPose();
        }

        if (closing > 0) {
            float size = 0.45F * closing;
            poseStack.pushPose();
            poseStack.translate(0.5, HEIGHT, 0.5);
            poseStack.mulPose(Axis.YP.rotationDegrees(time * 4));
            poseStack.scale(size, size, size);
            items.renderStatic(recipe.getResultItem(level.registryAccess()), ItemDisplayContext.GROUND, lit, overlay, poseStack, buffers, level, count);
            poseStack.popPose();
        }
    }

    /** The assembly floats above the block: culled to the block alone, it would vanish at the plate's edge. */
    @Override
    public AABB getRenderBoundingBox(AssemblyTableBlockEntity table) {
        return new AABB(table.getBlockPos()).expandTowards(0, 1, 0);
    }
}
