package dev.drimoz.laserassembly.client.table;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.drimoz.laserassembly.LaserAssembly;
import dev.drimoz.laserassembly.content.table.AssemblyTableBlock;
import dev.drimoz.laserassembly.content.table.AssemblyTableBlockEntity;
import dev.drimoz.laserassembly.recipe.AssemblyRecipe;
import dev.drimoz.laserassembly.registry.ModRecipes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.model.standalone.StandaloneModelKey;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * What the table makes, over its focus: the result alone, turning and bobbing in the beams while
 * the table works, still at rest. The ingredients in a ring read as clutter, and did not say which
 * item was being made. Driven by the game clock, not by the craft's progress - that would need a
 * packet every tick, and the screen already shows the true figure.
 */
public class AssemblyTableRenderer implements BlockEntityRenderer<AssemblyTableBlockEntity, AssemblyTableRenderer.State> {

    public static final StandaloneModelKey<BlockStateModelPart> GLOW = new StandaloneModelKey<>(() -> LaserAssembly.MOD_ID + ":assembly_table_glow");

    /** Just above the plate, where the beams meet. */
    private static final float HEIGHT = (float) AssemblyTableBlock.TOP + 0.3F;
    private static final float SCALE = 0.55F;

    public static final class State extends BlockEntityRenderState {
        final ItemStackRenderState item = new ItemStackRenderState();
        boolean working;
        float time;
        /** Lit by the air over the plate, not the table block itself, which is dark inside. */
        int itemLight;
    }

    private final ItemModelResolver items;

    public AssemblyTableRenderer(BlockEntityRendererProvider.Context context) {
        this.items = context.itemModelResolver();
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(AssemblyTableBlockEntity table, State state, float partialTick, Vec3 camera, ModelFeatureRenderer.@Nullable CrumblingOverlay breaking) {
        BlockEntityRenderer.super.extractRenderState(table, state, partialTick, camera, breaking);
        state.working = table.working();
        state.item.clear();
        Identifier current = table.current();
        Level level = table.getLevel();
        if (current == null || level == null) return;
        AssemblyRecipe recipe = ModRecipes.byId(level, current);
        if (recipe == null) return;
        state.time = level.getGameTime() + partialTick;
        state.itemLight = LevelRenderer.getLightCoords(level, table.getBlockPos().above());
        items.updateForTopItem(state.item, recipe.result(), ItemDisplayContext.GROUND, level, null, 0);
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        if (state.working) {
            // The focus lit at full brightness, so a working table reads from across a dark room.
            collector.submitBlockModel(poseStack, Sheets.cutoutBlockSheet(),
                    List.of(Minecraft.getInstance().getModelManager().getStandaloneModel(GLOW)), new int[0],
                    LightCoordsUtil.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 0);
        }
        if (state.item.isEmpty()) return;
        poseStack.pushPose();
        poseStack.translate(0.5, HEIGHT + (state.working ? Mth.sin(state.time * 0.1F) * 0.04F : 0), 0.5);
        poseStack.mulPose(Axis.YP.rotationDegrees(state.working ? state.time * 2 : 0));
        poseStack.scale(SCALE, SCALE, SCALE);
        state.item.submit(poseStack, collector, state.itemLight, OverlayTexture.NO_OVERLAY, 0);
        poseStack.popPose();
    }

    /** The result floats above the block: culled to the block alone, it would vanish at the plate's edge. */
    @Override
    public AABB getRenderBoundingBox(AssemblyTableBlockEntity table) {
        return new AABB(table.getBlockPos()).expandTowards(0, 1, 0);
    }
}
