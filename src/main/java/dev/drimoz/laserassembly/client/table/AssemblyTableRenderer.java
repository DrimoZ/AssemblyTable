package dev.drimoz.laserassembly.client.table;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.drimoz.laserassembly.LaserAssembly;
import dev.drimoz.laserassembly.content.table.AssemblyTableBlockEntity;
import dev.drimoz.laserassembly.recipe.AssemblyRecipe;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.client.model.data.ModelData;

/**
 * What the table makes, over its focus: the result alone, turning and bobbing in the beams while
 * the table works, still at rest. The ingredients in a ring read as clutter, and did not say which
 * item was being made. Driven by the game clock, not by the craft's progress - that would need a
 * packet every tick, and the screen already shows the true figure.
 */
public class AssemblyTableRenderer implements BlockEntityRenderer<AssemblyTableBlockEntity> {

    public static final ModelResourceLocation GLOW = ModelResourceLocation.standalone(LaserAssembly.id("block/assembly_table_glow"));

    /** Just above the plate, where the beams meet. */
    private static final float HEIGHT = 1.3F;
    private static final float SCALE = 0.55F;

    private final ItemRenderer items;
    private final ModelBlockRenderer models;

    public AssemblyTableRenderer(BlockEntityRendererProvider.Context context) {
        this.items = context.getItemRenderer();
        this.models = context.getBlockRenderDispatcher().getModelRenderer();
    }

    @Override
    public void render(AssemblyTableBlockEntity table, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light, int overlay) {
        boolean working = table.working();
        if (working) {
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
        poseStack.pushPose();
        poseStack.translate(0.5, HEIGHT + (working ? Mth.sin(time * 0.1F) * 0.04F : 0), 0.5);
        poseStack.mulPose(Axis.YP.rotationDegrees(working ? time * 2 : 0));
        poseStack.scale(SCALE, SCALE, SCALE);
        items.renderStatic(recipe.getResultItem(level.registryAccess()), ItemDisplayContext.GROUND,
                LevelRenderer.getLightColor(level, table.getBlockPos().above()), overlay, poseStack, buffers, level, 0);
        poseStack.popPose();
    }

    /** The result floats above the block: culled to the block alone, it would vanish at the plate's edge. */
    @Override
    public AABB getRenderBoundingBox(AssemblyTableBlockEntity table) {
        return new AABB(table.getBlockPos()).expandTowards(0, 1, 0);
    }
}
