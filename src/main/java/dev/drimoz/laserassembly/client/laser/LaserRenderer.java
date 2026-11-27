package dev.drimoz.laserassembly.client.laser;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.drimoz.laserassembly.LaserAssembly;
import dev.drimoz.laserassembly.client.BeamColors;
import dev.drimoz.laserassembly.content.laser.LaserBlock;
import dev.drimoz.laserassembly.content.laser.LaserBlockEntity;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.model.standalone.StandaloneModelKey;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.List;

/**
 * The head, turned towards the table it is linked to and lit while it fires, then the beam from its
 * nozzle to the middle of the table's top. The head eases there rather than snapping: the turn is
 * the feedback that a laser has found its table.
 */
public class LaserRenderer implements BlockEntityRenderer<LaserBlockEntity, LaserRenderer.State> {

    public static final StandaloneModelKey<BlockStateModelPart> HEAD = new StandaloneModelKey<>(() -> LaserAssembly.MOD_ID + ":laser_head");
    public static final StandaloneModelKey<BlockStateModelPart> GLOW = new StandaloneModelKey<>(() -> LaserAssembly.MOD_ID + ":laser_glow");

    /** The head turns about this point, on the laser's axis, 10 pixels up from its back. */
    private static final float PIVOT = 10 / 16F;
    /** From the pivot to the nozzle's tip, where the beam leaves. */
    private static final float NOZZLE = 5 / 16F;
    /** How far the head leans off its axis: further, its corners leave the block. */
    private static final float MAX_TILT = (float) Math.toRadians(50);
    private static final Vector3f UP = new Vector3f(0, 1, 0);
    private static final int[] NO_TINT = {};

    /** What one frame draws, worked out from the laser before anything is submitted. */
    public static final class State extends BlockEntityRenderState {
        final Quaternionf base = new Quaternionf();
        final Quaternionf tilt = new Quaternionf();
        float pivotX, pivotY, pivotZ;
        boolean firing;
        int rgb;
        float pulse;
        @Nullable Vec3 from, to;
    }

    // Scratch, reused every frame: the renderer runs on the render thread only.
    private final Vector3f desired = new Vector3f();
    private final Vector3f tip = new Vector3f();

    public LaserRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(LaserBlockEntity laser, State state, float partialTick, Vec3 camera, ModelFeatureRenderer.@Nullable CrumblingOverlay breaking) {
        BlockEntityRenderer.super.extractRenderState(laser, state, partialTick, camera, breaking);
        Direction facing = laser.getBlockState().getValue(LaserBlock.FACING);
        BlockPos target = laser.target();
        state.base.set(facing.getRotation());
        // The pivot, relative to the block's corner.
        float offset = PIVOT - 0.5F;
        state.pivotX = 0.5F + facing.getStepX() * offset;
        state.pivotY = 0.5F + facing.getStepY() * offset;
        state.pivotZ = 0.5F + facing.getStepZ() * offset;

        // Where the head should point, in its own frame - built pointing up.
        desired.set(0, 1, 0);
        if (target != null) {
            BlockPos pos = laser.getBlockPos();
            Vec3 end = LaserBlockEntity.beamEnd(target);
            desired.set((float) (end.x - pos.getX() - state.pivotX), (float) (end.y - pos.getY() - state.pivotY), (float) (end.z - pos.getZ() - state.pivotZ));
            state.base.transformInverse(desired).normalize();
            float cos = Mth.cos(MAX_TILT);
            if (desired.y < cos) {
                float horizontal = Mth.sqrt(desired.x * desired.x + desired.z * desired.z);
                if (horizontal > 1e-4F) {
                    float scale = Mth.sin(MAX_TILT) / horizontal;
                    desired.set(desired.x * scale, cos, desired.z * scale);
                }
            }
        }

        float now = laser.getLevel().getGameTime() + partialTick;
        Vector3f aim = laser.headAim;
        if (laser.headAimTime == 0) {
            aim.set(desired);
        } else {
            // About a second to settle, whatever the frame rate.
            float elapsed = Mth.clamp(now - laser.headAimTime, 0, 20);
            aim.lerp(desired, 1 - (float) Math.pow(0.85, elapsed)).normalize();
        }
        laser.headAimTime = now;
        state.tilt.rotationTo(UP, aim);

        state.firing = laser.firing() && target != null;
        state.rgb = BeamColors.rgb(laser.beam());
        state.pulse = 0.85F + 0.15F * Mth.sin(now * 0.6F);
        if (state.firing) {
            state.tilt.transform(tip.set(0, NOZZLE, 0));
            state.base.transform(tip);
            state.from = new Vec3(state.pivotX + tip.x, state.pivotY + tip.y, state.pivotZ + tip.z);
            state.to = LaserBlockEntity.beamEnd(target).subtract(Vec3.atLowerCornerOf(laser.getBlockPos()));
        }
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        var models = Minecraft.getInstance().getModelManager();
        poseStack.pushPose();
        poseStack.translate(state.pivotX, state.pivotY, state.pivotZ);
        poseStack.mulPose(state.base);
        poseStack.mulPose(state.tilt);
        poseStack.translate(-0.5F, -PIVOT, -0.5F);
        collector.submitBlockModel(poseStack, Sheets.cutoutBlockSheet(), List.of(models.getStandaloneModel(HEAD)), NO_TINT,
                state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
        int r = state.rgb >> 16 & 0xFF, g = state.rgb >> 8 & 0xFF, b = state.rgb & 0xFF;
        if (state.firing) {
            // The lens whitened, through the glow model's tint index: the core of the beam's colour.
            int whitened = 0xFF000000 | (r + 255) / 2 << 16 | (g + 255) / 2 << 8 | (b + 255) / 2;
            collector.submitBlockModel(poseStack, Sheets.cutoutBlockSheet(), List.of(models.getStandaloneModel(GLOW)), new int[]{whitened},
                    LightCoordsUtil.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 0);
        }
        poseStack.popPose();
        if (!state.firing || state.from == null || state.to == null) return;

        // Additive and untextured, the lightning bolt's render type: a bright core inside a faint glow.
        Vec3 from = state.from, to = state.to;
        float pulse = state.pulse;
        collector.submitCustomGeometry(poseStack, RenderTypes.lightning(), (pose, consumer) -> {
            beam(consumer, pose, from, to, 0.035F * pulse, (r + 255) / 2, (g + 255) / 2, (b + 255) / 2, 230);
            beam(consumer, pose, from, to, 0.09F * pulse, r, g, b, 90);
        });
    }

    /** Two crossed quads along the beam, each drawn both ways: the render type culls back faces. */
    private static void beam(VertexConsumer consumer, PoseStack.Pose pose, Vec3 from, Vec3 to, float halfWidth, int r, int g, int b, int a) {
        Vec3 direction = to.subtract(from).normalize();
        Vec3 up = Math.abs(direction.y) < 0.99 ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0);
        Vec3 side = direction.cross(up).normalize().scale(halfWidth);
        Vec3 other = direction.cross(side).normalize().scale(halfWidth);
        for (Vec3 offset : new Vec3[]{side, other}) {
            Vec3 a1 = from.add(offset), a2 = from.subtract(offset), b1 = to.subtract(offset), b2 = to.add(offset);
            quad(consumer, pose, a1, a2, b1, b2, r, g, b, a);
            quad(consumer, pose, b2, b1, a2, a1, r, g, b, a);
        }
    }

    private static void quad(VertexConsumer consumer, PoseStack.Pose pose, Vec3 p1, Vec3 p2, Vec3 p3, Vec3 p4, int r, int g, int b, int a) {
        for (Vec3 p : new Vec3[]{p1, p2, p3, p4}) {
            consumer.addVertex(pose, (float) p.x, (float) p.y, (float) p.z).setColor(r, g, b, a);
        }
    }

    /** The beam leaves the laser's block: culled to that block alone, it would vanish when the laser does. */
    @Override
    public AABB getRenderBoundingBox(LaserBlockEntity laser) {
        BlockPos target = laser.target();
        AABB own = new AABB(laser.getBlockPos());
        return target == null ? own : own.minmax(new AABB(target)).inflate(1);
    }
}
