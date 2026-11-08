package com.personal.dockermanager.domain;

import java.nio.file.Path;

public record StackFile(
        String id,
        String projectName,
        String displayName,
        String stackName,
        String deployCommand,
        Path filePath,
        Path workingDir
) {
    public boolean hasDeployCommand() {
        return deployCommand != null && !deployCommand.isBlank();
    }
}
