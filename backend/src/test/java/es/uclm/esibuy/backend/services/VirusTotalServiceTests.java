package es.uclm.esibuy.backend.services;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import tools.jackson.databind.ObjectMapper;

class VirusTotalServiceTests {

    @Test
    void noHacePeticionesCuandoElAnalisisEstaDesactivado() {
        RestClient.Builder builder = builderMock();
        VirusTotalService servicio = new VirusTotalService(
                builder, mock(ObjectMapper.class), "", false, true);

        assertDoesNotThrow(() -> servicio.verificar(new byte[] { 1, 2, 3 }, "image/png"));
    }

    @Test
    void rechazaLaConfiguracionActivaSinApiKey() {
        RestClient.Builder builder = builderMock();
        VirusTotalService servicio = new VirusTotalService(
                builder, mock(ObjectMapper.class), "", true, true);

        assertThrows(IllegalStateException.class,
                () -> servicio.verificar(new byte[] { 1, 2, 3 }, "image/png"));
    }

    private static RestClient.Builder builderMock() {
        RestClient.Builder builder = mock(RestClient.Builder.class);
        when(builder.baseUrl("https://www.virustotal.com/api/v3")).thenReturn(builder);
        when(builder.build()).thenReturn(mock(RestClient.class));
        return builder;
    }
}
