package com.personal.dockermanager.service;

import com.personal.dockermanager.config.AppProperties;
import com.personal.dockermanager.domain.StackFile;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Service
public class ScannerService {

    private static final Logger log = LoggerFactory.getLogger(ScannerService.class);
    private static final Path EXTRA_DIRS_FILE = Path.of(System.getProperty("user.home"),
            ".docker-stack-manager", "extra-dirs.txt");
    private static final Path STACK_OWNERS_FILE = Path.of(System.getProperty("user.home"),
            ".docker-stack-manager", "stack-owners.properties");

    private final AppProperties appProperties;
    private final StackParserService stackParserService;

    private List<StackFile> cache = new ArrayList<>();

    public ScannerService(AppProperties appProperties, StackParserService stackParserService) {
        this.appProperties = appProperties;
        this.stackParserService = stackParserService;
    }

    @PostConstruct
    public void init() {
        refresh();
    }

    public List<StackFile> getAll() {
        return List.copyOf(cache);
    }

    // ── Extra dirs persistence ────────────────────────────────

    public List<String> getExtraDirs() {
        if (!Files.exists(EXTRA_DIRS_FILE)) return List.of();
        try {
            return Files.readAllLines(EXTRA_DIRS_FILE).stream()
                    .map(String::trim).filter(s -> !s.isBlank()).toList();
        } catch (IOException e) {
            return List.of();
        }
    }

    public boolean addExtraDir(String dirPath) {
        String normalized = dirPath.trim();
        if (normalized.isBlank()) return false;
        Set<String> dirs = new LinkedHashSet<>(getExtraDirs());
        if (!dirs.add(normalized)) return false; // già presente
        saveExtraDirs(dirs);
        return true;
    }

    public boolean removeExtraDir(String dirPath) {
        Set<String> dirs = new LinkedHashSet<>(getExtraDirs());
        if (!dirs.remove(dirPath.trim())) return false;
        saveExtraDirs(dirs);
        return true;
    }

    private void saveExtraDirs(Set<String> dirs) {
        try {
            Files.createDirectories(EXTRA_DIRS_FILE.getParent());
            Files.writeString(EXTRA_DIRS_FILE, String.join("\n", dirs));
        } catch (IOException e) {
            log.error("Impossibile salvare extra-dirs: {}", e.getMessage());
        }
    }

    // ── Scan ─────────────────────────────────────────────────

    public List<StackFile> refresh() {
        List<StackFile> found = new ArrayList<>();

        // Scan root principale
        Path root = Path.of(appProperties.getScanRoot());
        if (Files.exists(root)) {
            scanDir(root, root, found);
        } else {
            log.warn("Cartella di scansione non trovata: {}", root);
        }

        // Scan cartelle extra aggiunte manualmente
        for (String extra : getExtraDirs()) {
            Path extraPath = Path.of(extra);
            if (Files.exists(extraPath)) {
                // use parent as display root so top-level name is the project folder itself
                Path displayRoot = extraPath.getParent() != null ? extraPath.getParent() : extraPath;
                scanDir(extraPath, displayRoot, found);
            } else {
                log.warn("Cartella extra non trovata: {}", extra);
            }
        }

        found.sort(Comparator.comparing(StackFile::projectName));
        cache = found;
        log.info("Scansione completata: {} stack trovati", cache.size());
        return List.copyOf(cache);
    }

    private void scanDir(Path dir, Path scanRoot, List<StackFile> found) {
        try {
            Files.walkFileTree(dir, java.util.Set.of(FileVisitOption.FOLLOW_LINKS),
                    appProperties.getMaxScanDepth(),
                    new SimpleFileVisitor<>() {
                        @Override
                        public FileVisitResult preVisitDirectory(Path d, BasicFileAttributes attrs) {
                            String name = d.getFileName().toString();
                            if (name.startsWith(".") || name.equals("node_modules") || name.equals("target")) {
                                return FileVisitResult.SKIP_SUBTREE;
                            }
                            return FileVisitResult.CONTINUE;
                        }

                        @Override
                        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                            if (file.getFileName().toString().equals("docker-stack.yml")) {
                                StackFile sf = stackParserService.parse(file);
                                Path fileDir = file.getParent().normalize();
                                Path relative = scanRoot.normalize().relativize(fileDir);
                                int depth = relative.getNameCount();
                                String displayName;
                                if (depth == 0) {
                                    // docker-stack.yml direttamente in scanRoot
                                    displayName = sf.projectName();
                                } else if (depth == 1) {
                                    // scanRoot/progetto/docker-stack.yml
                                    displayName = relative.getName(0).toString();
                                } else {
                                    // scanRoot/progetto/.../cartella/docker-stack.yml
                                    // → "progetto — cartella"
                                    String topDir  = relative.getName(0).toString();
                                    String leafDir = relative.getName(depth - 1).toString();
                                    displayName = topDir.equals(leafDir) ? topDir : topDir + " — " + leafDir;
                                }
                                sf = new StackFile(sf.id(), sf.projectName(), displayName,
                                        sf.stackName(), sf.deployCommand(), sf.filePath(), sf.workingDir());
                                found.add(sf);
                                log.info("Trovato stack: {} → {}", sf.displayName(), sf.stackName());
                            }
                            return FileVisitResult.CONTINUE;
                        }

                        @Override
                        public FileVisitResult visitFileFailed(Path file, IOException exc) {
                            return FileVisitResult.CONTINUE;
                        }
                    });
        } catch (IOException e) {
            log.error("Errore scansione {}: {}", dir, e.getMessage());
        }
    }

    public Optional<StackFile> findById(String id) {
        return cache.stream().filter(s -> s.id().equals(id)).findFirst();
    }

    // ── Stack ownership ───────────────────────────────────────

    public void recordOwner(String stackName, String projectId) {
        Map<String, String> owners = loadOwners();
        owners.put(stackName, projectId);
        saveOwners(owners);
    }

    public String getOwner(String stackName) {
        return loadOwners().get(stackName);
    }

    private Map<String, String> loadOwners() {
        Map<String, String> map = new LinkedHashMap<>();
        if (!Files.exists(STACK_OWNERS_FILE)) return map;
        try {
            for (String line : Files.readAllLines(STACK_OWNERS_FILE)) {
                String trimmed = line.trim();
                if (trimmed.isBlank() || !trimmed.contains("=")) continue;
                int eq = trimmed.indexOf('=');
                map.put(trimmed.substring(0, eq).trim(), trimmed.substring(eq + 1).trim());
            }
        } catch (IOException e) {
            log.error("Impossibile leggere stack-owners: {}", e.getMessage());
        }
        return map;
    }

    private void saveOwners(Map<String, String> owners) {
        try {
            Files.createDirectories(STACK_OWNERS_FILE.getParent());
            StringBuilder sb = new StringBuilder();
            owners.forEach((k, v) -> sb.append(k).append('=').append(v).append('\n'));
            Files.writeString(STACK_OWNERS_FILE, sb.toString());
        } catch (IOException e) {
            log.error("Impossibile salvare stack-owners: {}", e.getMessage());
        }
    }
}
