package dev.drimoz.laserassembly.core;

/**
 * The server-wide numbers the table runs on, read from the config. A record so {@code core/} never
 * reads a config spec itself and stays testable without one.
 *
 * @param defaultIntensity minimum intensity of a step that declares none, nor its recipe
 * @param heatMultiplier   a beam heats the table above {@code min ×} this, unless a recipe says otherwise
 * @param maxMultiplier    a beam is too strong above {@code min ×} this, unless a recipe says otherwise
 * @param rawFactor        share of a raw beam's power that counts on a typed step
 * @param fePerIntensity   FE of progress one point of a beam's intensity is worth per tick
 * @param heatPerIntensity heat per tick per point of a beam's intensity above the heating threshold
 * @param dissipation      heat lost per tick on its own
 * @param maxHeat          heat at which the table stops until it has cooled to half
 * @param stackingFactor   each extra beam counts this much of the one before it: more lasers, less each
 */
public record Tuning(double defaultIntensity, double heatMultiplier, double maxMultiplier,
                     double rawFactor, double fePerIntensity,
                     double heatPerIntensity, double dissipation, double maxHeat, double stackingFactor) {

    /**
     * The config's defaults. Also what a recipe's thresholds are checked against when it loads,
     * since datapacks load before the server config does.
     */
    public static final Tuning DEFAULTS = new Tuning(1, 3, 10, 0.1, 20, 1, 1, 1000, 0.7);
}
