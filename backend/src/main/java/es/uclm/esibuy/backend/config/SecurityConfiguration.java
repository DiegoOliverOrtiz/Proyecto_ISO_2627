package es.uclm.esibuy.backend.config;

import java.time.Duration;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.session.SessionRegistryImpl;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.session.HttpSessionEventPublisher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import es.uclm.esibuy.backend.security.PepperedBCryptPasswordEncoder;
import es.uclm.esibuy.backend.security.SesionAbsolutaFilter;

@Configuration
@EnableMethodSecurity
public class SecurityConfiguration {

        private static final String ROL_ADMIN = "ADMIN";
        private static final String RUTA_CATEGORIAS = "/categorias/**";

        @Bean
        PasswordEncoder passwordEncoder(
                        @Value("${app.security.password.pepper:}") String pepper) {
                return new PepperedBCryptPasswordEncoder(pepper, 12);
        }

        @Bean
        SecurityContextRepository securityContextRepository() {
                return new HttpSessionSecurityContextRepository();
        }

        @Bean
        CookieCsrfTokenRepository csrfTokenRepository(
                        @Value("${app.security.cookies.secure:true}") boolean cookieSeguro) {

                CookieCsrfTokenRepository repository = CookieCsrfTokenRepository.withHttpOnlyFalse();

                repository.setCookieCustomizer(cookie -> cookie.secure(cookieSeguro).sameSite("Lax"));

                return repository;
        }

        @Bean
        SessionRegistry sessionRegistry() {
                return new SessionRegistryImpl();
        }

        @Bean
        HttpSessionEventPublisher httpSessionEventPublisher() {
                return new HttpSessionEventPublisher();
        }

        @Bean
        SecurityFilterChain securityFilterChain(
                        HttpSecurity http,
                        CorsConfigurationSource corsConfigurationSource,
                        SecurityContextRepository securityContextRepository,
                        CookieCsrfTokenRepository csrfTokenRepository,
                        SessionRegistry sessionRegistry,
                        @Value("${app.security.session.absolute-timeout:8h}") Duration timeoutAbsolutoSesion)
                        throws Exception {

                http
                                .cors(cors -> cors.configurationSource(corsConfigurationSource))

                                .securityContext(context -> context.securityContextRepository(
                                                securityContextRepository))

                                .sessionManagement(session -> session
                                                .sessionCreationPolicy(
                                                                org.springframework.security.config.http.SessionCreationPolicy.IF_REQUIRED)
                                                .maximumSessions(-1)
                                                .sessionRegistry(sessionRegistry)
                                                .expiredSessionStrategy(event -> event.getResponse()
                                                                .setStatus(HttpStatus.UNAUTHORIZED.value())))

                                .addFilterBefore(new SesionAbsolutaFilter(timeoutAbsolutoSesion),
                                                AuthorizationFilter.class)

                                .csrf(csrf -> csrf
                                                .csrfTokenRepository(csrfTokenRepository)
                                                .ignoringRequestMatchers(
                                                                "/cliente/register",
                                                                "/vendedor/register"))

                                .logout(logout -> logout
                                                .logoutUrl("/usuarios/logout")
                                                .logoutSuccessHandler(
                                                                (request, response, authentication) -> response
                                                                                .setStatus(204)))

                                // El orden importa: primero las rutas públicas y después las de cada rol.
                                .authorizeHttpRequests(authorize -> {
                                        permitirRutasPublicas(authorize);
                                        restringirPorRol(authorize);
                                });

                return http.build();
        }

        private static void permitirRutasPublicas(
                        AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizationManagerRequestMatcherRegistry authorize) {
                authorize
                                                .requestMatchers(
                                                                HttpMethod.OPTIONS,
                                                                "/**")
                                                .permitAll()

                                                .requestMatchers(
                                                                HttpMethod.POST,
                                                                "/usuarios/login",
                                                                "/usuarios/login/2fa",
                                                                "/usuarios/login/2fa/configurar",
                                                                "/usuarios/login/2fa/confirmar-alta",
                                                                "/usuarios/recuperar-contrasena",
                                                                "/usuarios/recuperar-contrasena/confirmar",
                                                                "/cliente/register",
                                                                "/vendedor/register")
                                                .permitAll()

                                                .requestMatchers(
                                                                HttpMethod.GET,
                                                                "/usuarios/csrf")
                                                .permitAll()

                                                .requestMatchers(
                                                                HttpMethod.GET,
                                                                "/product-images/**")
                                                .permitAll()
                                                // Recursos públicos del frontend Angular empaquetado
                                                .requestMatchers(
                                                                HttpMethod.GET,
                                                                "/",
                                                                "/index.html",
                                                                "/favicon.ico",
                                                                "/*.js",
                                                                "/*.css",
                                                                "/avatars/**",
                                                                "/media/**")
                                                .permitAll()

                                                // Rutas HTML de Angular: solo navegación GET.
                                                // La autorización real de los datos sigue en las API.
                                                .requestMatchers(
                                                                HttpMethod.GET,
                                                                "/login",
                                                                "/registro",
                                                                "/recuperar-contrasena",
                                                                "/perfil",
                                                                "/listas",
                                                                "/perfil/direcciones",
                                                                "/perfil/pagos",
                                                                "/admin",
                                                                "/admin/usuarios",
                                                                "/admin/usuarios/*",
                                                                "/admin/categorias",
                                                                "/admin/administradores",
                                                                "/admin/administradores/nuevo",
                                                                "/admin/productos",
                                                                "/cliente",
                                                                "/cliente/favoritos",
                                                                "/cliente/pedidos",
                                                                "/carrito",
                                                                "/cliente/checkout",
                                                                "/vendedor",
                                                                "/vendedor/pedidos")
                                                .permitAll()

                                                // Necesario para el registro público de vendedores.
                                                .requestMatchers(
                                                                HttpMethod.GET,
                                                                "/categorias/registro")
                                                .permitAll();
        }

        private static void restringirPorRol(
                        AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizationManagerRequestMatcherRegistry authorize) {
                authorize
                                                // HU11 + HU17: los vendedores necesitan consultar categorías para
                                                // asociar productos
                                                .requestMatchers(
                                                                HttpMethod.GET,
                                                                "/categorias",
                                                                RUTA_CATEGORIAS)
                                                .hasAnyRole(ROL_ADMIN, "VENDEDOR")

                                                .requestMatchers(
                                                                HttpMethod.POST,
                                                                "/categorias")
                                                .hasRole(ROL_ADMIN)

                                                .requestMatchers(
                                                                HttpMethod.PUT,
                                                                RUTA_CATEGORIAS)
                                                .hasRole(ROL_ADMIN)

                                                .requestMatchers(
                                                                HttpMethod.DELETE,
                                                                RUTA_CATEGORIAS)
                                                .hasRole(ROL_ADMIN)

                                                .requestMatchers(
                                                                "/cliente/**")
                                                .hasRole("CLIENTE")

                                                .requestMatchers(
                                                                "/vendedor/**")
                                                .hasRole("VENDEDOR")

                                                .requestMatchers(
                                                                "/administrador/**")
                                                .hasRole(ROL_ADMIN)

                                                .anyRequest().authenticated();
        }

        @Bean
        CorsConfigurationSource corsConfigurationSource(
                        @Value("${app.cors.allowed-origin:https://localhost:4200}") String allowedOrigin) {

                CorsConfiguration configuration = new CorsConfiguration();

                configuration.setAllowedOrigins(List.of(allowedOrigin));
                configuration.setAllowedMethods(
                                List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
                configuration.setAllowedHeaders(
                                List.of("Content-Type", "Accept", "X-XSRF-TOKEN"));
                configuration.setAllowCredentials(true);

                UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();

                source.registerCorsConfiguration("/**", configuration);

                return source;
        }
}
