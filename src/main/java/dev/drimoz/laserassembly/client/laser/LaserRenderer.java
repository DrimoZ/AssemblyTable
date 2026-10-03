package dev.drimoz.laserassembly.client.laser;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.drimoz.laserassembly.client.BeamColors;
import dev.drimoz.laserassembly.content.laser.LaserBlockEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** The beam, from the laser's face to the middle of the table's top, while it fires. */
public class LaserRenderer implements BlockEntityRenderer<LaserBlockEntity> {

    public LaserRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(LaserBlockEntity laser, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light, int overlay) {
        BlockPos target = laser.target();
        if (!laser.firing() || target == null) return;

        Vec3 origin = Vec3.atLowerCornerOf(laser.getBlockPos());
        Vec3 from = laser.muzzle().subtract(origin);
        Vec3 to = LaserBlockEntity.beamEnd(target).subtract(origin);
        float pulse = 0.85F + 0.15F * Mth.sin((laser.getLevel().getGameTime() + partialTick) * 0.6F);

        // Additive and untextured, the lightning bolt's render type: a bright core inside a faint glow.
        VertexConsumer consumer = buffers.getBuffer(RenderType.lightning());
        PoseStack.Pose pose = poseStack.last();
        // The core whitened towards its centre, the glow in the lens's own colour.
        int rgb = BeamColors.rgb(laser.beam());
        int r = rgb >> 16 & 0xFF, g = rgb >> 8 & 0xFF, b = rgb & 0xFF;
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
