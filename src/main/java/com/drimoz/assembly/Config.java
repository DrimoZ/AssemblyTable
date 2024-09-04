
package com.drimoz.assembly;

import net.minecraftforge.common.ForgeConfigSpec;

public class Config
{
    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();
    public static final ForgeConfigSpec SPEC;

    // AssemblyTable

    public static final ForgeConfigSpec.IntValue ASSEMBLY_TABLE_UPGRADE_COUNT;

    public static final ForgeConfigSpec.BooleanValue ASSEMBLY_TABLE_NO_LASER;
    public static final ForgeConfigSpec.DoubleValue ASSEMBLY_TABLE_NO_LASER_SPEED_MULTIPLIER;
    public static final ForgeConfigSpec.IntValue ASSEMBLY_TABLE_NO_LASER_ENERGY_CAPACITY;
    public static final ForgeConfigSpec.IntValue ASSEMBLY_TABLE_NO_LASER_ENERGY_INPUT_RATE;

    static {
        BUILDER.comment("Assembly Common Configs");
        BUILDER.push("Assembly");

            // Assembly Table

            BUILDER.push("AssemblyTable");

            ASSEMBLY_TABLE_UPGRADE_COUNT = BUILDER
                    .comment("Choose whether the Assembly Table have slots")
                    .comment("0 means no upgrades - CARE : Reducing the upgrade count when Upgrade Cards are inside an Assembly Table may cause Item suppressions !")
                    .comment("Default : 3")
                    .defineInRange("assembly_table_upgrades_count", 3, 1, 5);

                // Basic Assembly Table

                BUILDER.comment("The properties in the following section relate to a single feature: the ability to use the Assembly Table without lasers");
                BUILDER.comment("to create a ‘Basic’ version before allowing players to use the machine to its full potential.");
                BUILDER.comment("The ‘allow_no_laser’ property defines whether this mechanic exists in-game or not.");
                BUILDER.comment("If this property is set to ‘false’, all the properties in this section will be ignored.");
                BUILDER.push("NoLaser");


                ASSEMBLY_TABLE_NO_LASER = BUILDER
                        .comment("Allow the Assembly Table to work without Lasers")
                        .comment("The Assembly Table will than accept energy bellow her and work at a slower rate.")
                        .comment("A card named \"Laser Augmentation\" will then be needed to make the Assembly Table works with Lasers.")
                        .comment("When that card is applied on the Assembly Table")
                        .comment("Default : true")
                        .define("allow_no_laser", true);

                ASSEMBLY_TABLE_NO_LASER_SPEED_MULTIPLIER = BUILDER
                        .comment("Defines the multiplier applied on a recipe base speed while using the No Laser Assembly Table.")
                        .comment("Default : 0.85")
                        .defineInRange("assembly_table_no_laser_speed_modifier", .85, .001, 10.0);

                ASSEMBLY_TABLE_NO_LASER_ENERGY_CAPACITY = BUILDER
                        .comment("Defines the maximum amount of energy the Assembly Table has when it can operate without Laser.")
                        .comment("Default : 10000")
                        .defineInRange("assembly_table_no_laser_energy_capacity", 10000, 1, 100000000);

                ASSEMBLY_TABLE_NO_LASER_ENERGY_INPUT_RATE = BUILDER
                        .comment("Defines the amount of energy per tick that the Assembly Table can accept as input when it can operate without Laser.")
                        .comment("Default : 500")
                        .defineInRange("assembly_table_no_laser_max_energy_input", 500, 1, 100000000);

                BUILDER.pop();

                // Assembly Table Augments

                BUILDER.push("Augmentation Cards");
                BUILDER.pop();



            BUILDER.pop();

        // Assembly Taser

        BUILDER.push("AssemblyLaser");

        BUILDER.pop();

        BUILDER.pop();

        SPEC = BUILDER.build();
    }
}
