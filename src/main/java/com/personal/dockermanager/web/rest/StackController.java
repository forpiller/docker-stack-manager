package com.personal.dockermanager.web.rest;

import com.personal.dockermanager.domain.CommandResult;
import com.personal.dockermanager.domain.ServiceInfo;
import com.personal.dockermanager.domain.StackFile;
import com.personal.dockermanager.service.DockerCommandService;
import com.personal.dockermanager.service.ImageService;
import com.personal.dockermanager.service.ScannerService;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Controller
public class StackController {

    private final ScannerService scannerService;
    private final DockerCommandService dockerCommandService;
    private final ImageService imageService;

    public StackController(ScannerService scannerService,
                           DockerCommandService dockerCommandService,
                           ImageService imageService) {
        this.scannerService = scannerService;
        this.dockerCommandService = dockerCommandService;
        this.imageService = imageService;
    }

    @GetMapping("/")
    public String index(Model model) {
        List<StackFile> stacks = scannerService.getAll();
        Set<String> activeStackNames = getActiveStackNames();
        model.addAttribute("stacks", stacks);
        model.addAttribute("activeStacks", activeStackNames);
        model.addAttribute("activeProjectIds", computeActiveProjectIds(stacks, activeStackNames));
        return "index";
    }

    private Set<String> computeActiveProjectIds(List<StackFile> stacks, Set<String> activeStackNames) {
        Set<String> result = new HashSet<>();
        for (StackFile sf : stacks) {
            if (!activeStackNames.contains(sf.stackName())) continue;
            String owner = scannerService.getOwner(sf.stackName());
            if (owner == null || owner.equals(sf.id())) {
                result.add(sf.id());
            }
        }
        return result;
    }

    private Set<String> getActiveStackNames() {
        Set<String> active = new HashSet<>();
        CommandResult result = dockerCommandService.dockerStackLs();
        // Output: "NAME   SERVICES\ncs-questions   5\nacc   3\n..."
        for (int i = 1; i < result.output().size(); i++) {
            String line = result.output().get(i).trim();
            if (!line.isEmpty()) {
                active.add(line.split("\\s+")[0]);
            }
        }
        return active;
    }

    @GetMapping("/refresh")
    public String refresh() {
        scannerService.refresh();
        return "redirect:/";
    }

    @PostMapping("/stacks/{id}/deploy")
    public String deploy(@PathVariable String id, RedirectAttributes redirectAttributes) {
        Optional<StackFile> stackOpt = scannerService.findById(id);
        if (stackOpt.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Stack non trovato: " + id);
            return "redirect:/";
        }
        StackFile stack = stackOpt.get();
        if (!stack.hasDeployCommand()) {
            redirectAttributes.addFlashAttribute("error", "Nessun comando deploy trovato nel file: " + stack.filePath());
            return "redirect:/";
        }
        CommandResult result = dockerCommandService.deploy(stack);
        if (result.success()) {
            redirectAttributes.addFlashAttribute("message", "Deploy avviato: " + stack.stackName() + "\n" + result.outputAsString());
        } else {
            redirectAttributes.addFlashAttribute("error", "Errore deploy " + stack.stackName() + ":\n" + result.outputAsString());
        }
        return "redirect:/";
    }

    @PostMapping("/stacks/{id}/remove")
    public String remove(@PathVariable String id, RedirectAttributes redirectAttributes) {
        Optional<StackFile> stackOpt = scannerService.findById(id);
        if (stackOpt.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Stack non trovato: " + id);
            return "redirect:/";
        }
        StackFile stack = stackOpt.get();
        CommandResult result = dockerCommandService.remove(stack.stackName());
        if (result.success()) {
            redirectAttributes.addFlashAttribute("message", "Stack rimosso: " + stack.stackName());
        } else {
            redirectAttributes.addFlashAttribute("error", "Errore rimozione " + stack.stackName() + ":\n" + result.outputAsString());
        }
        return "redirect:/";
    }

    @GetMapping(value = "/stacks/{id}/status", produces = MediaType.TEXT_PLAIN_VALUE)
    @ResponseBody
    public String status(@PathVariable String id) {
        return scannerService.findById(id)
                .map(stack -> dockerCommandService.status(stack.stackName()).outputAsString())
                .orElse("Stack non trovato");
    }

    @GetMapping(value = "/stacks/{id}/services-list", produces = MediaType.TEXT_PLAIN_VALUE)
    @ResponseBody
    public String servicesList(@PathVariable String id) {
        return scannerService.findById(id)
                .map(stack -> dockerCommandService.services(stack.stackName()).outputAsString())
                .orElse("Stack non trovato");
    }

    @GetMapping("/stacks/{id}/logs")
    public String logsPage(@PathVariable String id, Model model) {
        Optional<StackFile> stackOpt = scannerService.findById(id);
        if (stackOpt.isEmpty()) {
            return "redirect:/";
        }
        StackFile stack = stackOpt.get();
        CommandResult result = dockerCommandService.services(stack.stackName());
        List<ServiceInfo> services = parseServices(result);
        model.addAttribute("stack", stack);
        model.addAttribute("services", services);
        return "logs";
    }

    @GetMapping(value = "/stacks/{id}/services/{serviceName}/logs", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamLogs(@PathVariable String id, @PathVariable String serviceName) {
        SseEmitter emitter = new SseEmitter(0L);
        try {
            Process process = dockerCommandService.startLogProcess(serviceName);
            ExecutorService exec = Executors.newSingleThreadExecutor();
            exec.submit(() -> {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        emitter.send(line);
                    }
                    emitter.complete();
                } catch (Exception e) {
                    emitter.completeWithError(e);
                }
            });
            emitter.onCompletion(() -> { process.destroy(); exec.shutdown(); });
            emitter.onTimeout(() -> { process.destroy(); exec.shutdown(); });
        } catch (IOException e) {
            emitter.completeWithError(e);
        }
        return emitter;
    }

    @GetMapping("/stacks/{id}/edit")
    public String editPage(@PathVariable String id, Model model) {
        Optional<StackFile> stackOpt = scannerService.findById(id);
        if (stackOpt.isEmpty()) {
            return "redirect:/";
        }
        StackFile stack = stackOpt.get();
        try {
            String fileContent = Files.readString(stack.filePath());
            model.addAttribute("stack", stack);
            model.addAttribute("fileContent", fileContent);
            model.addAttribute("imageTags", imageService.getImageTagsForFile(stack.filePath()));
        } catch (IOException e) {
            model.addAttribute("error", "Impossibile leggere il file: " + e.getMessage());
        }
        return "editor";
    }

    @PostMapping("/stacks/{id}/save")
    public String saveFile(@PathVariable String id,
                           @RequestParam String content,
                           RedirectAttributes redirectAttributes) {
        Optional<StackFile> stackOpt = scannerService.findById(id);
        if (stackOpt.isEmpty()) {
            return "redirect:/";
        }
        StackFile stack = stackOpt.get();
        try {
            Files.writeString(stack.filePath(), content);
            redirectAttributes.addFlashAttribute("message", "File salvato correttamente");
        } catch (IOException e) {
            redirectAttributes.addFlashAttribute("error", "Errore salvataggio: " + e.getMessage());
        }
        return "redirect:/stacks/" + id + "/edit";
    }

    @PostMapping("/stacks/{id}/save-and-deploy")
    public String saveAndDeploy(@PathVariable String id,
                                @RequestParam String content,
                                RedirectAttributes redirectAttributes) {
        Optional<StackFile> stackOpt = scannerService.findById(id);
        if (stackOpt.isEmpty()) {
            return "redirect:/";
        }
        StackFile stack = stackOpt.get();
        try {
            Files.writeString(stack.filePath(), content);
            // Rilegge il file aggiornato per ottenere il comando aggiornato
            scannerService.refresh();
            Optional<StackFile> updatedStack = scannerService.findById(id);
            if (updatedStack.isPresent() && updatedStack.get().hasDeployCommand()) {
                CommandResult result = dockerCommandService.deploy(updatedStack.get());
                if (result.success()) {
                    redirectAttributes.addFlashAttribute("message", "File salvato e deploy avviato:\n" + result.outputAsString());
                } else {
                    redirectAttributes.addFlashAttribute("error", "File salvato ma deploy fallito:\n" + result.outputAsString());
                }
            }
        } catch (IOException e) {
            redirectAttributes.addFlashAttribute("error", "Errore: " + e.getMessage());
        }
        return "redirect:/";
    }

    @GetMapping(value = "/stacks/{id}/services/{serviceName}/logs/history", produces = MediaType.TEXT_PLAIN_VALUE)
    @ResponseBody
    public String logHistory(@PathVariable String id,
                             @PathVariable String serviceName,
                             @RequestParam(defaultValue = "300") int lines) {
        CommandResult result = dockerCommandService.run(
                "docker service logs --no-trunc --tail " + lines + " " + serviceName, null);
        return result.outputAsString();
    }

    @GetMapping(value = "/stacks/{id}/services/{serviceName}/ps", produces = MediaType.TEXT_PLAIN_VALUE)
    @ResponseBody
    public String servicePs(@PathVariable String id, @PathVariable String serviceName) {
        return dockerCommandService.run("docker service ps " + serviceName + " --no-trunc", null).outputAsString();
    }

    @PostMapping(value = "/stacks/{id}/services/{serviceName}/scale", produces = MediaType.TEXT_PLAIN_VALUE)
    @ResponseBody
    public String scaleService(@PathVariable String id,
                               @PathVariable String serviceName,
                               @RequestParam int replicas) {
        CommandResult result = dockerCommandService.scale(serviceName, replicas);
        return result.success() ? "ok" : result.output().stream()
                .map(String::trim).filter(l -> !l.isBlank())
                .reduce((a, b) -> b).orElse("errore");
    }

    @PostMapping("/stacks/{id}/services/{serviceName}/restart")
    @ResponseBody
    public String restartService(@PathVariable String id, @PathVariable String serviceName) {
        CommandResult result = dockerCommandService.run(
                "docker service update --force " + serviceName,
                null);
        if (result.success()) return "ok";
        // Return only the last meaningful line to avoid flooding the log with progress lines
        return result.output().stream()
                .map(String::trim)
                .filter(l -> !l.isBlank() && !l.startsWith("overall progress") && !l.matches("\\d+/\\d+:"))
                .reduce((a, b) -> b)
                .orElse(result.outputAsString());
    }

    @PostMapping("/stacks/{id}/scale")
    public String scale(@PathVariable String id,
                        @RequestParam String serviceName,
                        @RequestParam int replicas,
                        RedirectAttributes redirectAttributes) {
        CommandResult result = dockerCommandService.scale(serviceName, replicas);
        if (result.success()) {
            redirectAttributes.addFlashAttribute("message", "Scale eseguito: " + serviceName + "=" + replicas);
        } else {
            redirectAttributes.addFlashAttribute("error", "Errore scale:\n" + result.outputAsString());
        }
        return "redirect:/";
    }

    @PostMapping("/stacks/{id}/deploy-command")
    @ResponseBody
    public Map<String, Object> updateDeployCommand(@PathVariable String id,
                                                    @RequestParam String command) {
        Map<String, Object> response = new LinkedHashMap<>();
        Optional<StackFile> opt = scannerService.findById(id);
        if (opt.isEmpty()) {
            response.put("success", false);
            response.put("error", "Stack non trovato");
            return response;
        }
        try {
            List<String> lines = Files.readAllLines(opt.get().filePath());
            boolean replaced = false;
            for (int i = 0; i < Math.min(lines.size(), 15); i++) {
                String trimmed = lines.get(i).trim();
                if (trimmed.startsWith("#") && trimmed.contains("docker stack deploy")) {
                    lines.set(i, "# " + command);
                    replaced = true;
                    break;
                }
            }
            if (!replaced) {
                lines.add(0, "# " + command);
            }
            Files.write(opt.get().filePath(), lines);
            scannerService.refresh();
            response.put("success", true);
        } catch (IOException e) {
            response.put("success", false);
            response.put("error", e.getMessage());
        }
        return response;
    }

    @GetMapping("/stacks/pick-folder")
    @ResponseBody
    public Map<String, Object> pickFolder() {
        try {
            ProcessBuilder pb = new ProcessBuilder("bash", "-c",
                    "osascript -e 'POSIX path of (choose folder with prompt \"Seleziona cartella del progetto\")'");
            pb.redirectErrorStream(true);
            Process p = pb.start();
            String out = new String(p.getInputStream().readAllBytes()).trim();
            int exit = p.waitFor();
            if (exit != 0 || out.isBlank()) return Map.of("cancelled", true);
            // rimuovi slash finale
            String path = out.endsWith("/") ? out.substring(0, out.length() - 1) : out;
            return Map.of("path", path);
        } catch (Exception e) {
            return Map.of("error", e.getMessage());
        }
    }

    @PostMapping("/stacks/add-project")
    @ResponseBody
    public Map<String, Object> addProject(@RequestParam String folderPath) {
        String path = folderPath.trim();
        if (path.isBlank()) return Map.of("success", false, "message", "Percorso vuoto");
        java.nio.file.Path dir = java.nio.file.Path.of(path);
        if (!java.nio.file.Files.isDirectory(dir)) {
            return Map.of("success", false, "message", "Cartella non trovata: " + path);
        }
        // cerca docker-stack.yml nella cartella (non ricorsivo — è un progetto singolo)
        boolean hasStack = java.nio.file.Files.exists(dir.resolve("docker-stack.yml"));
        if (!hasStack) {
            // cerca un livello sotto
            try (java.util.stream.Stream<java.nio.file.Path> sub = java.nio.file.Files.list(dir)) {
                hasStack = sub.anyMatch(p -> java.nio.file.Files.exists(p.resolve("docker-stack.yml")));
            } catch (java.io.IOException ignored) { }
        }
        if (!hasStack) {
            return Map.of("success", false, "message", "Nessun docker-stack.yml trovato in: " + path);
        }
        boolean added = scannerService.addExtraDir(path);
        List<StackFile> refreshed = scannerService.refresh();
        long count = refreshed.stream()
                .filter(s -> s.filePath().startsWith(dir))
                .count();
        if (added) {
            return Map.of("success", true, "message", "Aggiunti " + count + " stack da: " + path);
        } else {
            return Map.of("success", true, "alreadyPresent", true,
                    "message", "Cartella già presente (" + count + " stack)");
        }
    }

    @PostMapping("/stacks/{id}/deploy-action")
    @ResponseBody
    public Map<String, Object> deployAction(@PathVariable String id) {
        Optional<StackFile> stackOpt = scannerService.findById(id);
        if (stackOpt.isEmpty()) return Map.of("success", false, "message", "Stack non trovato");
        StackFile stack = stackOpt.get();
        if (!stack.hasDeployCommand()) return Map.of("success", false, "message", "Nessun comando deploy");
        CommandResult result = dockerCommandService.deploy(stack);
        if (result.success()) {
            scannerService.recordOwner(stack.stackName(), id);
        }
        return Map.of("success", result.success(), "message", result.outputAsString(), "stackName", stack.stackName());
    }

    @PostMapping("/stacks/{id}/remove-action")
    @ResponseBody
    public Map<String, Object> removeAction(@PathVariable String id) {
        Optional<StackFile> stackOpt = scannerService.findById(id);
        if (stackOpt.isEmpty()) return Map.of("success", false, "message", "Stack non trovato");
        StackFile stack = stackOpt.get();
        CommandResult result = dockerCommandService.remove(stack.stackName());
        return Map.of("success", result.success(), "message", result.outputAsString(), "stackName", stack.stackName());
    }

    @GetMapping(value = "/stacks/{id}/active-status", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public Map<String, Object> activeStatus(@PathVariable String id) {
        Optional<StackFile> stackOpt = scannerService.findById(id);
        if (stackOpt.isEmpty()) return Map.of("active", false, "allRunning", false, "hasError", false, "errorText", "");
        String stackName = stackOpt.get().stackName();
        boolean isActive = getActiveStackNames().contains(stackName);
        if (!isActive) return Map.of("active", false, "allRunning", false, "hasError", false, "errorText", "");
        // If another project last deployed this stack name, report as inactive for this card
        String owner = scannerService.getOwner(stackName);
        if (owner != null && !owner.equals(id)) {
            return Map.of("active", false, "allRunning", false, "hasError", false, "errorText", "");
        }

        // Use structured format to distinguish transient failures (desired=Shutdown) from real ones (desired=Running)
        List<String> formatted = dockerCommandService.statusFormatted(stackName).output();
        boolean hasRealError = false;
        boolean hasStarting = false;
        int runningCount = 0;
        int totalTasks = 0;
        for (String line : formatted) {
            String[] parts = line.split("\\|", 2);
            if (parts.length < 2) continue;
            String desired = parts[0].trim().toLowerCase();
            String current = parts[1].trim().toLowerCase();
            if (desired.equals("running")) {
                totalTasks++;
                if (current.startsWith("running")) runningCount++;
                else if (current.startsWith("failed") || current.startsWith("rejected")) hasRealError = true;
                else hasStarting = true; // preparing, starting, pending, etc.
            }
            // desired=shutdown → old task being replaced, ignore
        }
        boolean allRunning = !hasRealError && !hasStarting && totalTasks > 0 && runningCount == totalTasks;
        return Map.of("active", true, "allRunning", allRunning, "hasError", hasRealError, "errorText", "");
    }

    private List<ServiceInfo> parseServices(CommandResult result) {
        // Output: ID   NAME   MODE   REPLICAS   IMAGE   PORTS
        // parts[0]=ID, parts[1]=NAME, parts[2]=MODE, parts[3]=REPLICAS, parts[4]=IMAGE, parts[5]=PORTS
        List<ServiceInfo> services = new ArrayList<>();
        List<String> lines = result.output();
        for (int i = 1; i < lines.size(); i++) {
            String line = lines.get(i).trim();
            if (line.isEmpty()) continue;
            String[] parts = line.split("\\s{2,}");
            if (parts.length >= 2) {
                String id = parts[0].trim();
                String name = parts[1].trim();
                String image = parts.length > 4 ? parts[4].trim() : "";
                String replicas = parts.length > 3 ? parts[3].trim() : "";
                String ports = parts.length > 5 ? parts[5].trim() : "";
                services.add(new ServiceInfo(id, name, image, replicas, ports));
            }
        }
        return services;
    }
}
