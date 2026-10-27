package com.soundia.queue.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.soundia.config.BadRequestException;
import com.soundia.config.ConflictException;
import com.soundia.config.ResourceNotFoundException;
import com.soundia.queue.model.QueueItem;
import com.soundia.queue.service.QueueService;

@WebMvcTest(QueueController.class)
class QueueControllerTests {

    private static final LocalDateTime AHORA = LocalDateTime.of(2026, 10, 21, 20, 0);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private QueueService queue;

    private QueueItem item(long id, long sesion, long pista) {
        QueueItem item = new QueueItem(sesion, pista, AHORA);
        item.setId(id);
        return item;
    }

    private ResultActions encolar(long sesion, String json) throws Exception {
        return mockMvc.perform(post("/api/v1/sessions/" + sesion + "/queue")
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    @Test
    void encolarUnaPistaDevuelve201ConLocationYElElemento() throws Exception {
        when(queue.add(5L, 42L)).thenReturn(item(8, 5, 42));

        encolar(5, "{\"trackId\":42}")
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/sessions/5/queue/8"))
                .andExpect(jsonPath("$.id").value(8))
                .andExpect(jsonPath("$.sessionId").value(5))
                .andExpect(jsonPath("$.trackId").value(42));
    }

    @Test
    void unaPeticionInvalidaDevuelve400SinLlamarAlServicio() throws Exception {
        encolar(5, "{}").andExpect(status().isBadRequest());
        encolar(5, "{\"trackId\":0}").andExpect(status().isBadRequest());
        encolar(5, "{\"trackId\":\"x\"}").andExpect(status().isBadRequest());

        verify(queue, never()).add(anyLong(), any());
    }

    @Test
    void encolarEnUnaSesionCerradaDevuelve409() throws Exception {
        when(queue.add(5L, 42L)).thenThrow(new ConflictException("La sesion 5 ya esta cerrada."));

        encolar(5, "{\"trackId\":42}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("La sesion 5 ya esta cerrada."));
    }

    @Test
    void encolarEnUnaSesionInexistenteDevuelve404() throws Exception {
        when(queue.add(9L, 42L)).thenThrow(new ResourceNotFoundException("No existe la sesion 9."));

        encolar(9, "{\"trackId\":42}").andExpect(status().isNotFound());
    }

    @Test
    void listaLaColaEnOrden() throws Exception {
        when(queue.list(5L)).thenReturn(List.of(item(1, 5, 10), item(2, 5, 20)));

        mockMvc.perform(get("/api/v1/sessions/5/queue"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].trackId").value(10))
                .andExpect(jsonPath("$[1].trackId").value(20));
    }

    @Test
    void unaColaVaciaDevuelveUnaListaVacia() throws Exception {
        when(queue.list(5L)).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/sessions/5/queue"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void sacarLaSiguienteDevuelveLaPrimeraPista() throws Exception {
        when(queue.pollNext(5L, null)).thenReturn(item(1, 5, 10));

        mockMvc.perform(post("/api/v1/sessions/5/queue/next"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trackId").value(10));
    }

    @Test
    void sacarLaSiguienteConOrderPasaLaEstrategiaElegida() throws Exception {
        when(queue.pollNext(5L, "shuffle")).thenReturn(item(2, 5, 20));

        mockMvc.perform(post("/api/v1/sessions/5/queue/next").param("order", "shuffle"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trackId").value(20));
    }

    @Test
    void unOrdenDesconocidoDevuelve400() throws Exception {
        when(queue.pollNext(5L, "raro")).thenThrow(new BadRequestException("Orden desconocido 'raro'."));

        mockMvc.perform(post("/api/v1/sessions/5/queue/next").param("order", "raro"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Orden desconocido 'raro'."));
    }

    @Test
    void sacarDeUnaColaVaciaDevuelve404() throws Exception {
        when(queue.pollNext(5L, null)).thenThrow(new ResourceNotFoundException("La cola de la sesion 5 esta vacia."));

        mockMvc.perform(post("/api/v1/sessions/5/queue/next"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("La cola de la sesion 5 esta vacia."));
    }

    @Test
    void quitarUnElementoDevuelve204() throws Exception {
        mockMvc.perform(delete("/api/v1/sessions/5/queue/3")).andExpect(status().isNoContent());

        verify(queue).remove(5L, 3L);
    }

    @Test
    void quitarUnElementoInexistenteDevuelve404() throws Exception {
        org.mockito.Mockito.doThrow(new ResourceNotFoundException("No existe el elemento 3."))
                .when(queue).remove(5L, 3L);

        mockMvc.perform(delete("/api/v1/sessions/5/queue/3")).andExpect(status().isNotFound());
    }

    @Test
    void idsNoNumericosDevuelven400() throws Exception {
        mockMvc.perform(get("/api/v1/sessions/abc/queue")).andExpect(status().isBadRequest());
        mockMvc.perform(delete("/api/v1/sessions/5/queue/xyz")).andExpect(status().isBadRequest());
    }
}
