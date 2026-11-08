package com.personal.dockermanager.service;

import com.personal.dockermanager.config.AppProperties;
import com.personal.dockermanager.config.OsType;
import com.personal.dockermanager.domain.CommandResult;
import com.personal.dockermanager.domain.StackFile;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Service
public class DockerCommandService {

    private static final int MAX_OUTPUT_LINES = 1000;

    private final AppProperties appProperties;

    public DockerCommandService(AppProperties appProperties) {
        this.appProperties = appProperties;
    }

    public CommandResult run(String command, Path workingDir) {
        try {
            ProcessBuilder pb = buildProcess(command);
            if (workingDir != null) {
                pb.directory(workingDir.toFile());
            }
            pb.redirectErrorStream(true);

            Process process = pb.start();
            List<String> lines = new ArrayList<>();

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    lines.add(line);
                    if (lines.size() >= MAX_OUTPUT_LINES) {
                        lines.add("... output troncato a " + MAX_OUTPUT_LINES + " righe ...");
                        break;
                    }
                }
            }

            int exitCode = process.waitFor();
            return new CommandResult(exitCode, lines, exitCode == 0);

        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            return new CommandResult(-1, List.of("Errore: " + e.getMessage()), false);
        }
    }

    private ProcessBuilder buildProcess(String command) {
        OsType os = appProperties.getOs();
        return switch (os) {
            case WINDOWS -> new ProcessBuilder("cmd", "/c", command);
            default -> new ProcessBuilder("bash", "-c", command);
        };
    }

    public CommandResult deploy(StackFile stack) {
        return run(stack.deployCommand(), stack.workingDir());
    }

    public CommandResult remove(String stackName) {
        return run("docker stack rm " + stackName, null);
    }

    public CommandResult status(String stackName) {
        return run("docker stack ps " + stackName + " --no-trunc", null);
    }

    /** Returns lines of "desiredState|currentState" for each task, to detect real vs transient failures. */
    public CommandResult statusFormatted(String stackName) {
        return run("docker stack ps " + stackName + " --format '{{.DesiredState}}|{{.CurrentState}}' --no-trunc", null);
    }

    public CommandResult services(String stackName) {
        return run("docker stack services " + stackName, null);
    }

    public CommandResult scale(String serviceName, int replicas) {
        return run("docker service scale " + serviceName + "=" + replicas, null);
    }

    public CommandResult dockerPs() {
        return run("docker ps --format 'table {{.ID}}\\t{{.Image}}\\t{{.Status}}\\t{{.Names}}'", null);
    }

    public CommandResult dockerImages() {
        return run("docker images --format 'table {{.Repository}}\\t{{.Tag}}\\t{{.ID}}\\t{{.Size}}'", null);
    }

    public CommandResult dockerStats() {
        return run("docker stats --no-stream --format 'table {{.Name}}\\t{{.CPUPerc}}\\t{{.MemUsage}}'", null);
    }

    public CommandResult dockerStackLs() {
        return run("docker stack ls", null);
    }

    public CommandResult dockerPrune() {
        return run("docker system prune -f", null);
    }

    public CommandResult dockerPsAll() {
        return run("docker ps -a --format '{{.ID}}|{{.Names}}|{{.Image}}|{{.Status}}|{{.State}}|{{.Ports}}|{{.RunningFor}}|{{.Label \"com.docker.compose.project\"}}'", null);
    }

    public CommandResult stopContainer(String containerId) {
        return run("docker stop " + containerId, null);
    }

    public CommandResult startContainer(String containerId) {
        return run("docker start " + containerId, null);
    }

    public CommandResult inspectState(String containerId) {
        return run("docker inspect --format '{{.State.Status}}' " + containerId, null);
    }

    public Process startLogProcess(String serviceName) throws IOException {
        ProcessBuilder pb = new ProcessBuilder("bash", "-c",
                "docker service logs --follow --tail 30 " + serviceName);
        pb.redirectErrorStream(true);
        return pb.start();
    }
}
