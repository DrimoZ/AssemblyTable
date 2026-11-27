package dev.drimoz.laserassembly;

import dev.drimoz.laserassembly.core.Tuning;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Server-side knobs, per world and synced to clients. Kept short: content belongs in datapacks.
 * Defaults come from {@link Tuning#DEFAULTS}, the same numbers recipes are checked against on load.
 */
public final class ServerConfig {

    public static final ModConfigSpec SPEC;

    private static final ModConfigSpec.DoubleValue DEFAULT_INTENSITY;
    private static final ModConfigSpec.DoubleValue HEAT_MULTIPLIER;
    private static final ModConfigSpec.DoubleValue MAX_MULTIPLIER;
    private static final ModConfigSpec.DoubleValue RAW_FACTOR;
    private static final ModConfigSpec.DoubleValue FE_PER_INTENSITY;
    private static final ModConfigSpec.DoubleValue STACKING_FACTOR;
    private static final ModConfigSpec.DoubleValue HEAT_PER_INTENSITY;
    private static final ModConfigSpec.DoubleValue DISSIPATION;
    private static final ModConfigSpec.DoubleValue MAX_HEAT;
    private static final ModConfigSpec.IntValue LASER_RANGE;

    /** Rebuilt on every load and reload, so the table reads one field per tick instead of eleven specs. */
    private static Tuning tuning = Tuning.DEFAULTS;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        Tuning d = Tuning.DEFAULTS;

        b.push("intensity");
        DEFAULT_INTENSITY = b.comment("Minimum intensity of a recipe step that sets none itself.")
                .defineInRange("default_intensity", d.defaultIntensity(), 0.01, 1000);
        HEAT_MULTIPLIER = b.comment("A beam heats the table above minimum x this, unless the recipe sets heat_above.")
                .defineInRange("heat_multiplier", d.heatMultiplier(), 1, 100);
        MAX_MULTIPLIER = b.comment("A beam is too strong for a step above minimum x this, unless the recipe sets max_intensity.",
                        "Must not be below heat_multiplier.")
                .defineInRange("max_multiplier", d.maxMultiplier(), 1, 100);
        RAW_FACTOR = b.comment("Share of a raw beam's power (a laser without a lens) that counts on a step wanting a lens.",
                        "0 makes lenses required: a laser without one holds fire on such a step.")
                .defineInRange("raw_beam_factor", d.rawFactor(), 0, 1);
        FE_PER_INTENSITY = b.comment("FE of recipe progress one point of a beam's intensity is worth per tick.")
                .defineInRange("fe_per_intensity", d.fePerIntensity(), 1, 100_000);
        STACKING_FACTOR = b.comment("Each extra laser on a table counts this much of the one before it: 0.7 makes a second",
                        "laser add 70 %, a third 49 %, and nine about three times one. 1 adds them up in full.")
                .defineInRange("stacking_factor", d.stackingFactor(), 0, 1);
        b.pop();

        b.push("heat");
        HEAT_PER_INTENSITY = b.comment("Heat gained per tick per point of intensity above the heating threshold.")
                .defineInRange("heat_per_intensity", d.heatPerIntensity(), 0, 1000);
        DISSIPATION = b.comment("Heat a table loses per tick on its own, before any coolant around it.")
                .defineInRange("dissipation", d.dissipation(), 0, 1000);
        MAX_HEAT = b.comment("Past half of this the table slows down; at this it stops until cooled to half.")
                .defineInRange("max_heat", d.maxHeat(), 1, 1_000_000);
        b.pop();

        b.push("laser");
        LASER_RANGE = b.comment("How far in front of it a laser looks for a table, in blocks.")
                .defineInRange("range", 8, 1, 32);
        b.pop();

        SPEC = b.build();
    }

    static void onLoad(ModConfigEvent event) {
        if (event.getConfig().getSpec() != SPEC) return;
        // Not coerced: the owner must see it. Kept as written, every recipe that sets neither
        // threshold refuses to run, and the tables name each one in the log once.
        if (MAX_MULTIPLIER.get() < HEAT_MULTIPLIER.get()) {
            LaserAssembly.LOGGER.error("Server config: intensity.max_multiplier {} is below intensity.heat_multiplier {}; "
                    + "recipes that set neither max_intensity nor heat_above will not run", MAX_MULTIPLIER.get(), HEAT_MULTIPLIER.get());
        }
        tuning = new Tuning(DEFAULT_INTENSITY.get(), HEAT_MULTIPLIER.get(), MAX_MULTIPLIER.get(),
                RAW_FACTOR.get(), FE_PER_INTENSITY.get(), HEAT_PER_INTENSITY.get(), DISSIPATION.get(), MAX_HEAT.get(), STACKING_FACTOR.get());
    }

    public static Tuning tuning() {
        return tuning;
    }

    // Before the spec loads - a menu built at login, a table placed in a fresh world - the defaults stand.

    public static int laserRange() {
        return SPEC.isLoaded() ? LASER_RANGE.get() : 8;
    }

    private ServerConfig() {}
}
