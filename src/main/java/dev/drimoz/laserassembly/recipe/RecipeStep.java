package dev.drimoz.laserassembly.recipe;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.drimoz.laserassembly.core.Step;
import dev.drimoz.laserassembly.core.Thresholds;
import dev.drimoz.laserassembly.core.Thresholds.Overrides;
import dev.drimoz.laserassembly.core.Tuning;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ExtraCodecs;

import java.util.Optional;

/** One step as a datapack writes it: its thresholds stay unresolved until the config is known. */
public record RecipeStep(Optional<Identifier> beam, int energy, Overrides intensity, boolean strict) {

    private static final Codec<Double> POSITIVE = Codec.DOUBLE.validate(value -> value > 0
            ? DataResult.success(value) : DataResult.error(() -> "must be above 0, got " + value));

    /** The optional threshold fields, shared by a recipe and its steps. */
    static final MapCodec<Overrides> OVERRIDES = RecordCodecBuilder.mapCodec(i -> i.group(
            POSITIVE.optionalFieldOf("intensity").forGetter(Overrides::min),
            POSITIVE.optionalFieldOf("heat_above").forGetter(Overrides::heatAbove),
            POSITIVE.optionalFieldOf("max_intensity").forGetter(Overrides::max)
    ).apply(i, Overrides::new));

    private static final StreamCodec<ByteBuf, Optional<Double>> OPTIONAL_DOUBLE = ByteBufCodecs.DOUBLE.apply(ByteBufCodecs::optional);

    static final StreamCodec<ByteBuf, Overrides> OVERRIDES_STREAM = StreamCodec.composite(
            OPTIONAL_DOUBLE, Overrides::min,
            OPTIONAL_DOUBLE, Overrides::heatAbove,
            OPTIONAL_DOUBLE, Overrides::max,
            Overrides::new);

    static final Codec<RecipeStep> CODEC = RecordCodecBuilder.create(i -> i.group(
            Identifier.CODEC.optionalFieldOf("beam").forGetter(RecipeStep::beam),
            ExtraCodecs.POSITIVE_INT.fieldOf("energy").forGetter(RecipeStep::energy),
            OVERRIDES.forGetter(RecipeStep::intensity),
            Codec.BOOL.optionalFieldOf("strict", false).forGetter(RecipeStep::strict)
    ).apply(i, RecipeStep::new));

    static final StreamCodec<ByteBuf, RecipeStep> STREAM_CODEC = StreamCodec.composite(
            Identifier.STREAM_CODEC.apply(ByteBufCodecs::optional), RecipeStep::beam,
            ByteBufCodecs.VAR_INT, RecipeStep::energy,
            OVERRIDES_STREAM, RecipeStep::intensity,
            ByteBufCodecs.BOOL, RecipeStep::strict,
            RecipeStep::new);

    Step resolve(Overrides recipe, Tuning tuning) {
        return new Step(beam, energy, Thresholds.resolve(intensity, recipe, tuning), strict);
    }
}
