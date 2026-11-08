package com.personal.dockermanager.service;

import com.personal.dockermanager.domain.CommandResult;
import com.personal.dockermanager.domain.ImageEntry;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class ImageService {

    private static final Pattern IMAGE_PATTERN = Pattern.compile("^\\s*image:\\s*(.+)$");

    private final DockerCommandService dockerCommandService;

    public ImageService(DockerCommandService dockerCommandService) {
        this.dockerCommandService = dockerCommandService;
    }

    public List<String> getLocalTags(String imageName) {
        // Rimuovi il tag dall'immagine per interrogare docker images
        String imageNameWithoutTag = imageName.contains(":") ? imageName.substring(0, imageName.lastIndexOf(':')) : imageName;

        try {
            CommandResult result = dockerCommandService.run(
                    "docker images " + imageNameWithoutTag + " --format \"{{.Tag}}\"", null);

            if (!result.success() || result.output().isEmpty()) {
                return List.of();
            }

            return result.output().stream()
                    .filter(tag -> !tag.isBlank() && !tag.equals("<none>"))
                    .toList();
        } catch (Exception e) {
            return List.of();
        }
    }

    public List<String> parseImagesFromFile(Path filePath) {
        List<String> images = new ArrayList<>();
        try {
            List<String> lines = Files.readAllLines(filePath);
            for (String line : lines) {
                Matcher matcher = IMAGE_PATTERN.matcher(line);
                if (matcher.matches()) {
                    String image = matcher.group(1).trim();
                    if (!images.contains(image)) {
                        images.add(image);
                    }
                }
            }
        } catch (IOException e) {
            // restituisce lista vuota
        }
        return images;
    }

    public List<ImageEntry> getImageTagsForFile(Path filePath) {
        List<ImageEntry> result = new ArrayList<>();
        for (String fullKey : parseImagesFromFile(filePath)) {
            String currentTag = fullKey.contains(":") ? fullKey.substring(fullKey.lastIndexOf(':') + 1) : "";
            String afterSlash = fullKey.contains("/") ? fullKey.substring(fullKey.lastIndexOf('/') + 1) : fullKey;
            String containerName = afterSlash.contains(":") ? afterSlash.substring(0, afterSlash.indexOf(':')) : afterSlash;

            List<String> localTags = getLocalTags(fullKey);
            List<String> allTags = new ArrayList<>();
            // Tag attuale nel file sempre per primo
            allTags.add(currentTag.isEmpty() ? "latest" : currentTag);
            for (String t : localTags) {
                if (!t.equals(currentTag)) allTags.add(t);
            }
            result.add(new ImageEntry(fullKey, containerName, currentTag, allTags));
        }
        return result;
    }
}
