package com.personal.dockermanager.service;

import com.personal.dockermanager.domain.StackFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class StackParserServiceTest {

    private final StackParserService parser = new StackParserService();

    @Test
    void extractsCommandAndStackNameFromComment(@TempDir Path tempDir) throws IOException {
        Path stackFile = writeStackFile(tempDir, """
                # docker stack deploy -c docker-stack.yml --with-registry-auth --resolve-image never miostack
                version: "3.8"
                services:
                  app:
                    image: myapp:latest
                """);

        StackFile result = parser.parse(stackFile);

        assertThat(result.stackName()).isEqualTo("miostack");
        assertThat(result.deployCommand())
                .isEqualTo("docker stack deploy -c docker-stack.yml --with-registry-auth --resolve-image never miostack");
        assertThat(result.hasDeployCommand()).isTrue();
    }

    @Test
    void extractsCommandWhenPrefixedByEnvLoad(@TempDir Path tempDir) throws IOException {
        Path stackFile = writeStackFile(tempDir, """
                # source .env.local && docker stack deploy -c docker-stack.yml --with-registry-auth altrostack
                version: "3.8"
                """);

        StackFile result = parser.parse(stackFile);

        assertThat(result.stackName()).isEqualTo("altrostack");
        assertThat(result.deployCommand()).startsWith("source .env.local &&");
    }

    @Test
    void fallsBackToUnknownWhenNoDeployCommentFound(@TempDir Path tempDir) throws IOException {
        Path stackFile = writeStackFile(tempDir, """
                version: "3.8"
                services:
                  app:
                    image: myapp:latest
                """);

        StackFile result = parser.parse(stackFile);

        assertThat(result.stackName()).isEqualTo("unknown");
        assertThat(result.deployCommand()).isNull();
        assertThat(result.hasDeployCommand()).isFalse();
    }

    @Test
    void usesParentFolderNameAsProjectName(@TempDir Path tempDir) throws IOException {
        Path projectDir = Files.createDirectory(tempDir.resolve("my-project"));
        Path stackFile = projectDir.resolve("docker-stack.yml");
        Files.writeString(stackFile, "# docker stack deploy -c docker-stack.yml mystack\n");

        StackFile result = parser.parse(stackFile);

        assertThat(result.projectName()).isEqualTo("my-project");
        assertThat(result.workingDir()).isEqualTo(projectDir);
    }

    private Path writeStackFile(Path dir, String content) throws IOException {
        Path file = dir.resolve("docker-stack.yml");
        Files.writeString(file, content);
        return file;
    }
}
