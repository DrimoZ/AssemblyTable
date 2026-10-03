package dev.drimoz.laserassembly.core;

import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Predicate;

/** BuildCraft's turn-taking between the recipes a player has ticked. */
public final class Rotation {

    /**
     * The first recipe after {@code current} that can run, wrapping around, with {@code current}
     * itself considered last. Null when none can run. A {@code current} no longer selected starts
     * the search from the top.
     */
    public static <T> @Nullable T next(List<T> selected, @Nullable T current, Predicate<T> canRun) {
        int size = selected.size();
        int start = selected.indexOf(current) + 1;
        for (int i = 0; i < size; i++) {
            T candidate = selected.get((start + i) % size);
            if (canRun.test(candidate)) return candidate;
        }
        return null;
    }

    private Rotation() {}
}
