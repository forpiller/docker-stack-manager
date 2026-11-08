package com.personal.dockermanager.service;

import com.personal.dockermanager.domain.StackFile;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

@Service
public class StackParserService {

    public StackFile parse(Path filePath) {
        String deployCommand = null;
        String stackName = "unknown";

        try {
            List<String> firstLines = Files.lines(filePath).limit(10).toList();
            for (String line : firstLines) {
                String trimmed = line.trim();
                if (trimmed.startsWith("#") && trimmed.contains("docker stack deploy")) {
                    deployCommand = trimmed.substring(1).trim();
                    String[] tokens = deployCommand.split("\\s+");
                    stackName = tokens[tokens.length - 1];
                    break;
                }
            }
        } catch (IOException e) {
            // se il file non è leggibile, restituiamo un StackFile con valori di default
        }

        String projectName = resolveProjectName(filePath);
        String id = computeId(filePath.toAbsolutePath().toString());
        Path workingDir = filePath.getParent();

        return new StackFile(id, projectName, projectName, stackName, deployCommand, filePath, workingDir);
    }

    private String resolveProjectName(Path filePath) {
        // Risale fino a trovare un nome significativo (non "src", "docker", ecc.)
        Path parent = filePath.getParent();
        if (parent != null) {
            return parent.getFileName().toString();
        }
        return "unknown";
    }

    private String computeId(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes());
            return HexFormat.of().formatHex(hash).substring(0, 8);
        } catch (NoSuchAlgorithmException e) {
            return Integer.toHexString(input.hashCode());
        }
    }
}
