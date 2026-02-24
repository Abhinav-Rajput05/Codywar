package com.gourav.CodyWar;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class CodyWarApplication {

	public static void main(String[] args) {
		SpringApplication.run(CodyWarApplication.class, args);
	}

}
