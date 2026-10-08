package com.badfisher.temu.server.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** Stateless service authentication. Only the health endpoint permits anonymous access. */
@Configuration
public class ApiKeySecurityConfiguration {

	private final byte[] apiKey;

	public ApiKeySecurityConfiguration(@Value("${temu.security.api-key:}") String apiKey) {
		if (apiKey == null || apiKey.trim().length() < 32) {
			throw new IllegalArgumentException("TEMU_API_KEY must contain at least 32 characters");
		}
		this.apiKey = apiKey.getBytes(StandardCharsets.UTF_8);
	}

	@Bean
	public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		http.csrf().disable()
			.sessionManagement().sessionCreationPolicy(SessionCreationPolicy.STATELESS)
			.and().authorizeRequests()
			.antMatchers("/actuator/health").permitAll()
			.anyRequest().authenticated()
			.and().exceptionHandling().authenticationEntryPoint((request, response, exception) -> {
				response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
				response.setContentType("application/json;charset=UTF-8");
				response.getWriter().write("{\"code\":401,\"message\":\"Authentication required\"}");
			});
		http.addFilterBefore(new ApiKeyFilter(), UsernamePasswordAuthenticationFilter.class);
		return http.build();
	}

	private class ApiKeyFilter extends OncePerRequestFilter {

		@Override
		protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
				FilterChain chain) throws ServletException, IOException {
			String supplied = request.getHeader("X-Api-Key");
			if (supplied != null && MessageDigest.isEqual(apiKey, supplied.getBytes(StandardCharsets.UTF_8))) {
				SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
						"service", null, AuthorityUtils.createAuthorityList("ROLE_SERVICE")));
			}
			chain.doFilter(request, response);
		}

	}

}
