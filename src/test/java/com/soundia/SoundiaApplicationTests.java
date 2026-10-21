package com.soundia;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

// Perfil test: usa H2 en memoria, asi el contexto carga sin PostgreSQL ni credenciales.
@SpringBootTest
@ActiveProfiles("test")
class SoundiaApplicationTests {

	@Test
	void contextLoads() {
	}

}
