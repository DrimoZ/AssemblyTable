package dev.drimoz.laserassembly.core;

import dev.drimoz.laserassembly.core.Assembly.Status;
import dev.drimoz.laserassembly.core.Thresholds.Overrides;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class CoreTest {

    static final ResourceLocation CUT = ResourceLocation.fromNamespaceAndPath("laserassembly", "cut");
    static final ResourceLocation WELD = ResourceLocation.fromNamespaceAndPath("laserassembly", "weld");

    // min 2 -> heats above 4, overloads above 8. 10 FE of progress per point of intensity.
    static final Tuning TUNING = new Tuning(2, 2, 4, 0.25, 10, 50, 10, 100);

    static Step step(ResourceLocation beam, int energy) {
        return new Step(Optional.ofNullable(beam), energy, Thresholds.resolve(Overrides.NONE, Overrides.NONE, TUNING), false);
    }

    static Beams beams(ResourceLocation beam, double intensity) {
        Beams beams = new Beams();
        beams.add(beam, intensity);
        return beams;
    }

    @Test
    void thresholdsFallBackFromStepToRecipeToConfigAndRefuseContradictions() {
        Overrides recipe = new Overrides(Optional.of(3.0), Optional.of(10.0), Optional.empty());
        Overrides step = new Overrides(Optional.of(5.0), Optional.empty(), Optional.empty());

        assertEquals(new Thresholds(5, 10, 20), Thresholds.resolve(step, recipe, TUNING));
        assertEquals(new Thresholds(2, 4, 8), Thresholds.resolve(Overrides.NONE, Overrides.NONE, TUNING));
        assertThrows(IllegalArgumentException.class, () -> Thresholds.resolve(
                new Overrides(Optional.of(5.0), Optional.of(3.0), Optional.empty()), Overrides.NONE, TUNING));
    }

    @Test
    void typedStepTakesItsTypeAndAShareOfRawNeverAnotherLens() {
        Beams beams = beams(CUT, 2);
        beams.add(WELD, 5);
        beams.add(null, 4);

        assertEquals(3, beams.effective(step(CUT, 100), 0.25));
        assertEquals(11, beams.effective(step(null, 100), 0.25));
    }

    @Test
    void stepsRunInOrderOnlyInsideTheirWindow() {
        Assembly assembly = new Assembly();
        List<Step> steps = List.of(step(CUT, 30), step(WELD, 30));

        assertEquals(Status.TOO_WEAK, assembly.tick(steps, beams(CUT, 1), 0, TUNING));
        // Over the max is over the heat threshold too: cooled hard so only the overload shows.
        assertEquals(Status.OVERLOAD, assembly.tick(steps, beams(CUT, 9), 1000, TUNING));
        assertEquals(0, assembly.progress());

        assertEquals(Status.WORKING, assembly.tick(steps, beams(CUT, 2), 0, TUNING));
        assertEquals(Status.WORKING, assembly.tick(steps, beams(CUT, 2), 0, TUNING));
        assertEquals(Status.IDLE, assembly.tick(steps, beams(CUT, 2), 0, TUNING));
        assertEquals(Status.WORKING, assembly.tick(steps, beams(WELD, 2), 0, TUNING));
        assertEquals(Status.DONE, assembly.tick(steps, beams(WELD, 2), 0, TUNING));
        assertEquals(0, assembly.step());
    }

    @Test
    void overheatingStopsUntilHalfCooledAndKeepsProgress() {
        Assembly assembly = new Assembly();
        List<Step> steps = List.of(step(null, 100_000));

        // 2 over the threshold at 50 a point, less 10 dissipation: +90, then capped at 100.
        assertEquals(Status.WORKING, assembly.tick(steps, beams(null, 6), 0, TUNING));
        assertEquals(Status.OVERHEATED, assembly.tick(steps, beams(null, 6), 0, TUNING));
        double progress = assembly.progress();

        // Cools 10 a tick, 20 with cooling 10: 80, 60, 40 - resumes below half.
        for (int i = 0; i < 3; i++) assertEquals(Status.OVERHEATED, assembly.tick(steps, beams(null, 6), 10, TUNING));
        assertFalse(assembly.overheated());
        assertEquals(progress, assembly.progress());
        assertEquals(Status.WORKING, assembly.tick(steps, beams(null, 4), 10, TUNING));
    }

    @Test
    void rotationTakesTurnsSkipsWhatCannotRunAndWraps() {
        List<String> selected = List.of("a", "b", "c");

        assertEquals("b", Rotation.next(selected, "a", r -> true));
        assertEquals("a", Rotation.next(selected, "c", r -> true));
        assertEquals("a", Rotation.next(selected, "a", Set.of("a")::contains));
        assertNull(Rotation.next(selected, "a", r -> false));
    }
}
