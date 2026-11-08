package com.personal.dockermanager.domain;

import java.util.List;

public record CommandResult(
        int exitCode,
        List<String> output,
        boolean success
) {
    public String outputAsString() {
        return String.join("\n", output);
    }
}
