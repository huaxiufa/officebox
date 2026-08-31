package com.officebox;

import com.officebox.common.storage.StorageProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(StorageProperties.class)
public class OfficeBoxApplication {
  public static void main(String[] args) {
    SpringApplication.run(OfficeBoxApplication.class, args);
  }
}
