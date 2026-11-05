package com.fashion.Riskyc;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class RiskycApplication {

	public static void main(String[] args) {
		SpringApplication.run(RiskycApplication.class, args);
	}

}
