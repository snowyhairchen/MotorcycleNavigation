package com.example.motorcyclenavigation;

import com.google.android.gms.maps.model.LatLng;

public class Instruction {
    private String instruction;
    private LatLng turnLocation;

    public Instruction(String instruction, LatLng turnLocation) {
        this.instruction = instruction;
        this.turnLocation = turnLocation;
    }

    public String getInstruction() {
        return instruction;
    }

    public LatLng getTurnLocation() {
        return turnLocation;
    }

    public void setInstruction(String instruction) {
        this.instruction = instruction;
    }

    public void setTurnLocation(LatLng turnLocation) {
        this.turnLocation = turnLocation;
    }

    public String toString() {
        return String.format("%s, %s", instruction, turnLocation.toString());
    }
}
