package dev.drimoz.laserassembly.core;

import net.minecraft.resources.ResourceLocation;

import java.util.Optional;

/**
 * One step of a recipe, with its thresholds already resolved.
 *
 * @param beam   the beam type it wants; empty for a raw step, which any beam advances
 * @param energy FE of progress to complete it
 * @param strict refuse the raw beam on a typed step - for late-game recipes only
 */
public record Step(Optional<ResourceLocation> beam, int energy, Thresholds thresholds, boolean strict) {
}
