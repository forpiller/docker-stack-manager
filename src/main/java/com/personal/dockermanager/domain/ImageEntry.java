package com.personal.dockermanager.domain;

import java.util.List;

public record ImageEntry(
        String fullKey,
        String containerName,
        String currentTag,
        List<String> tags
) {}
