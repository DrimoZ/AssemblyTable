package com.drimoz.assembly.core.model;

import com.drimoz.assembly.Assembly;
import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.energy.EnergyStorage;

public class EnergyContainer extends EnergyStorage {

    //Life cycle

    public EnergyContainer(int capacity) {
        super(capacity);
        this.onEnergyChanged();
    }

    public EnergyContainer(int capacity, int maxTransfer) {
        super(capacity, maxTransfer);
        this.onEnergyChanged();
    }

    public EnergyContainer(int capacity, int maxReceive, int maxExtract) {
        super(capacity, maxReceive, maxExtract);
        this.onEnergyChanged();
    }

    public EnergyContainer(int capacity, int maxReceive, int maxExtract, int energy) {
        super(capacity, maxReceive, maxExtract, energy);
        this.onEnergyChanged();
    }

    // Event Emitters

    public void onEnergyChanged() {}

    @Override
    public int receiveEnergy(int maxReceive, boolean simulate) {
        int x = super.receiveEnergy(maxReceive, simulate);
        if (!simulate) onEnergyChanged();
        return x;
    }

    @Override
    public int extractEnergy(int maxExtract, boolean simulate) {
        int x = super.extractEnergy(maxExtract, simulate);
        if (!simulate) onEnergyChanged();
        return x;
    }

    public int useEnergy(int amount, boolean simulate) {
        int energyExtracted = Math.min(energy, amount);
        if (!simulate) {
            energy -= energyExtracted;
            onEnergyChanged();
        }
        return energyExtracted;
    }


    // Getters

    public int getCurrentEnergy() {
        return this.getEnergyStored();
    }

    public int getEnergyCapacity() {
        return this.getMaxEnergyStored();
    }

    public int getMaxReceive() {
        return this.maxReceive;
    }

    public int getMaxExtract() {
        return this.maxExtract;
    }

    // Setters

    public void overrideCurrentEnergy(int energy) {
        this.energy = energy;

        if (this.energy > this.capacity) {
            this.energy = this.capacity;
        } else if (this.energy < 0) {
            this.energy = 0;
        }

        this.onEnergyChanged();
    }

    public EnergyStorage overrideEnergyCapacity(int capacity) {
        this.capacity = capacity;
        if (this.energy > capacity) {
            this.energy = capacity;
        }

        this.onEnergyChanged();
        return this;
    }

    public EnergyStorage overrideMaxTransfer(int maxTransfer) {
        this.overrideMaxReceive(maxTransfer);
        this.overrideMaxExtract(maxTransfer);
        return this;
    }

    public EnergyStorage overrideMaxReceive(int maxReceive) {
        this.maxReceive = maxReceive;
        return this;
    }

    public EnergyStorage overrideMaxExtract(int maxExtract) {
        this.maxExtract = maxExtract;
        return this;
    }

    // Serialization

    @Override
    public CompoundTag serializeNBT() {
        CompoundTag nbt = new CompoundTag();
        nbt.putInt("energy", this.energy);
        nbt.putInt("capacity", this.capacity);
        nbt.putInt("maxReceive", this.maxReceive);
        nbt.putInt("maxExtract", this.maxExtract);

        return nbt;
    }

    public void deserializeNBT(CompoundTag nbt) {
        this.energy = nbt.getInt("energy");
        this.capacity = nbt.getInt("capacity");
        this.maxReceive = nbt.getInt("maxReceive");
        this.maxExtract = nbt.getInt("maxExtract");

        this.onEnergyChanged();
    }
}