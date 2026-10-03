package dev.drimoz.laserassembly.core;

/**
 * The server-wide numbers the table runs on, read from the config. A record so {@code core/} never
 * reads a config spec itself and stays testable without one.
 *
 * @param defaultIntensity minimum intensity of a step that declares none, nor its recipe
 * @param heatMultiplier   heating starts above {@code min ×} this, unless a recipe says otherwise
 * @param maxMultiplier    overload above {@code min ×} this, unless a recipe says otherwise
 * @param rawFactor        share of a raw beam that counts on a typed step: lenses speed a step up,
 *                         they never gate it
 * @param fePerIntensity   FE of progress one point of intensity is worth per tick
 * @param heatPerIntensity heat gained per tick per point of intensity above the heating threshold
 * @param dissipation      heat lost per tick on its own
 * @param maxHeat          heat at which the table stops until it has cooled to half
 */
public record Tuning(double defaultIntensity, double heatMultiplier, double maxMultiplier,
                     double rawFactor, double fePerIntensity,
                     double heatPerIntensity, double dissipation, double maxHeat) {

    /**
     * The config's defaults. Also what a recipe's thresholds are checked against when it loads,
     * since datapacks load before the server config does.
     */
    public static final Tuning DEFAULTS = new Tuning(1, 3, 10, 0.25, 20, 1, 1, 1000);
}
