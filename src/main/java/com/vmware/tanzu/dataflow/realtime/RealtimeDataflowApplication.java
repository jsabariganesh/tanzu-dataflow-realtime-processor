package com.vmware.tanzu.dataflow.realtime;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class RealtimeDataflowApplication {

    public static void main(String[] args) {
        SpringApplication.run(RealtimeDataflowApplication.class, args);
    }
}
