package com.gymos;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class GymOsApplication {

	public static void main(String[] args) {
		SpringApplication.run(GymOsApplication.class, args);
	}

}
