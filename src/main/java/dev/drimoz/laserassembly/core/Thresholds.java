package dev.drimoz.laserassembly.core;

import java.util.Optional;

/**
 * The three intensities that shape a step: below {@code min} nothing advances, above
 * {@code heatAbove} the table heats, above {@code max} it overloads.
 */
public record Thresholds(double min, double heatAbove, double max) {

    public Thresholds {
        // Refused rather than reordered: a recipe whose step overheats before it can advance is a
        // datapack mistake, and correcting it silently would hide it from its author.
        if (!(min > 0)) throw new IllegalArgumentException("intensity must be above 0, got " + min);
        if (heatAbove < min) throw new IllegalArgumentException("heat_above " + heatAbove + " is below intensity " + min);
        if (max < heatAbove) throw new IllegalArgumentException("max_intensity " + max + " is below heat_above " + heatAbove);
    }

    /** What a recipe or a step may declare. Every field is optional. */
    public record Overrides(Optional<Double> min, Optional<Double> heatAbove, Optional<Double> max) {
        public static final Overrides NONE = new Overrides(Optional.empty(), Optional.empty(), Optional.empty());
    }

    /**
     * Each value comes from the step, else the recipe; the minimum then falls back to the config,
     * and the two others to multiples of the minimum. Nothing has to be written down.
     */
    public static Thresholds resolve(Overrides step, Overrides recipe, Tuning tuning) {
        double min = step.min().or(recipe::min).orElse(tuning.defaultIntensity());
        double heatAbove = step.heatAbove().or(recipe::heatAbove).orElse(min * tuning.heatMultiplier());
        double max = step.max().or(recipe::max).orElse(min * tuning.maxMultiplier());
        return new Thresholds(min, heatAbove, max);
    }
}
