package com.example.book_be;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest
@Import(TestcontainersConfig.class)
class BookBeApplicationTests {

	@Test
	void contextLoads() {
	}

}
