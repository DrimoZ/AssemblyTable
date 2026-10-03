package dev.drimoz.laserassembly.core;

import it.unimi.dsi.fastutil.doubles.DoubleArrayList;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The beams reaching a table this tick, one by one. Never summed: intensity is a property of each
 * beam, judged on its own against the step - nine weak lasers are nine weak beams, not one strong
 * one. Cleared and refilled every tick rather than reallocated: this sits on the hot path.
 */
public final class Beams {

    /** Null for a raw beam: a laser without a lens. */
    private final List<@Nullable ResourceLocation> types = new ArrayList<>();
    private final DoubleArrayList intensities = new DoubleArrayList();

    public void clear() {
        types.clear();
        intensities.clear();
    }

    public void add(@Nullable ResourceLocation beam, double intensity) {
        types.add(beam);
        intensities.add(intensity);
    }

    public int size() {
        return types.size();
    }

    public @Nullable ResourceLocation type(int index) {
        return types.get(index);
    }

    public double intensity(int index) {
        return intensities.getDouble(index);
    }
}
