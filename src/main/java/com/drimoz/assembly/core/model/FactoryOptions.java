package com.drimoz.assembly.core.model;

import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;

public class FactoryOptions {

    // Private Properties

    private EFactoryFace faceBottom = EFactoryFace.ENERGY;
    private EFactoryFace faceNorth = EFactoryFace.NONE;
    private EFactoryFace faceEast = EFactoryFace.NONE;
    private EFactoryFace faceSouth = EFactoryFace.NONE;
    private EFactoryFace faceWest = EFactoryFace.NONE;

    private ERedstoneMode redstoneMode = ERedstoneMode.IGNORED;

    private boolean showAugmentGUI = false;
    private boolean showConfigGUI = false;

    private boolean autoInput = false;
    private boolean autoOutput = false;

    // Life Cycle

    public FactoryOptions() {

    }

    // Interface

    public void read(CompoundTag tag) {
        this.faceBottom = EFactoryFace.valueOf(tag.getString("faceBottom"));
        this.faceNorth = EFactoryFace.valueOf(tag.getString("faceNorth"));
        this.faceEast = EFactoryFace.valueOf(tag.getString("faceEast"));
        this.faceSouth = EFactoryFace.valueOf(tag.getString("faceSouth"));
        this.faceWest = EFactoryFace.valueOf(tag.getString("faceWest"));

        this.redstoneMode = ERedstoneMode.valueOf(tag.getString("redstoneMode"));

        this.showAugmentGUI = tag.getBoolean("showAugmentGUI");
        this.showConfigGUI = tag.getBoolean("showConfigGUI");

        this.autoInput = tag.getBoolean("autoInput");
        this.autoOutput = tag.getBoolean("autoOutput");

        onChanged();
    }

    public void write(CompoundTag tag) {
        tag.putString("faceBottom", this.faceBottom.name());
        tag.putString("faceNorth", this.faceNorth.name());
        tag.putString("faceEast", this.faceEast.name());
        tag.putString("faceSouth", this.faceSouth.name());
        tag.putString("faceWest", this.faceWest.name());

        tag.putString("redstoneMode", this.redstoneMode.name());

        tag.putBoolean("showAugmentGUI", this.showAugmentGUI);
        tag.putBoolean("showConfigGUI", this.showConfigGUI);

        tag.putBoolean("autoInput", this.autoInput);
        tag.putBoolean("autoOutput", this.autoOutput);
    }

    public void onChanged() {

    }

    // Inner work



    // Getters & Setters

    public EFactoryFace getFace(Direction direction) {
        return switch (direction) {
            case DOWN -> faceBottom;
            case NORTH -> faceNorth;
            case EAST -> faceEast;
            case SOUTH -> faceSouth;
            case WEST -> faceWest;
            default -> EFactoryFace.NONE;
        };
    }

    public void setFace(Direction direction, EFactoryFace face) {
        switch (direction) {
            case DOWN:
                this.faceBottom = face;
                break;
            case NORTH:
                this.faceNorth = face;
                break;
            case EAST:
                this.faceEast = face;
                break;
            case SOUTH:
                this.faceSouth = face;
                break;
            case WEST:
                this.faceWest = face;
                break;
        }
    }

    public ERedstoneMode getRedstoneMode() {
        return redstoneMode;
    }

    public void setRedstoneMode(ERedstoneMode redstoneMode) {
        this.redstoneMode = redstoneMode;
    }

    public boolean isShowAugmentGUI() {
        return showAugmentGUI;
    }

    public void setShowAugmentGUI(boolean showAugmentGUI) {
        this.showAugmentGUI = showAugmentGUI;
    }

    public boolean isShowConfigGUI() {
        return showConfigGUI;
    }

    public void setShowConfigGUI(boolean showConfigGUI) {
        this.showConfigGUI = showConfigGUI;
    }

    public boolean isAutoInput() {
        return autoInput;
    }

    public void setAutoInput(boolean autoInput) {
        this.autoInput = autoInput;
    }

    public boolean isAutoOutput() {
        return autoOutput;
    }

    public void setAutoOutput(boolean autoOutput) {
        this.autoOutput = autoOutput;
    }
}

