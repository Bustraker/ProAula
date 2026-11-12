package com.proaula.aula.config;

import com.proaula.aula.Service.CustomUserDetailsService;
import com.proaula.aula.Service.LegalConsentService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestRedirectFilter;
import org.springframework.security.web.context.DelegatingSecurityContextRepository;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.RequestAttributeSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final CustomUserDetailsService userDetailsService;
    private final CustomOAuth2UserService customOAuth2UserService;
    private final OAuth2LoginSuccessHandler oauth2LoginSuccessHandler;
    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final LegalConsentService legalConsentService;

    public SecurityConfig(CustomUserDetailsService userDetailsService,
                          CustomOAuth2UserService customOAuth2UserService,
                          OAuth2LoginSuccessHandler oauth2LoginSuccessHandler,
                          JwtAuthenticationFilter jwtAuthenticationFilter,
                          LegalConsentService legalConsentService) {
        this.userDetailsService = userDetailsService;
        this.customOAuth2UserService = customOAuth2UserService;
        this.oauth2LoginSuccessHandler = oauth2LoginSuccessHandler;
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.legalConsentService = legalConsentService;
    }

    @Bean
    public DaoAuthenticationProvider authenticationProvider(BCryptPasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider();
        authProvider.setUserDetailsService(userDetailsService);
        authProvider.setPasswordEncoder(passwordEncoder);
        return authProvider;
    }

    @Bean
    public AuthenticationManager authenticationManager(HttpSecurity http, DaoAuthenticationProvider authProvider) throws Exception {
        AuthenticationManagerBuilder authenticationManagerBuilder =
            http.getSharedObject(AuthenticationManagerBuilder.class);
        authenticationManagerBuilder.authenticationProvider(authProvider);
        return authenticationManagerBuilder.build();
    }

    @Bean
    public SecurityContextRepository securityContextRepository() {
        return new DelegatingSecurityContextRepository(
            new RequestAttributeSecurityContextRepository(),
            new HttpSessionSecurityContextRepository()
        );
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
            )
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health").permitAll()
                .requestMatchers(HttpMethod.POST, "/rutas").access(adminAuthorization())
                .requestMatchers(HttpMethod.POST, "/api/buses/**").access(adminAuthorization())
                .requestMatchers(HttpMethod.PUT, "/api/buses/**").access(adminAuthorization())
                .requestMatchers(HttpMethod.DELETE, "/api/buses/**").access(adminAuthorization())
                .requestMatchers("/api/buses/todos").hasAnyRole("USER", "ADMIN")
                .requestMatchers("/api/admin/**").access(adminAuthorization())
                .requestMatchers("/api/favoritos/**").hasAnyRole("USER", "ADMIN")
                .requestMatchers("/api/notificaciones/**").hasAnyRole("USER", "ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/modelos/retrasos/riesgo").permitAll()
                .requestMatchers("/api/modelos/**").hasAnyRole("USER", "ADMIN")
                .requestMatchers(HttpMethod.GET, "/api/llegadas/**").permitAll()
                .requestMatchers("/api/llegadas/**").hasAnyRole("USER", "ADMIN")
                .requestMatchers(HttpMethod.GET, "/api/incidentes/ruta/*").permitAll()
                // Rutas completamente públicas
                .requestMatchers(
                    "/", "/registro-mejorado", "/registro-mejorado.html", "/registro", "/registro/**",
                    "/admin/registrar", "/firebase-messaging-sw.js", "/api/firebase/config",
                    "/inicio-de-sesion-mejorado", "/inicio-de-sesion-mejorado.html", "/inicio_de_sesion",
                    "/admin-login", "/admin/verificar-codigo", "/admin/login",
                    "/css/**", "/js/**", "/images/**", "/data/cartografia/**",
                    "/public_index_3", "/viajar_public", "/index_3_public",
                    "/contacto", "/contacto_public", "/contacto_usuario",
                    "/oauth2/preauthorize", "/oauth2/consentir",
                    "/terminos", "/terminos.html", "/privacidad", "/privacidad.html",
                    "/login/oauth2/**", "/oauth2/authorization/**",
                    "/api/usuarios/login", "/api/usuarios/register",
                    // Permite acceso público para barrios
                    "/api/barrios/**",
                    "/rutas", "/rutas/detalle/**", "/ruta/**", "/buses/**"
                ).permitAll()
                .requestMatchers(HttpMethod.GET, "/api/rutas", "/api/rutas-con-coordenadas",
                    "/api/map/tiles").permitAll()
                .requestMatchers("/oauth2/complete-password").hasAnyRole("USER", "ADMIN")
                .requestMatchers(HttpMethod.GET, "/incidentes/panel", "/api/incidentes/**").access(adminAuthorization())
                .requestMatchers(HttpMethod.PATCH, "/api/incidentes/**").access(adminAuthorization())
                .requestMatchers("/incidentes/**", "/api/incidentes/**").hasAnyRole("USER", "ADMIN")
                // Protege rutas de administración
                .requestMatchers(
                    "/index_2", "/admin/**", "/reportes", "/gestionar-usuarios",
                    "/editar-usuario/**", "/eliminar-usuario/**",
                    "/actualizar-usuario", "/rutas/admin",
                    "/registro-buses", "/actualizarbuses", "/eliminarbuses",
                    "/actualizar-bus/**", "/eliminar-buses",
                    "/editar-ruta", "/editar-ruta/**", "/eliminar-ruta/**",
                    "/mensajes_contacto", "/contacto/mensajes", "/eliminar_mensaje/**"
                ).access(adminAuthorization())
                // Rutas para usuarios y administradores autenticados
                .requestMatchers(
                    "/dashboard", "/perfil", "/perfil/actualizar", "/perfil/cambiar-password",
                    "/viajar", "/historial", "/usuario/**", "/index_3", "/consultas"
                ).hasAnyRole("USER", "ADMIN")
                .anyRequest().authenticated()
            )
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(new OAuthConsentGateFilter(), OAuth2AuthorizationRequestRedirectFilter.class)
            .addFilterAfter(new LegalConsentEnforcementFilter(legalConsentService), AuthorizationFilter.class)
            .addFilterAfter(new AuthenticationRateLimitFilter(), JwtAuthenticationFilter.class)
            .formLogin(form -> form
                .loginPage("/inicio-de-sesion-mejorado")
                .loginProcessingUrl("/login")
                .defaultSuccessUrl("/dashboard", true)
                .failureUrl("/inicio-de-sesion-mejorado?error=true")
                .permitAll()
            )
            .oauth2Login(oauth2 -> oauth2
                .loginPage("/inicio-de-sesion-mejorado")
                .userInfoEndpoint(userInfo -> userInfo
                    .oidcUserService(customOAuth2UserService::loadOidcUser)
                    .userService(customOAuth2UserService))
                .successHandler(oauth2LoginSuccessHandler)
                .failureUrl("/inicio-de-sesion-mejorado?oauthError=true")
            )
            .logout(logout -> logout
                .logoutUrl("/logout")
                .logoutSuccessUrl("/inicio-de-sesion-mejorado?logout=true")
                .invalidateHttpSession(true)
                .deleteCookies("JSESSIONID", "BustrakerSession", "JWT_TOKEN")
                .permitAll()
            );

        return http.build();
    }

    private AuthorizationManager<RequestAuthorizationContext> adminAuthorization() {
        return (authentication, context) -> {
            var current = authentication.get();
            boolean hasAdminRole = current != null && current.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));
            var session = context.getRequest().getSession(false);
            boolean codeVerified = session != null
                && "ADMIN".equals(session.getAttribute("rolUsuario"));
            return new AuthorizationDecision(hasAdminRole && codeVerified);
        };
    }
}
