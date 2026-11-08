package com.personal.dockermanager.web.rest;

import com.personal.dockermanager.service.DockerCommandService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/docker")
public class GlobalDockerController {

    private final DockerCommandService dockerCommandService;

    public GlobalDockerController(DockerCommandService dockerCommandService) {
        this.dockerCommandService = dockerCommandService;
    }

    @GetMapping(value = "/ps", produces = MediaType.TEXT_PLAIN_VALUE)
    public String ps() {
        return dockerCommandService.dockerPs().outputAsString();
    }

    @GetMapping(value = "/images", produces = MediaType.TEXT_PLAIN_VALUE)
    public String images() {
        return dockerCommandService.dockerImages().outputAsString();
    }

    @GetMapping(value = "/stacks", produces = MediaType.TEXT_PLAIN_VALUE)
    public String stacks() {
        return dockerCommandService.dockerStackLs().outputAsString();
    }

    @GetMapping(value = "/stack/{name}/services", produces = MediaType.TEXT_PLAIN_VALUE)
    public String stackServices(@PathVariable String name) {
        return dockerCommandService.run("docker stack services " + name, null).outputAsString();
    }

    @GetMapping(value = "/stack/{name}/ps", produces = MediaType.TEXT_PLAIN_VALUE)
    public String stackPs(@PathVariable String name) {
        return dockerCommandService.run("docker stack ps " + name, null).outputAsString();
    }

    @GetMapping(value = "/stats", produces = MediaType.TEXT_PLAIN_VALUE)
    public String stats() {
        return dockerCommandService.dockerStats().outputAsString();
    }

    @PostMapping(value = "/prune", produces = MediaType.TEXT_PLAIN_VALUE)
    public String prune() {
        return dockerCommandService.dockerPrune().outputAsString();
    }
}
