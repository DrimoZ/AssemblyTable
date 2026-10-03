package dev.drimoz.laserassembly.core;

import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/**
 * One step of a recipe, with its thresholds already resolved.
 *
 * @param beam   the beam type it wants; empty for a raw step, which any beam advances
 * @param energy FE of progress to complete it
 * @param strict refuse the raw beam on a typed step - for late-game recipes only
 */
public record Step(Optional<ResourceLocation> beam, int energy, Thresholds thresholds, boolean strict) {

    /** How one beam fits this step. Only {@link #OK} does any work; a laser holds fire otherwise. */
    public enum Fit { OK, WRONG_BEAM, TOO_WEAK, TOO_STRONG }

    /**
     * A raw beam fits a step that wants a lens only when it counts for something: {@code rawFactor}
     * of 0 - a server that wants lenses required - or a strict step turns it away, so its laser
     * holds fire instead of heating the table for nothing.
     */
    public Fit fit(@Nullable ResourceLocation beamType, double intensity, double rawFactor) {
        if (beam.isPresent() && (beamType == null ? strict || rawFactor <= 0 : !beamType.equals(beam.get()))) return Fit.WRONG_BEAM;
        if (intensity < thresholds.min()) return Fit.TOO_WEAK;
        if (intensity > thresholds.max()) return Fit.TOO_STRONG;
        return Fit.OK;
    }

    /** A raw beam on a step that wants a lens counts for a share of its power: lenses speed a step up, they never gate it. */
    public double power(@Nullable ResourceLocation beamType, double intensity, double rawFactor) {
        return beam.isPresent() && beamType == null ? intensity * rawFactor : intensity;
    }
}
