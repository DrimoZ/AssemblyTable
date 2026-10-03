package dev.drimoz.laserassembly.core;

import it.unimi.dsi.fastutil.doubles.DoubleArrayList;
import it.unimi.dsi.fastutil.doubles.DoubleArrays;

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
        /** No beam arriving, or only beams of the wrong type. */
        IDLE,
        WORKING,
        /** Working, but past half heat and slowing down as it climbs. */
        SLOWED,
        /** Beams arriving, all too weak for this step. */
        TOO_WEAK,
        /** Beams arriving, none fitting, some too strong for this step. */
        OVERLOAD,
        /** Hit max heat: stopped until it has cooled to half. */
        OVERHEATED,
        /** The last step just completed. The table crafts, the next tick starts over. */
        DONE
    }

    /** Scratch for the fitting beams' power, reused every tick. */
    private final DoubleArrayList powers = new DoubleArrayList();

    private int step;
    private double progress;
    private double heat;
    private boolean overheated;
    /** Last tick's pace against the strongest beam alone: the other lasers' bonus, times the heat slowdown; 0 when nothing worked. */
    private double rate;

    /**
     * One tick of work. {@code cooling} is the heat the blocks around the table remove per tick.
     * The caller only ticks a recipe whose ingredients and output room are there.
     */
    public Status tick(List<Step> steps, Beams beams, double cooling, Tuning tuning) {
        rate = 0;
        if (overheated) {
            // Lasers hold fire on a stopped table, so it only cools.
            cool(cooling, tuning);
            return Status.OVERHEATED;
        }

        Step current = steps.get(step);
        double heatAbove = current.thresholds().heatAbove();

        // Each beam is judged on its own. Those that fit work, and heat by their own excess.
        powers.clear();
        double excess = 0;
        boolean tooStrong = false, tooWeak = false;
        for (int i = 0; i < beams.size(); i++) {
            double intensity = beams.intensity(i);
            switch (current.fit(beams.type(i), intensity, tuning.rawFactor())) {
                case OK -> {
                    powers.add(current.power(beams.type(i), intensity, tuning.rawFactor()));
                    excess += Math.max(0, intensity - heatAbove);
                }
                case TOO_STRONG -> tooStrong = true;
                case TOO_WEAK -> tooWeak = true;
                case WRONG_BEAM -> {
                }
            }
        }

        heat = Math.max(0, heat + excess * tuning.heatPerIntensity() - tuning.dissipation() - cooling);
        if (heat >= tuning.maxHeat()) {
            heat = tuning.maxHeat();
            overheated = true;
            return Status.OVERHEATED;
        }
        if (powers.isEmpty()) return tooStrong ? Status.OVERLOAD : tooWeak ? Status.TOO_WEAK : Status.IDLE;

        double speed = speed(tuning.maxHeat());
        Status working = speed < 1 ? Status.SLOWED : Status.WORKING;
        double stacked = stackedPower(tuning.stackingFactor());
        // Sorted by stackedPower: the strongest beam is last.
        rate = stacked / powers.getDouble(powers.size() - 1) * speed;
        progress += stacked * tuning.fePerIntensity() * speed;
        if (progress < current.energy()) return working;

        // Surplus is dropped rather than carried: it was spent on this step's beam, and the next step
        // may want another one.
        progress = 0;
        if (++step < steps.size()) return working;
        step = 0;
        return Status.DONE;
    }

    /**
     * The fitting beams' power, strongest first, each counting {@code factor} of the one before:
     * a second laser adds 70 %, a ninth almost nothing, while every one still pays its full FE.
     * More lasers is faster, never proportionally.
     */
    private double stackedPower(double factor) {
        DoubleArrays.quickSort(powers.elements(), 0, powers.size());
        double total = 0, weight = 1;
        for (int i = powers.size() - 1; i >= 0; i--) {
            total += powers.getDouble(i) * weight;
            weight *= factor;
        }
        return total;
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
        rate = 0;
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

    public double rate() {
        return rate;
    }

    public boolean overheated() {
        return overheated;
    }
}
