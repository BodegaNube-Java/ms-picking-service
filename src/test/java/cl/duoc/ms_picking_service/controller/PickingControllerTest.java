package cl.duoc.ms_picking_service.controller;

import cl.duoc.ms_picking_service.dto.DespachoResponse;
import cl.duoc.ms_picking_service.dto.OrdenPickingResponse;
import cl.duoc.ms_picking_service.model.EstadoPicking;
import cl.duoc.ms_picking_service.service.PickingService;
import cl.duoc.ms_picking_service.service.exception.OrdenPickingNoEncontradaException;
import cl.duoc.ms_picking_service.service.exception.OrdenPickingYaExisteException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PickingController.class)
class PickingControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private PickingService pickingService;

    @Test
    void postOrdenDevuelve201() throws Exception {
        when(pickingService.registrarOrdenDisponible(any())).thenReturn(
                new OrdenPickingResponse("ORD-001", "COM-001", EstadoPicking.DISPONIBLE, LocalDateTime.now()));

        mvc.perform(post("/v1/picking/ordenes")
                        .contentType("application/json")
                        .content("{\"ordenId\":\"ORD-001\",\"comercioId\":\"COM-001\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado").value("DISPONIBLE"));
    }

    @Test
    void postOrdenSinCamposObligatoriosDevuelve400() throws Exception {
        mvc.perform(post("/v1/picking/ordenes")
                        .contentType("application/json")
                        .content("{\"ordenId\":\"\",\"comercioId\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void postOrdenDuplicadaDevuelve409ConFormatoDeError() throws Exception {
        when(pickingService.registrarOrdenDisponible(any()))
                .thenThrow(new OrdenPickingYaExisteException("ORD-001"));

        mvc.perform(post("/v1/picking/ordenes")
                        .contentType("application/json")
                        .content("{\"ordenId\":\"ORD-001\",\"comercioId\":\"COM-001\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.mensaje").exists());
    }

    @Test
    void getOrdenesDevuelve200ConLista() throws Exception {
        when(pickingService.listarDisponibles()).thenReturn(List.of(
                new OrdenPickingResponse("ORD-001", "COM-001", EstadoPicking.DISPONIBLE, LocalDateTime.now())));

        mvc.perform(get("/v1/picking/ordenes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].ordenId").value("ORD-001"));
    }

    @Test
    void despachoDevuelve200ConNumeroDeSeguimiento() throws Exception {
        when(pickingService.registrarDespacho(anyString())).thenReturn(
                new DespachoResponse("ORD-001", "BN-1A2B3C4D", LocalDateTime.now()));

        mvc.perform(post("/v1/picking/ordenes/ORD-001/despacho"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.numeroSeguimiento").value("BN-1A2B3C4D"));
    }

    @Test
    void despachoDeOrdenInexistenteDevuelve404() throws Exception {
        when(pickingService.registrarDespacho(anyString()))
                .thenThrow(new OrdenPickingNoEncontradaException("ORD-X"));

        mvc.perform(post("/v1/picking/ordenes/ORD-X/despacho"))
                .andExpect(status().isNotFound());
    }
}
