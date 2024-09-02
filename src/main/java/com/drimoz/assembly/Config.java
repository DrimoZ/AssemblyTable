package com.drimoz.assembly;

import net.minecraftforge.common.ForgeConfigSpec;

public class Config
{
    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();
    public static final ForgeConfigSpec SPEC;

    // AssemblyTable

    public static final ForgeConfigSpec.IntValue ASSEMBLY_TABLE_UPGRADE_COUNT;

    static {
        BUILDER.comment("Assembly Common Configs");
        BUILDER.push("Assembly");

        // Assembly Table

        BUILDER.push("AssemblyTable");

        ASSEMBLY_TABLE_UPGRADE_COUNT = BUILDER
                .comment("Choose whether the Assembly Table have slots")
                .comment("0 means no upgrades - CARE : Reducing the upgrade count when Upgrade Cards are inside an Assembly Table may cause Item suppressions !")
                .defineInRange("assembly_table_upgrades_count", 3, 0, 5);

        BUILDER.pop();

        // Assembly Taser

        BUILDER.push("AssemblyLaser");

        BUILDER.pop();

        BUILDER.pop();

        SPEC = BUILDER.build();
    }
}
