package com.personal.dockermanager;

import com.personal.dockermanager.config.AppProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(AppProperties.class)
public class DockerStackManagerApplication {

    public static void main(String[] args) {
        SpringApplication.run(DockerStackManagerApplication.class, args);
    }
}
