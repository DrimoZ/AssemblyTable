package com.drimoz.assembly.core.utils;

import com.drimoz.assembly.Assembly;
import net.minecraft.resources.ResourceLocation;

public class Constants {

    // Menu

    public static class Menu {

        // Slot Count

        public static final int HOTBAR_SLOT_COUNT = 9;
        public static final int PLAYER_INVENTORY_ROW_COUNT = 3;
        public static final int PLAYER_INVENTORY_COLUMN_COUNT = 9;
        public static final int PLAYER_INVENTORY_SLOT_COUNT = PLAYER_INVENTORY_COLUMN_COUNT * PLAYER_INVENTORY_ROW_COUNT;
        public static final int VANILLA_SLOT_COUNT = HOTBAR_SLOT_COUNT + PLAYER_INVENTORY_SLOT_COUNT;
        public static final int VANILLA_FIRST_SLOT_INDEX = 0;
        public static final int TE_INVENTORY_FIRST_SLOT_INDEX = VANILLA_FIRST_SLOT_INDEX + VANILLA_SLOT_COUNT;

        // Hotbar Slots

        public static final int HOTBAR_FIRST_SLOT_X = 35;
        public static final int HOTBAR_FIRST_SLOT_Y = 252;

        // Inventory Slots

        public static final int PLAYER_INVENTORY_FIRST_SLOT_X = 35;
        public static final int PLAYER_INVENTORY_FIRST_SLOT_Y = 194;

        // Input Slots

        public static final int INPUT_FIRST_SLOT_X = 35;
        public static final int INPUT_FIRST_SLOT_Y = 18;

        public static final int INPUT_ROW_COUNT = 6;
        public static final int INPUT_COLUMN_COUNT = 4;

        // Shadow Slots

        public static final int SHADOW_FIRST_SLOT_X = 125;
        public static final int SHADOW_FIRST_SLOT_Y = 18;

        public static final int SHADOW_ROW_COUNT = 9;
        public static final int SHADOW_COLUMN_COUNT = 5;

        // Output Slots

        public static final int OUTPUT_FIRST_SLOT_X = 35;
        public static final int OUTPUT_FIRST_SLOT_Y = 144;

        public static final int OUTPUT_ROW_COUNT = 2;
        public static final int OUTPUT_COLUMN_COUNT = 4;
    }

    // Screen

    public static class Screen {

        // Textures

        public static final ResourceLocation OLD_GUI_TEXTURE = new ResourceLocation(Assembly.MOD_ID, "textures/gui/assembly_table.png");
        public static final ResourceLocation GUI_TEXTURE = new ResourceLocation(Assembly.MOD_ID, "textures/gui/assembly_table_gui.png");

        public static final int TEXTURE_WIDTH = 256;
        public static final int TEXTURE_HEIGHT = 512;

        // Screen Size

        public static final int GUI_WIDTH = 229;
        public static final int GUI_HEIGHT = 275;

        // Progress Bar

        public static final int PROGRESS_BAR_X = 113;
        public static final int PROGRESS_BAR_Y = 18;
        public static final int FILLED_PROGRESS_BAR_X = 1;
        public static final int FILLED_PROGRESS_BAR_Y = 278;

        public static final int PROGRESS_BAR_WIDTH = 4;
        public static final int PROGRESS_BAR_HEIGHT = 160;

        // Energy Bar

        public static final int ENERGY_BAR_X = 12;
        public static final int ENERGY_BAR_Y = 110;
        public static final int FILLED_ENERGY_BAR_X = 10;
        public static final int FILLED_ENERGY_BAR_Y = 280;

        public static final int ENERGY_BAR_WIDTH = 12;
        public static final int ENERGY_BAR_HEIGHT = 66;

        // Slot

        public static final int SLOT_X = 238;
        public static final int SLOT_Y = 0;

        public static final int SLOT_WIDTH = 18;
        public static final int SLOT_HEIGHT = 18;
    }


}
