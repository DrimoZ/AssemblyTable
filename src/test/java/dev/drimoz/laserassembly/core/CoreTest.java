package dev.drimoz.laserassembly.core;

import dev.drimoz.laserassembly.core.Assembly.Status;
import dev.drimoz.laserassembly.core.Step.Fit;
import dev.drimoz.laserassembly.core.Thresholds.Overrides;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class CoreTest {

    static final Identifier CUT = Identifier.fromNamespaceAndPath("laserassembly", "cut");
    static final Identifier WELD = Identifier.fromNamespaceAndPath("laserassembly", "weld");

    // min 2 -> a beam heats above 4 and is too strong above 8. 10 FE of progress per point of power,
    // and each extra beam counts half the one before, so sums stay exact.
    static final Tuning TUNING = new Tuning(2, 2, 4, 0.25, 10, 50, 10, 100, 0.5);

    static Step step(Identifier beam, int energy) {
        return new Step(Optional.ofNullable(beam), energy, Thresholds.resolve(Overrides.NONE, Overrides.NONE, TUNING), false);
    }

    /** {@code count} beams of one type and intensity. */
    static Beams beams(Identifier beam, double intensity, int count) {
        Beams beams = new Beams();
        for (int i = 0; i < count; i++) beams.add(beam, intensity);
        return beams;
    }

    static Beams beams(Identifier beam, double intensity) {
        return beams(beam, intensity, 1);
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
    void eachBeamIsJudgedAloneAgainstTheStep() {
        Step cut = step(CUT, 100);
        assertEquals(Fit.OK, cut.fit(CUT, 2, 0.25));
        assertEquals(Fit.TOO_WEAK, cut.fit(CUT, 1, 0.25));
        assertEquals(Fit.TOO_STRONG, cut.fit(CUT, 9, 0.25));
        assertEquals(Fit.WRONG_BEAM, cut.fit(WELD, 5, 0.25));
        // A raw beam fits a typed step, for a share of its power - unless the step is strict, or the
        // server makes lenses required with a raw factor of 0.
        assertEquals(Fit.OK, cut.fit(null, 3, 0.25));
        assertEquals(1, cut.power(null, 4, 0.25));
        assertEquals(Fit.WRONG_BEAM, new Step(Optional.of(CUT), 100, cut.thresholds(), true).fit(null, 3, 0.25));
        assertEquals(Fit.WRONG_BEAM, cut.fit(null, 3, 0));
        assertEquals(Fit.OK, step(null, 100).fit(null, 3, 0), "a raw step never needs a lens");
    }

    @Test
    void moreLasersGoFasterButEachAddsLess() {
        List<Step> steps = List.of(step(null, 1_000_000));

        Assembly one = new Assembly();
        one.tick(steps, beams(null, 2), 0, TUNING);
        assertEquals(20, one.progress(), 1e-9);

        // 2 + 1 + 0.5: the third beam adds a quarter of the first.
        Assembly three = new Assembly();
        three.tick(steps, beams(null, 2, 3), 0, TUNING);
        assertEquals(35, three.progress(), 1e-9);

        // Nine weak beams on a weak step: all fit, none overloads, none heats.
        Assembly nine = new Assembly();
        assertEquals(Status.WORKING, nine.tick(steps, beams(null, 2, 9), 0, TUNING));
        assertEquals(0, nine.heat());
    }

    @Test
    void stepsRunInOrderAndOnlyFittingBeamsWork() {
        Assembly assembly = new Assembly();
        List<Step> steps = List.of(step(CUT, 30), step(WELD, 30));

        assertEquals(Status.TOO_WEAK, assembly.tick(steps, beams(CUT, 1), 0, TUNING));
        assertEquals(Status.OVERLOAD, assembly.tick(steps, beams(CUT, 9), 0, TUNING));
        assertEquals(0, assembly.progress());
        assertEquals(0, assembly.heat(), "a beam too strong to work must not heat either");

        assertEquals(Status.WORKING, assembly.tick(steps, beams(CUT, 2), 0, TUNING));
        assertEquals(Status.WORKING, assembly.tick(steps, beams(CUT, 2), 0, TUNING));
        assertEquals(Status.IDLE, assembly.tick(steps, beams(CUT, 2), 0, TUNING));
        assertEquals(Status.WORKING, assembly.tick(steps, beams(WELD, 2), 0, TUNING));
        assertEquals(Status.DONE, assembly.tick(steps, beams(WELD, 2), 0, TUNING));
        assertEquals(0, assembly.step());
    }

    @Test
    void heatSlowsThenStopsUntilHalfCooledAndKeepsProgress() {
        Assembly assembly = new Assembly();
        List<Step> steps = List.of(step(null, 100_000));

        // 2 over the threshold at 50 a point, less 10 dissipation: 90 heat, 40 past half of 100, so
        // speed 1 - 0.75 * 40 / 50 = 0.4 of 60 FE. Then +90 again, capped at 100.
        assertEquals(Status.SLOWED, assembly.tick(steps, beams(null, 6), 0, TUNING));
        assertEquals(24, assembly.progress(), 1e-9);
        assertEquals(Status.OVERHEATED, assembly.tick(steps, beams(null, 6), 0, TUNING));
        double progress = assembly.progress();

        // Cools 10 a tick, 20 with cooling 10: 80, 60, 40 - resumes below half.
        for (int i = 0; i < 3; i++) assertEquals(Status.OVERHEATED, assembly.tick(steps, beams(null, 6), 10, TUNING));
        assertFalse(assembly.overheated());
        assertEquals(progress, assembly.progress());
        assertEquals(Status.WORKING, assembly.tick(steps, beams(null, 4), 10, TUNING));

        // And with no work at all, it still cools.
        double before = assembly.heat();
        assembly.cool(0, TUNING);
        assertEquals(before - 10, assembly.heat(), 1e-9);
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
