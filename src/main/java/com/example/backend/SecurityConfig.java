package com.example.backend;

import java.io.IOException;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {
    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    UserDetailsService userDetailsService(PasswordEncoder passwordEncoder) {
        return new InMemoryUserDetailsManager(User.withUsername("test")
                .password(passwordEncoder.encode("123456"))
                .roles("USER")
                .build());
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        // Keep Spring Security's CSRF protection for cookie-based sessions.
        http.authorizeHttpRequests(authorize -> authorize
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/csrf").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/hello").authenticated()
                        .anyRequest().denyAll())
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                .requestCache(cache -> cache.disable())
                .formLogin(login -> login
                        .loginProcessingUrl("/api/login")
                        .successHandler((request, response, authentication) ->
                                json(response, 200, "{\"message\":\"Login successful\"}"))
                        .failureHandler((request, response, exception) ->
                                json(response, 401, "{\"error\":\"Invalid username or password\"}"))
                        .permitAll())
                .logout(logout -> logout
                        .logoutUrl("/api/logout")
                        .deleteCookies("JSESSIONID")
                        .logoutSuccessHandler((request, response, authentication) ->
                                json(response, 200, "{\"message\":\"Logout successful\"}")))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, exception) ->
                                json(response, 401, "{\"error\":\"Authentication required\"}"))
                        .accessDeniedHandler((request, response, exception) ->
                                json(response, 403, "{\"error\":\"Forbidden\"}")));
        return http.build();
    }

    private static void json(HttpServletResponse response, int status, String body) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(body);
    }
}
