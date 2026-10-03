package dev.drimoz.laserassembly.core;

import java.util.List;

/**
 * A table's work on its current recipe: which step, how far into it, and how hot the table is.
 *
 * <p>Ingredients are not its concern - the table consumes them on {@link Status#DONE}, so breaking
 * the table or switching recipe never needs to give anything back. Heat belongs to the table, not to
 * the recipe, so {@link #reset()} keeps it.
 */
public final class Assembly {

    public enum Status {
        /** Nothing that counts toward this step is arriving. */
        IDLE,
        WORKING,
        /** Working, but past half heat and slowing down as it climbs. */
        SLOWED,
        /** Below the step's minimum intensity: no progress. */
        TOO_WEAK,
        /** Above the step's maximum: no progress until the beams come down. */
        OVERLOAD,
        /** Hit max heat: stopped until it has cooled to half. */
        OVERHEATED,
        /** The last step just completed. The table crafts, the next tick starts over. */
        DONE
    }

    private int step;
    private double progress;
    private double heat;
    private boolean overheated;

    /**
     * One tick of work. {@code cooling} is the heat the blocks around the table remove per tick.
     * The caller only ticks a recipe whose ingredients and output room are there.
     */
    public Status tick(List<Step> steps, Beams beams, double cooling, Tuning tuning) {
        if (overheated) {
            // Lasers hold fire on a stopped table, so it only cools.
            cool(cooling, tuning);
            return Status.OVERHEATED;
        }

        Step current = steps.get(step);
        Thresholds limits = current.thresholds();
        double intensity = beams.effective(current, tuning.rawFactor());

        double excess = Math.max(0, intensity - limits.heatAbove());
        heat = Math.max(0, heat + excess * tuning.heatPerIntensity() - tuning.dissipation() - cooling);
        if (heat >= tuning.maxHeat()) {
            heat = tuning.maxHeat();
            overheated = true;
            return Status.OVERHEATED;
        }

        if (intensity <= 0) return Status.IDLE;
        if (intensity < limits.min()) return Status.TOO_WEAK;
        if (intensity > limits.max()) return Status.OVERLOAD;

        double speed = speed(tuning.maxHeat());
        Status working = speed < 1 ? Status.SLOWED : Status.WORKING;
        progress += intensity * tuning.fePerIntensity() * speed;
        if (progress < current.energy()) return working;

        // Surplus is dropped rather than carried: it was spent on this step's beam, and the next step
        // may want another one.
        progress = 0;
        if (++step < steps.size()) return working;
        step = 0;
        return Status.DONE;
    }

    /**
     * Heat as a warning before it is a stop: full speed up to half heat, then down to 25 % just
     * short of the maximum. Half is also where an overheated table resumes, so it comes back at
     * full speed rather than straight into the slow zone.
     */
    private double speed(double maxHeat) {
        double half = maxHeat / 2;
        return heat <= half ? 1 : 1 - 0.75 * (heat - half) / half;
    }

    /**
     * A tick with no work - overheated, idle, nothing to craft. Heat leaves on its own whatever the
     * table is doing; resuming at half rather than just under the limit keeps an overheated table
     * from flickering on and off every tick.
     */
    public void cool(double cooling, Tuning tuning) {
        heat = Math.max(0, heat - tuning.dissipation() - cooling);
        if (heat <= tuning.maxHeat() / 2) overheated = false;
    }

    /** Back to the first step, for a recipe change. The heat stays: it is the table's. */
    public void reset() {
        step = 0;
        progress = 0;
    }

    /** From a save. */
    public void restore(int step, double progress, double heat, boolean overheated) {
        this.step = step;
        this.progress = progress;
        this.heat = heat;
        this.overheated = overheated;
    }

    public int step() {
        return step;
    }

    public double progress() {
        return progress;
    }

    public double heat() {
        return heat;
    }

    public boolean overheated() {
        return overheated;
    }
}
