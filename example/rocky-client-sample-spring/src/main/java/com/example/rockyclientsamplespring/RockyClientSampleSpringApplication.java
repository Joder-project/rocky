package com.example.rockyclientsamplespring;

import org.alps.rocky.client.spring.EnabledRockyClient;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@EnabledRockyClient
public class RockyClientSampleSpringApplication {

	public static void main(String[] args) {
		SpringApplication.run(RockyClientSampleSpringApplication.class, args);
	}

}
