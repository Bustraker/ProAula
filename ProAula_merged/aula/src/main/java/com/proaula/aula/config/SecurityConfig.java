package com.proaula.aula.config;

import com.proaula.aula.Service.CustomUserDetailsService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
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

    public SecurityConfig(CustomUserDetailsService userDetailsService,
                          CustomOAuth2UserService customOAuth2UserService,
                          OAuth2LoginSuccessHandler oauth2LoginSuccessHandler,
                          JwtAuthenticationFilter jwtAuthenticationFilter) {
        this.userDetailsService = userDetailsService;
        this.customOAuth2UserService = customOAuth2UserService;
        this.oauth2LoginSuccessHandler = oauth2LoginSuccessHandler;
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
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
            .csrf(csrf -> csrf
                .ignoringRequestMatchers("/api/**")
            )
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
            )
            .authorizeHttpRequests(auth -> auth
                // Rutas completamente públicas
                .requestMatchers(
                    "/", "/registro-mejorado", "/registro-mejorado.html", "/registro", "/registro/**",
                    "/inicio-de-sesion-mejorado", "/inicio-de-sesion-mejorado.html", "/inicio_de_sesion",
                    "/admin-login", "/admin/verificar-codigo", "/admin/login",
                    "/css/**", "/js/**", "/images/**",
                    "/public_index_3", "/viajar_public", "/index_3_public",
                    "/contacto_public", "/contacto_usuario",
                    "/terminos", "/terminos.html", "/privacidad", "/privacidad.html",
                    "/oauth2/**", "/login/oauth2/**", "/oauth2/authorization/**",
                    "/api/usuarios/login", "/api/usuarios/register",
                    "/rutas", "/rutas/detalle/**", "/ruta/**", "/buses/**"
                ).permitAll()
                // FIX: rutas de gestión de admin protegidas explícitamente
                .requestMatchers(
                    "/index_2", "/admin/**", "/reportes", "/gestionar-usuarios",
                    "/editar-usuario/**", "/eliminar-usuario/**", "/admin-crear-usuario",
                    "/actualizar-usuario", "/rutas/admin",
                    "/registro-buses", "/actualizarbuses", "/eliminarbuses",
                    "/actualizar-bus/**", "/eliminar-buses",
                    "/editar-ruta", "/editar-ruta/**", "/eliminar-ruta/**",
                    "/mensajes_contacto", "/eliminar_mensaje/**"
                ).hasRole("ADMIN")
                // Rutas para usuarios y administradores autenticados
                .requestMatchers(
                    "/dashboard", "/perfil", "/perfil/actualizar", "/perfil/cambiar-password",
                    "/viajar", "/consultas", "/historial", "/usuario/**", "/index_3"
                ).hasAnyRole("USER", "ADMIN")
                .anyRequest().authenticated()
            )
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
            .formLogin(form -> form
                .loginPage("/inicio-de-sesion-mejorado")
                .loginProcessingUrl("/login")
                .defaultSuccessUrl("/dashboard", true)
                .failureUrl("/inicio-de-sesion-mejorado?error=true")
                .permitAll()
            )
            .oauth2Login(oauth2 -> oauth2
                .loginPage("/inicio-de-sesion-mejorado")
                .userInfoEndpoint(userInfo -> userInfo.userService(customOAuth2UserService))
                .successHandler(oauth2LoginSuccessHandler)
                .failureUrl("/inicio-de-sesion-mejorado?error=true")
            )
            .logout(logout -> logout
                .logoutUrl("/logout")
                .logoutSuccessUrl("/")
                .invalidateHttpSession(true)
                .deleteCookies("JSESSIONID", "BustrakerSession", "JWT_TOKEN")
                .permitAll()
            );

        return http.build();
    }
}
