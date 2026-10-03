package dev.drimoz.laserassembly.client.laser;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.drimoz.laserassembly.LaserAssembly;
import dev.drimoz.laserassembly.client.BeamColors;
import dev.drimoz.laserassembly.content.laser.LaserBlock;
import dev.drimoz.laserassembly.content.laser.LaserBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * The head, turned towards the table it is linked to and lit while it fires, then the beam from its
 * nozzle to the middle of the table's top. The head eases there rather than snapping: the turn is
 * the feedback that a laser has found its table.
 */
public class LaserRenderer implements BlockEntityRenderer<LaserBlockEntity> {

    public static final ModelResourceLocation HEAD = ModelResourceLocation.standalone(LaserAssembly.id("block/laser_head"));
    public static final ModelResourceLocation GLOW = ModelResourceLocation.standalone(LaserAssembly.id("block/laser_glow"));

    /** The head turns about this point, on the laser's axis, 10 pixels up from its back. */
    private static final float PIVOT = 10 / 16F;
    /** From the pivot to the nozzle's tip, where the beam leaves. */
    private static final float NOZZLE = 5 / 16F;
    /** How far the head leans off its axis: further, its corners leave the block. */
    private static final float MAX_TILT = (float) Math.toRadians(50);
    private static final Vector3f UP = new Vector3f(0, 1, 0);

    private final ModelBlockRenderer models;
    // Scratch, reused every frame: the renderer runs on the render thread only.
    private final Quaternionf base = new Quaternionf();
    private final Quaternionf tilt = new Quaternionf();
    private final Vector3f desired = new Vector3f();
    private final Vector3f tip = new Vector3f();

    public LaserRenderer(BlockEntityRendererProvider.Context context) {
        this.models = context.getBlockRenderDispatcher().getModelRenderer();
    }

    @Override
    public void render(LaserBlockEntity laser, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light, int overlay) {
        Direction facing = laser.getBlockState().getValue(LaserBlock.FACING);
        BlockPos target = laser.target();
        base.set(facing.getRotation());
        // The pivot, relative to the block's corner.
        float offset = PIVOT - 0.5F;
        float pivotX = 0.5F + facing.getStepX() * offset, pivotY = 0.5F + facing.getStepY() * offset, pivotZ = 0.5F + facing.getStepZ() * offset;

        // Where the head should point, in its own frame - built pointing up.
        desired.set(0, 1, 0);
        if (target != null) {
            BlockPos pos = laser.getBlockPos();
            Vec3 end = LaserBlockEntity.beamEnd(target);
            desired.set((float) (end.x - pos.getX() - pivotX), (float) (end.y - pos.getY() - pivotY), (float) (end.z - pos.getZ() - pivotZ));
            base.transformInverse(desired).normalize();
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
        tilt.rotationTo(UP, aim);

        poseStack.pushPose();
        poseStack.translate(pivotX, pivotY, pivotZ);
        poseStack.mulPose(base);
        poseStack.mulPose(tilt);
        poseStack.translate(-0.5F, -PIVOT, -0.5F);
        Minecraft minecraft = Minecraft.getInstance();
        models.renderModel(poseStack.last(), buffers.getBuffer(Sheets.solidBlockSheet()), null,
                minecraft.getModelManager().getModel(HEAD), 1, 1, 1, light, overlay, ModelData.EMPTY, RenderType.solid());
        boolean firing = laser.firing() && target != null;
        int rgb = BeamColors.rgb(laser.beam());
        // The core whitened towards its centre, the glow in the lens's own colour.
        int r = rgb >> 16 & 0xFF, g = rgb >> 8 & 0xFF, b = rgb & 0xFF;
        if (firing) {
            models.renderModel(poseStack.last(), buffers.getBuffer(Sheets.solidBlockSheet()), null,
                    minecraft.getModelManager().getModel(GLOW), (r + 255) / 510F, (g + 255) / 510F, (b + 255) / 510F,
                    LightTexture.FULL_BRIGHT, overlay, ModelData.EMPTY, RenderType.solid());
        }
        poseStack.popPose();
        if (!firing) return;

        tilt.transform(tip.set(0, NOZZLE, 0));
        base.transform(tip);
        Vec3 from = new Vec3(pivotX + tip.x, pivotY + tip.y, pivotZ + tip.z);
        Vec3 to = LaserBlockEntity.beamEnd(target).subtract(Vec3.atLowerCornerOf(laser.getBlockPos()));
        float pulse = 0.85F + 0.15F * Mth.sin(now * 0.6F);

        // Additive and untextured, the lightning bolt's render type: a bright core inside a faint glow.
        VertexConsumer consumer = buffers.getBuffer(RenderType.lightning());
        PoseStack.Pose pose = poseStack.last();
        beam(consumer, pose, from, to, 0.035F * pulse, (r + 255) / 2, (g + 255) / 2, (b + 255) / 2, 230);
        beam(consumer, pose, from, to, 0.09F * pulse, r, g, b, 90);
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
