package es.uclm.esibuy.backend.config;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.session.SessionRegistryImpl;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.session.HttpSessionEventPublisher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableMethodSecurity
public class SecurityConfiguration {

        @Bean
        PasswordEncoder passwordEncoder() {
                return new BCryptPasswordEncoder();
        }

        @Bean
        SecurityContextRepository securityContextRepository() {
                return new HttpSessionSecurityContextRepository();
        }

        @Bean
        CookieCsrfTokenRepository csrfTokenRepository(
                        @Value("${app.security.cookies.secure:false}") boolean cookieSeguro) {

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
                        SessionRegistry sessionRegistry) throws Exception {

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

                                .authorizeHttpRequests(authorize -> authorize
                                                .requestMatchers(
                                                                HttpMethod.OPTIONS,
                                                                "/**")
                                                .permitAll()

                                                .requestMatchers(
                                                                HttpMethod.POST,
                                                                "/usuarios/login",
                                                                "/cliente/register",
                                                                "/vendedor/register")
                                                .permitAll()

                                                .requestMatchers(
                                                                HttpMethod.GET,
                                                                "/usuarios/csrf")
                                                .permitAll()

                                                // Necesario para el registro público de vendedores.
                                                .requestMatchers(
                                                                HttpMethod.GET,
                                                                "/categorias/registro")
                                                .permitAll()

                                                // HU11: consulta administrativa de categorías.
                                                .requestMatchers(
                                                                HttpMethod.GET,
                                                                "/categorias",
                                                                "/categorias/**")
                                                .hasRole("ADMIN")

                                                .requestMatchers(
                                                                HttpMethod.POST,
                                                                "/categorias")
                                                .hasRole("ADMIN")

                                                .requestMatchers(
                                                                HttpMethod.PUT,
                                                                "/categorias/**")
                                                .hasRole("ADMIN")

                                                .requestMatchers(
                                                                "/cliente/**")
                                                .hasRole("CLIENTE")

                                                .requestMatchers(
                                                                "/vendedor/**")
                                                .hasRole("VENDEDOR")

                                                .requestMatchers(
                                                                "/administrador/**")
                                                .hasRole("ADMIN")

                                                .anyRequest().authenticated());

                return http.build();
        }

        @Bean
        CorsConfigurationSource corsConfigurationSource(
                        @Value("${app.cors.allowed-origin:http://localhost:4200}") String allowedOrigin) {

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