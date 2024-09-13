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

        public static final int HOTBAR_FIRST_SLOT_X = 64;
        public static final int HOTBAR_FIRST_SLOT_Y = 252;

        // Inventory Slots

        public static final int PLAYER_INVENTORY_FIRST_SLOT_X = 64;
        public static final int PLAYER_INVENTORY_FIRST_SLOT_Y = 194;

        // Input Slots

        public static final int INPUT_FIRST_SLOT_X = 64;
        public static final int INPUT_FIRST_SLOT_Y = 18;

        public static final int INPUT_ROW_COUNT = 6;
        public static final int INPUT_COLUMN_COUNT = 4;

        // Shadow Slots

        public static final int SHADOW_FIRST_SLOT_X = 154;
        public static final int SHADOW_FIRST_SLOT_Y = 18;

        public static final int SHADOW_ROW_COUNT = 9;
        public static final int SHADOW_COLUMN_COUNT = 5;

        // Output Slots

        public static final int OUTPUT_FIRST_SLOT_X = 64;
        public static final int OUTPUT_FIRST_SLOT_Y = 144;

        public static final int OUTPUT_ROW_COUNT = 2;
        public static final int OUTPUT_COLUMN_COUNT = 4;
    }

    // Screen

    public static class Screen {

        // Textures

        public static final ResourceLocation GUI_TEXTURE = new ResourceLocation(Assembly.MOD_ID, "textures/gui/assembly_table_gui.png");

        public static final int TEXTURE_WIDTH = 512;
        public static final int TEXTURE_HEIGHT = 512;

        // Screen Size

        public static final int GUI_WIDTH = 288;
        public static final int GUI_HEIGHT = 276;

        // Progress Bar

        public static final int PROGRESS_BAR_X = 142;
        public static final int PROGRESS_BAR_Y = 18;
        public static final int FILLED_PROGRESS_BAR_X = 301;
        public static final int FILLED_PROGRESS_BAR_Y = 1;

        public static final int PROGRESS_BAR_WIDTH = 4;
        public static final int PROGRESS_BAR_HEIGHT = 160;

        // Energy Bar

        public static final int ENERGY_BAR_X = 41;
        public static final int ENERGY_BAR_Y = 56;
        public static final int FILLED_ENERGY_BAR_X = 313;
        public static final int FILLED_ENERGY_BAR_Y = 3;

        public static final int ENERGY_BAR_WIDTH = 12;
        public static final int ENERGY_BAR_HEIGHT = 120;

        // Slot

        public static final int SLOT_CRAFTING_X = 330;
        public static final int SLOT_CRAFTING_Y = 0;

        public static final int SLOT_WAITING_X = 330;
        public static final int SLOT_WAITING_Y = 18;

        public static final int SLOT_WIDTH = 18;
        public static final int SLOT_HEIGHT = 18;

        // Redstone

        public static final int REDSTONE_X = 266;
        public static final int REDSTONE_Y = 161;

        public static final int REDSTONE_IGNORED_X = 350;
        public static final int REDSTONE_IGNORED_Y = 0;

        public static final int REDSTONE_LOW_X = 350;
        public static final int REDSTONE_LOW_Y = 18;

        public static final int REDSTONE_HIGH_X = 350;
        public static final int REDSTONE_HIGH_Y = 36;

        // Consumption

        public static final int CONSUMPTION_X = 4;
        public static final int CONSUMPTION_Y = 161;
    }


}
