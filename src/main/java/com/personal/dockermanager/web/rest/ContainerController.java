package com.personal.dockermanager.web.rest;

import com.personal.dockermanager.domain.CommandResult;
import com.personal.dockermanager.domain.ContainerInfo;
import com.personal.dockermanager.service.DockerCommandService;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/containers")
public class ContainerController {

    private final DockerCommandService dockerCommandService;

    public ContainerController(DockerCommandService dockerCommandService) {
        this.dockerCommandService = dockerCommandService;
    }

    @GetMapping
    public String index(Model model) {
        model.addAttribute("containers", getContainers());
        return "containers";
    }

    @GetMapping(value = "/api", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public List<ContainerInfo> api() {
        return getContainers();
    }

    @PostMapping(value = "/{id}/stop", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public Map<String, Object> stop(@PathVariable String id) {
        CommandResult result = dockerCommandService.stopContainer(id);
        return Map.of("success", result.success(), "message", result.outputAsString());
    }

    @PostMapping(value = "/{id}/start", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public Map<String, Object> start(@PathVariable String id) {
        CommandResult result = dockerCommandService.startContainer(id);
        return Map.of("success", result.success(), "message", result.outputAsString());
    }

    @GetMapping(value = "/{id}/state", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public Map<String, Object> state(@PathVariable String id) {
        CommandResult result = dockerCommandService.inspectState(id);
        String state = result.output().isEmpty() ? "unknown" : result.output().get(0).trim();
        return Map.of("state", state);
    }

    private List<ContainerInfo> getContainers() {
        CommandResult result = dockerCommandService.dockerPsAll();
        List<ContainerInfo> containers = new ArrayList<>();
        for (String line : result.output()) {
            if (line.isBlank()) continue;
            String[] parts = line.split("\\|", 8);
            if (parts.length < 5) continue;
            String containerId = parts[0].trim();
            String name = parts[1].trim().replaceAll("^/+", "");
            String image = parts[2].trim();
            String status = parts[3].trim();
            String state = parts[4].trim();
            String ports = parts.length > 5 ? parts[5].trim() : "";
            String runningFor = parts.length > 6 ? parts[6].trim() : "";
            String composeProject = parts.length > 7 ? parts[7].trim() : "";
            containers.add(new ContainerInfo(containerId, name, image, status, state, ports, runningFor, composeProject));
        }
        containers.sort(Comparator.comparingInt(c -> c.isRunning() ? 0 : 1));
        return containers;
    }
}
