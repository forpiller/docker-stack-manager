package com.personal.dockermanager.domain;

public record ServiceInfo(
        String id,
        String name,
        String image,
        String replicas,
        String ports
) {
    public String shortName() {
        // acc_uaa → uaa, cs-questions_gateway → gateway
        int idx = name.indexOf('_');
        return idx >= 0 ? name.substring(idx + 1) : name;
    }
}
