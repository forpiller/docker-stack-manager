package com.personal.dockermanager.web.rest;

import com.personal.dockermanager.domain.CommandResult;
import com.personal.dockermanager.domain.StackFile;
import com.personal.dockermanager.service.DockerCommandService;
import com.personal.dockermanager.service.ScannerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api")
@Tag(name = "Docker Stack Manager", description = "Manage Docker Swarm stacks and services")
public class ApiController {

    private final ScannerService scannerService;
    private final DockerCommandService dockerCommandService;

    public ApiController(ScannerService scannerService, DockerCommandService dockerCommandService) {
        this.scannerService = scannerService;
        this.dockerCommandService = dockerCommandService;
    }

    @GetMapping("/stacks")
    @Operation(summary = "List all discovered stacks")
    public List<Map<String, Object>> listStacks() {
        return scannerService.getAll().stream().map(s -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", s.id());
            m.put("displayName", s.displayName());
            m.put("stackName", s.stackName());
            m.put("filePath", s.filePath().toString());
            m.put("hasDeployCommand", s.hasDeployCommand());
            m.put("deployCommand", s.deployCommand());
            return m;
        }).toList();
    }

    @GetMapping("/stacks/{id}/status")
    @Operation(summary = "Get status of a stack (docker stack services output)")
    public ResponseEntity<Map<String, Object>> stackStatus(@PathVariable String id) {
        Optional<StackFile> opt = scannerService.findById(id);
        if (opt.isEmpty()) return ResponseEntity.notFound().build();
        CommandResult result = dockerCommandService.run(
                "docker stack services " + opt.get().stackName(), opt.get().workingDir());
        return ResponseEntity.ok(toMap(result));
    }

    @PostMapping("/stacks/{id}/deploy")
    @Operation(summary = "Deploy a stack")
    public ResponseEntity<Map<String, Object>> deploy(@PathVariable String id) {
        Optional<StackFile> opt = scannerService.findById(id);
        if (opt.isEmpty()) return ResponseEntity.notFound().build();
        StackFile stack = opt.get();
        if (!stack.hasDeployCommand()) {
            return ResponseEntity.badRequest().body(Map.of("error", "No deploy command defined"));
        }
        CommandResult result = dockerCommandService.run(stack.deployCommand(), stack.workingDir());
        scannerService.refresh();
        return ResponseEntity.ok(toMap(result));
    }

    @PostMapping("/stacks/{id}/remove")
    @Operation(summary = "Remove a stack")
    public ResponseEntity<Map<String, Object>> remove(@PathVariable String id) {
        Optional<StackFile> opt = scannerService.findById(id);
        if (opt.isEmpty()) return ResponseEntity.notFound().build();
        CommandResult result = dockerCommandService.run(
                "docker stack rm " + opt.get().stackName(), opt.get().workingDir());
        return ResponseEntity.ok(toMap(result));
    }

    @GetMapping("/stacks/{id}/services")
    @Operation(summary = "List services of a stack")
    public ResponseEntity<Map<String, Object>> listServices(@PathVariable String id) {
        Optional<StackFile> opt = scannerService.findById(id);
        if (opt.isEmpty()) return ResponseEntity.notFound().build();
        CommandResult result = dockerCommandService.run(
                "docker stack services " + opt.get().stackName(), null);
        return ResponseEntity.ok(toMap(result));
    }

    @PostMapping("/stacks/{id}/services/{serviceName}/restart")
    @Operation(summary = "Restart a specific service by forcing an update")
    public ResponseEntity<Map<String, Object>> restart(
            @PathVariable String id, @PathVariable String serviceName) {
        Optional<StackFile> opt = scannerService.findById(id);
        if (opt.isEmpty()) return ResponseEntity.notFound().build();
        CommandResult result = dockerCommandService.run(
                "docker service update --force " + serviceName, null);
        return ResponseEntity.ok(toMap(result));
    }

    @GetMapping("/docker/stacks")
    @Operation(summary = "Run docker stack ls")
    public Map<String, Object> dockerStackLs() {
        return toMap(dockerCommandService.run("docker stack ls", null));
    }

    @GetMapping("/docker/ps")
    @Operation(summary = "Run docker ps")
    public Map<String, Object> dockerPs() {
        return toMap(dockerCommandService.run("docker ps", null));
    }

    private Map<String, Object> toMap(CommandResult r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("success", r.success());
        m.put("exitCode", r.exitCode());
        m.put("output", r.outputAsString());
        return m;
    }
}
