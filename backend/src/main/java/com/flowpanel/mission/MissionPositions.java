package com.flowpanel.mission;

/** Positions filled vs requested for a mission. Implemented by the sourcing module. */
public interface MissionPositions {

    record Positions(int filled, Integer total) {
    }

    Positions positions(Mission mission);
}
