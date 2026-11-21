package cl.duoc.ms_picking_service;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Smoke test: el contexto completo arranca y crea el índice único de ordenId.
 * Requiere MongoDB disponible en MONGODB_URI (por defecto mongodb://localhost:27017).
 * En CI se levanta un servicio mongo:7 en el job de tests.
 */
@SpringBootTest
class MsPickingServiceApplicationTests {

	@Test
	void contextLoads() {
	}

}
