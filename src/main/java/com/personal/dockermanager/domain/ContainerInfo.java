package com.personal.dockermanager.domain;

public record ContainerInfo(
        String id,
        String name,
        String image,
        String status,
        String state,
        String ports,
        String runningFor,
        String composeProject
) {
    public boolean isRunning() {
        return "running".equalsIgnoreCase(state);
    }

    public boolean isStoppable() {
        return "running".equalsIgnoreCase(state)
                || "paused".equalsIgnoreCase(state)
                || "restarting".equalsIgnoreCase(state);
    }

    public boolean isStartable() {
        return "exited".equalsIgnoreCase(state)
                || "created".equalsIgnoreCase(state)
                || "dead".equalsIgnoreCase(state);
    }
}
