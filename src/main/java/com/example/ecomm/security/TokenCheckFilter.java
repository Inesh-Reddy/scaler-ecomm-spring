package com.example.ecomm.security;

import com.example.ecomm.dtos.AuthUserDto;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.restclient.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class TokenCheckFilter extends OncePerRequestFilter {

    private final RestTemplate restTemplate;
    private final String authServiceUrl;

    public TokenCheckFilter(RestTemplateBuilder restTemplateBuilder,
                            @Value("${auth.service.url}") String authServiceUrl) {
        this.restTemplate = restTemplateBuilder.build();
        this.authServiceUrl = authServiceUrl;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (request.getMethod().equals("GET")) {
            filterChain.doFilter(request, response);
            return;
        }

        String header = request.getHeader("Authorization");
        if (header == null) {
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            return;
        }

        AuthUserDto user;
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", header);
            ResponseEntity<AuthUserDto> answer = restTemplate.exchange(
                    authServiceUrl + "/auth/validateToken", HttpMethod.GET,
                    new HttpEntity<>(headers), AuthUserDto.class);
            user = answer.getBody();
        } catch (HttpClientErrorException.Unauthorized e) {
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            return;
        } catch (RestClientException e) {
            response.setStatus(HttpStatus.SERVICE_UNAVAILABLE.value());
            return;
        }

        if (request.getMethod().equals("DELETE") && !"ADMIN".equals(user.getRole())) {
            response.setStatus(HttpStatus.FORBIDDEN.value());
            return;
        }

        filterChain.doFilter(request, response);
    }
}
