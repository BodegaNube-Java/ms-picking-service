package cl.duoc.ms_picking_service.config;

import cl.duoc.ms_picking_service.model.OrdenPicking;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;

/**
 * Crea explícitamente el índice único de ordenId al arrancar.
 *
 * La creación automática de índices de Spring Data no está activa por defecto en Spring Boot 4,
 * y sin este índice dos peticiones simultáneas podían registrar la misma orden dos veces.
 * InitializingBean corre antes de que el servidor HTTP acepte tráfico.
 */
@Configuration
public class MongoIndexConfig {

    @Bean
    public InitializingBean ordenPickingIndexes(MongoTemplate mongoTemplate) {
        return () -> mongoTemplate.indexOps(OrdenPicking.class)
                .ensureIndex(new Index().on("ordenId", Sort.Direction.ASC).unique());
    }
}
