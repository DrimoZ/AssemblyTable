package dev.drimoz.laserassembly.core;

import it.unimi.dsi.fastutil.objects.Object2DoubleOpenHashMap;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * The intensity a table receives this tick, summed per beam type. One instance per table, cleared
 * and refilled every tick rather than reallocated: this sits on the hot path.
 */
public final class Beams {

    private final Object2DoubleOpenHashMap<ResourceLocation> typed = new Object2DoubleOpenHashMap<>();
    private double raw;
    private double total;

    public void clear() {
        typed.clear();
        raw = 0;
        total = 0;
    }

    /** {@code beam} is null for a raw beam: a laser without a lens, or FE fed into the table directly. */
    public void add(@Nullable ResourceLocation beam, double intensity) {
        if (beam == null) raw += intensity;
        else typed.addTo(beam, intensity);
        total += intensity;
    }

    /**
     * What counts toward {@code step}. A raw step takes every beam; a typed step takes its own type
     * and, unless strict, a share of the raw beam. A lens of the wrong type counts for nothing.
     */
    public double effective(Step step, double rawFactor) {
        if (step.beam().isEmpty()) return total;
        double own = typed.getDouble(step.beam().get());
        return step.strict() ? own : own + raw * rawFactor;
    }
}
