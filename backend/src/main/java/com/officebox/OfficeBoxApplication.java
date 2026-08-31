package com.officebox;

import com.officebox.common.storage.StorageProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties(StorageProperties.class)
public class OfficeBoxApplication {
  public static void main(String[] args) {
    SpringApplication.run(OfficeBoxApplication.class, args);
  }
}
