package cl.duoc.ms_picking_service.config;

import cl.duoc.ms_picking_service.model.OrdenPicking;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.mongodb.core.index.IndexOperations;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.IndexDefinition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MongoIndexConfigTest {

    @Test
    void creaIndiceUnicoSobreOrdenIdAlInicializar() throws Exception {
        MongoTemplate template = mock(MongoTemplate.class);
        IndexOperations indexOps = mock(IndexOperations.class);
        when(template.indexOps(OrdenPicking.class)).thenReturn(indexOps);

        new MongoIndexConfig().ordenPickingIndexes(template).afterPropertiesSet();

        ArgumentCaptor<IndexDefinition> captor = ArgumentCaptor.forClass(IndexDefinition.class);
        verify(indexOps).ensureIndex(captor.capture());
        assertThat(captor.getValue().getIndexOptions().getBoolean("unique")).isTrue();
        assertThat(captor.getValue().getIndexKeys().get("ordenId")).isEqualTo(1);
    }
}
