package com.bachdauduc.vocab_app.configuration;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@Slf4j
public class TraceIdFilter extends OncePerRequestFilter {

    private static final String TRACE_ID_MDC_KEY = "traceId";
    private static final String TRACE_ID_HEADER = "X-Trace-Id";

    private static final Pattern VALID_TRACE_ID_PATTERN =
            Pattern.compile("^[a-zA-Z0-9._-]{1,100}$");

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        String traceId = resolveTraceId(request);

        MDC.put(TRACE_ID_MDC_KEY, traceId);
        response.setHeader(TRACE_ID_HEADER, traceId);

        long startTime = System.currentTimeMillis();
        log.info("HTTP request started: method={}, uri={}, query={}",
                request.getMethod(), request.getRequestURI(), request.getQueryString());

        try {
            filterChain.doFilter(request, response);
        } finally {
            long durationMs = System.currentTimeMillis() - startTime;
            log.info("HTTP request completed: method={}, uri={}, status={}, durationMs={}",
                    request.getMethod(), request.getRequestURI(), response.getStatus(), durationMs);
            MDC.remove(TRACE_ID_MDC_KEY);
        }
    }

    private String resolveTraceId(HttpServletRequest request) {
        String incomingTraceId = request.getHeader(TRACE_ID_HEADER);

        if (StringUtils.hasText(incomingTraceId)
                && VALID_TRACE_ID_PATTERN.matcher(incomingTraceId).matches()) {
            return incomingTraceId;
        }

        return UUID.randomUUID().toString();
    }
}
