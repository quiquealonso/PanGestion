package com.pangestion;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
		"spring.datasource.url=jdbc:h2:mem:pangestion-test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
		// Las imagenes de los datos de ejemplo van a una carpeta temporal, no a la real
		"app.uploads.dir=target/test-uploads"
})
class PangestionApiApplicationTests {

	@Test
	void contextLoads() {
	}

}
