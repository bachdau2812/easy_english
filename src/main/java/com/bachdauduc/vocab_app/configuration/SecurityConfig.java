package com.bachdauduc.vocab_app.configuration;

import com.bachdauduc.vocab_app.dto.response.ApiResponse;
import com.bachdauduc.vocab_app.exception.ErrorCode;
import com.bachdauduc.vocab_app.properties.RedisKeyProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.SignedJWT;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.logout.LogoutFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.text.ParseException;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;

@Configuration
@EnableMethodSecurity
@EnableWebSecurity

public class SecurityConfig {
    private static final String[] PUBLIC_ENDPOINT = {
            "/auth/register",
            "/auth/verify-email",
            "/auth/login",
            "/auth/logout",
            "/auth/refresh-token",
            "/auth/forgot-password",
            "/auth/forgot-password/submit-code",
            "/swagger-ui/**",
            "/v2/api-docs/**",
            "/v3/api-docs/**",
            "/swagger-resources/**",
            "/swagger-ui.html",
            "/webjars/**"
    };

    private static final String[] PUBLIC_GET_ENDPOINT = {
            "/word-data/word",
            "/word-data/words/search",
            "/word-data/words/basic-search",
            "/learning-resources/ielts-reading-sources",
            "/learning-resources/ielts-reading-sources/categories",
            "/learning-resources/ielts-reading-sources/by-category"
    };

    @Value("${jwt.signerKey}")
    private String SIGNED_KEY;

    private final FilterChainExceptionHandler filterChainExceptionHandler;
    private final RedisTemplate<String, String> redisTemplate;
    private final RedisKeyProperties redisKeyProperties;

    public SecurityConfig(
            FilterChainExceptionHandler filterChainExceptionHandler,
            RedisTemplate<String, String> redisTemplate,
            RedisKeyProperties redisKeyProperties
    ) {
        this.filterChainExceptionHandler = filterChainExceptionHandler;
        this.redisTemplate = redisTemplate;
        this.redisKeyProperties = redisKeyProperties;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity httpSecurity) throws Exception {
        httpSecurity.addFilterBefore(filterChainExceptionHandler, LogoutFilter.class);

        httpSecurity
                .cors(Customizer.withDefaults())
                .authorizeHttpRequests(request -> request
                        .requestMatchers(PUBLIC_ENDPOINT)
                        .permitAll()
                        .requestMatchers(HttpMethod.GET, PUBLIC_GET_ENDPOINT)
                        .permitAll()
                        .anyRequest()
                        .authenticated()
                )
                .csrf(AbstractHttpConfigurer::disable)
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(authenticationEntryPoint())
                )
                .oauth2ResourceServer(oauth2 ->
                                oauth2.jwt(jwtConfigurer -> jwtConfigurer.decoder(jwtDecoder())
                                                .jwtAuthenticationConverter(jwtAuthenticationConverter()))
                                        .authenticationEntryPoint(authenticationEntryPoint())
                );

        return httpSecurity.build();
    }

    @Bean
    JwtDecoder jwtDecoder() {
        return new JwtDecoder() {
            @Override
            public Jwt decode(String token) throws JwtException {
                try {
                    SignedJWT signedJWT = SignedJWT.parse(token);
                    JWSVerifier jwsVerifier = new MACVerifier(SIGNED_KEY.getBytes());

                    if (!signedJWT.verify(jwsVerifier)) {
                        throw new JwtException("Invalid signature");
                    }

                    if (redisTemplate.opsForValue().get(redisKeyProperties.logoutTokenKey(token)) != null) {
                        throw new JwtException("Token logged out");
                    }

                    if (signedJWT.getJWTClaimsSet().getExpirationTime().before(Date.from(Instant.now()))) {
                        throw new JwtException("Token expired");
                    }

                    return new Jwt(token,
                            signedJWT.getJWTClaimsSet().getIssueTime().toInstant(),
                            signedJWT.getJWTClaimsSet().getExpirationTime().toInstant(),
                            signedJWT.getHeader().toJSONObject(),
                            signedJWT.getJWTClaimsSet().getClaims()
                    );
                } catch (ParseException e) {
                    throw new JwtException("Invalid token");
                } catch (JOSEException e) {
                    throw new RuntimeException(e);
                }
            }
        };
    }

    @Bean
    JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter jwtGrantedAuthoritiesConverter = new JwtGrantedAuthoritiesConverter();
        jwtGrantedAuthoritiesConverter.setAuthorityPrefix("");

        JwtAuthenticationConverter jwtAuthenticationConverter = new JwtAuthenticationConverter();
        jwtAuthenticationConverter.setJwtGrantedAuthoritiesConverter(jwtGrantedAuthoritiesConverter);

        return jwtAuthenticationConverter;
    }

    @Bean
    public AuthenticationEntryPoint authenticationEntryPoint() {
        return (request, response, authException) -> {
            ErrorCode error = ErrorCode.UNAUTHENTICATED;

            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);

            ApiResponse<?> apiResponse = ApiResponse.builder()
                    .code(error.getCode())
                    .message(error.getMessage())
                    .build();

            ObjectMapper objectMapper = new ObjectMapper();

            response.getWriter().write(objectMapper.writeValueAsString(apiResponse));
            response.flushBuffer();
        };
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(10);
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        // 1. Cho phép Frontend ở localhost:5173 truy cập.
        // (Nếu sau này deploy frontend lên domain thật, bạn thêm domain đó vào đây)
//        configuration.setAllowedOrigins(Collections.singletonList("http://localhost:5173"));
        configuration.setAllowedOriginPatterns(Collections.singletonList("*"));

        // 2. Cho phép các phương thức HTTP (Đặc biệt phải có OPTIONS cho preflight request)
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));

        // 3. Cho phép tất cả các header (Authorization, Content-Type...)
        configuration.setAllowedHeaders(Collections.singletonList("*"));

        // 4. Cho phép gửi thông tin xác thực (Cookies, JWT Token...)
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        // 5. Áp dụng cấu hình này cho toàn bộ endpoint (/**)
        source.registerCorsConfiguration("/**", configuration);

        return source;
    }
}
