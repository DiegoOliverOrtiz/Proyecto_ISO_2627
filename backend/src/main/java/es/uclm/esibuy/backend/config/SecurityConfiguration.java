package es.uclm.esibuy.backend.config;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
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
	SecurityFilterChain securityFilterChain(
			HttpSecurity http,
			CorsConfigurationSource corsConfigurationSource,
			SecurityContextRepository securityContextRepository) throws Exception {
		http
				.cors(cors -> cors.configurationSource(corsConfigurationSource))
				.securityContext(context -> context.securityContextRepository(securityContextRepository))
				.sessionManagement(session -> session.sessionCreationPolicy(
						org.springframework.security.config.http.SessionCreationPolicy.IF_REQUIRED))
				.csrf(csrf -> csrf
						.csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
						.ignoringRequestMatchers(
								"/cliente/register",
								"/vendedor/register",
								"/administrador/register"))
				.authorizeHttpRequests(authorize -> authorize
						.requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
						.requestMatchers(HttpMethod.GET, "/usuarios/csrf").permitAll()
						.requestMatchers(HttpMethod.POST, "/usuarios/login").permitAll()
						.requestMatchers(HttpMethod.POST,
								"/cliente/register",
								"/vendedor/register",
								"/administrador/register").permitAll()
						.requestMatchers(HttpMethod.POST, "/usuarios/logout").authenticated()
						.requestMatchers("/administrador/**").hasRole("ADMIN")
						.requestMatchers("/cliente/**").hasRole("CLIENTE")
						.requestMatchers("/vendedor/**").hasRole("VENDEDOR")
						.anyRequest().authenticated());
		http
				.logout(logout -> logout
						.logoutUrl("/usuarios/logout")
						.invalidateHttpSession(true)
						.clearAuthentication(true)
						.deleteCookies("JSESSIONID")
						.logoutSuccessHandler((request, response, authentication) ->
								response.setStatus(HttpStatus.NO_CONTENT.value())))
				.exceptionHandling(exceptions -> exceptions
						.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)));
		return http.build();
	}

	@Bean
	CorsConfigurationSource corsConfigurationSource(
			@Value("${app.cors.allowed-origin:http://localhost:4200}") String allowedOrigin) {
		CorsConfiguration configuration = new CorsConfiguration();
		configuration.setAllowedOrigins(List.of(allowedOrigin));
		configuration.setAllowedMethods(List.of("GET", "POST", "PATCH", "DELETE", "OPTIONS"));
		configuration.setAllowedHeaders(List.of("Content-Type", "Accept", "X-XSRF-TOKEN"));
		configuration.setAllowCredentials(true);

		UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
		source.registerCorsConfiguration("/**", configuration);
		return source;
	}
}