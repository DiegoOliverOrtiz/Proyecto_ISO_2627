package es.uclm.esibuy.backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.convert.PropertyValueConverter;
import org.springframework.data.mongodb.core.convert.MongoConversionContext;
import org.springframework.data.mongodb.core.convert.MongoCustomConversions;

import es.uclm.esibuy.backend.model.Administrador;
import es.uclm.esibuy.backend.model.Cliente;
import es.uclm.esibuy.backend.model.Vendedor;
import es.uclm.esibuy.backend.services.DatosPersonalesCryptoService;

@Configuration
public class DatosPersonalesMongoConfiguration {

    @Bean
    public MongoCustomConversions mongoCustomConversions(DatosPersonalesCryptoService cifrado) {
        return MongoCustomConversions.create(conversiones ->
                conversiones.configurePropertyConversions(registro -> {
                    PropertyValueConverter<String, String, MongoConversionContext> converter =
                            new PropertyValueConverter<>() {
                                @Override
                                public String read(String valor, MongoConversionContext contexto) {
                                    return cifrado.descifrar(valor, contexto.getProperty().getName());
                                }

                                @Override
                                public String write(String valor, MongoConversionContext contexto) {
                                    return cifrado.cifrar(valor, contexto.getProperty().getName());
                                }
                            };
                    registro.registerConverter(Cliente.class, "telefono", converter);
                    registro.registerConverter(Vendedor.class, "telefono", converter);
                    registro.registerConverter(Administrador.class, "telefono", converter);
                    registro.registerConverter(Cliente.class, "dniNif", converter);
                    registro.registerConverter(Vendedor.class, "dniNif", converter);
                }));
    }
}
