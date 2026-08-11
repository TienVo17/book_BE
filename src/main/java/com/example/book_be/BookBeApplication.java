package com.example.book_be;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class BookBeApplication {

	public static void main(String[] args) {
		SpringApplication.run(BookBeApplication.class, args);
	}

}
