package es.uclm.esibuy.backend.dao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;

import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.convert.MongoConverter;
import org.springframework.data.mongodb.core.query.Query;
import org.junit.jupiter.api.extension.ExtendWith;

import es.uclm.esibuy.backend.dto.EstadisticasUsuariosResponse;
import es.uclm.esibuy.backend.model.Usuario;

@ExtendWith(MockitoExtension.class)
class UsuarioConsultaDAOImplHU04Tests {

    @Mock
    private MongoTemplate mongoTemplate;

    @Mock
    private MongoConverter mongoConverter;

    private UsuarioConsultaDAOImpl dao;

    @BeforeEach
    void setUp() {
        dao = new UsuarioConsultaDAOImpl(mongoTemplate, mongoConverter);
    }

    @Test
    void buscarConstruyeConsultaConTextoEstadoYRol() {
        PageRequest pageable = PageRequest.of(0, 20);
        when(mongoTemplate.find(any(Query.class), eq(Document.class), eq("usuarios")))
                .thenReturn(List.of());
        when(mongoTemplate.count(any(Query.class), eq(Usuario.class))).thenReturn(0L);

        Page<Usuario> resultado = dao.buscar("Ana", "ACTIVO", "CLIENTE", pageable);

        assertTrue(resultado.isEmpty());
        ArgumentCaptor<Query> consulta = ArgumentCaptor.forClass(Query.class);
        verify(mongoTemplate).find(consulta.capture(), eq(Document.class), eq("usuarios"));
        String consultaJson = consulta.getValue().getQueryObject().toJson();
        assertTrue(consultaJson.contains("nombre"));
        assertTrue(consultaJson.contains("activada"));
        assertTrue(consultaJson.contains("_class"));
    }

    @Test
    void rechazaEstadoDesconocidoSinConsultarMongo() {
        assertThrows(
                IllegalArgumentException.class,
                () -> dao.buscar(null, "DESCONOCIDO", null, PageRequest.of(0, 20)));

        verifyNoInteractions(mongoTemplate, mongoConverter);
    }

    @Test
    void rechazaRolDesconocidoSinConsultarMongo() {
        assertThrows(
                IllegalArgumentException.class,
                () -> dao.buscar(null, null, "DESCONOCIDO", PageRequest.of(0, 20)));

        verifyNoInteractions(mongoTemplate, mongoConverter);
    }

    @Test
    void obtieneEstadisticasGlobalesPorEstado() {
        when(mongoTemplate.count(any(Query.class), eq(Usuario.class)))
                .thenReturn(10L, 6L, 3L, 1L);

        EstadisticasUsuariosResponse estadisticas = dao.obtenerEstadisticas();

        assertEquals(10, estadisticas.totalUsuarios());
        assertEquals(6, estadisticas.activos());
        assertEquals(3, estadisticas.pendientes());
        assertEquals(1, estadisticas.desactivados());
    }
}
