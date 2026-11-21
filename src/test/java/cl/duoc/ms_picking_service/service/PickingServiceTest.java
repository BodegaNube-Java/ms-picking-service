package cl.duoc.ms_picking_service.service;

import cl.duoc.ms_picking_service.dto.DespachoResponse;
import cl.duoc.ms_picking_service.dto.OrdenDisponibleRequest;
import cl.duoc.ms_picking_service.dto.OrdenPickingResponse;
import cl.duoc.ms_picking_service.model.EstadoPicking;
import cl.duoc.ms_picking_service.model.OrdenPicking;
import cl.duoc.ms_picking_service.repository.OrdenPickingRepository;
import cl.duoc.ms_picking_service.service.exception.OrdenPickingNoEncontradaException;
import cl.duoc.ms_picking_service.service.exception.OrdenPickingYaExisteException;
import cl.duoc.ms_picking_service.service.exception.OrdenYaDespachadaException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PickingServiceTest {

    @Mock
    private OrdenPickingRepository repository;

    @InjectMocks
    private PickingService service;

    @Test
    void registrarOrdenDisponibleGuardaOrdenEnEstadoDisponible() {
        when(repository.existsByOrdenId("ORD-001")).thenReturn(false);
        when(repository.save(any(OrdenPicking.class))).thenAnswer(inv -> inv.getArgument(0));

        OrdenPickingResponse response =
                service.registrarOrdenDisponible(new OrdenDisponibleRequest("ORD-001", "COM-001"));

        ArgumentCaptor<OrdenPicking> captor = ArgumentCaptor.forClass(OrdenPicking.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getEstado()).isEqualTo(EstadoPicking.DISPONIBLE);
        assertThat(captor.getValue().getFechaDisponible()).isNotNull();
        assertThat(response.ordenId()).isEqualTo("ORD-001");
        assertThat(response.comercioId()).isEqualTo("COM-001");
    }

    @Test
    void registrarOrdenDuplicadaLanzaConflictoSinGuardar() {
        when(repository.existsByOrdenId("ORD-001")).thenReturn(true);

        assertThatThrownBy(() ->
                service.registrarOrdenDisponible(new OrdenDisponibleRequest("ORD-001", "COM-001")))
                .isInstanceOf(OrdenPickingYaExisteException.class);

        verify(repository, never()).save(any());
    }

    @Test
    void carreraEnInsercionSeTraduceAConflictoDeNegocio() {
        // Dos peticiones pasan el existsBy a la vez; el índice único rechaza la segunda.
        when(repository.existsByOrdenId("ORD-002")).thenReturn(false);
        when(repository.save(any(OrdenPicking.class))).thenThrow(new DuplicateKeyException("E11000"));

        assertThatThrownBy(() ->
                service.registrarOrdenDisponible(new OrdenDisponibleRequest("ORD-002", "COM-001")))
                .isInstanceOf(OrdenPickingYaExisteException.class);
    }

    @Test
    void listarDisponiblesSoloConsultaEstadoDisponible() {
        OrdenPicking orden = OrdenPicking.nuevaDisponible("ORD-003", "COM-002");
        when(repository.findByEstado(EstadoPicking.DISPONIBLE)).thenReturn(List.of(orden));

        List<OrdenPickingResponse> lista = service.listarDisponibles();

        assertThat(lista).hasSize(1);
        assertThat(lista.get(0).ordenId()).isEqualTo("ORD-003");
        assertThat(lista.get(0).estado()).isEqualTo(EstadoPicking.DISPONIBLE);
    }

    @Test
    void registrarDespachoGeneraNumeroDeSeguimientoConFormatoBN() {
        OrdenPicking orden = OrdenPicking.nuevaDisponible("ORD-004", "COM-001");
        when(repository.findByOrdenId("ORD-004")).thenReturn(Optional.of(orden));

        DespachoResponse despacho = service.registrarDespacho("ORD-004");

        assertThat(despacho.numeroSeguimiento()).matches("BN-[0-9A-F]{8}");
        assertThat(despacho.fechaDespacho()).isNotNull();
        assertThat(orden.getEstado()).isEqualTo(EstadoPicking.DESPACHADO);
        verify(repository).save(orden);
    }

    @Test
    void despachoDeOrdenInexistenteLanzaNoEncontrada() {
        when(repository.findByOrdenId("ORD-X")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.registrarDespacho("ORD-X"))
                .isInstanceOf(OrdenPickingNoEncontradaException.class);
    }

    @Test
    void despachoDeOrdenYaDespachadaLanzaConflicto() {
        OrdenPicking orden = OrdenPicking.nuevaDisponible("ORD-005", "COM-001");
        orden.setEstado(EstadoPicking.DESPACHADO);
        when(repository.findByOrdenId("ORD-005")).thenReturn(Optional.of(orden));

        assertThatThrownBy(() -> service.registrarDespacho("ORD-005"))
                .isInstanceOf(OrdenYaDespachadaException.class);

        verify(repository, never()).save(any());
    }
}
