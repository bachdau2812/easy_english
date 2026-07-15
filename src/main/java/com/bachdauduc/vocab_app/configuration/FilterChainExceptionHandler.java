package com.bachdauduc.vocab_app.configuration;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.web.servlet.ModelAndView;

import java.io.IOException;

@Component
public class FilterChainExceptionHandler extends OncePerRequestFilter {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(FilterChainExceptionHandler.class);

    private final HandlerExceptionResolver exceptionResolver;

    public FilterChainExceptionHandler(
            @Qualifier("handlerExceptionResolver")
            HandlerExceptionResolver exceptionResolver
    ) {
        this.exceptionResolver = exceptionResolver;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        try {
            filterChain.doFilter(request, response);
        } catch (Exception exception) {
            LOGGER.error(
                    "Exception occurred in Spring Security filter chain. Method: {}, URI: {}",
                    request.getMethod(),
                    request.getRequestURI(),
                    exception
            );

            ModelAndView resolved = exceptionResolver.resolveException(
                    request,
                    response,
                    null,
                    exception
            );

            if (resolved == null) {
                rethrowException(exception);
            }
        }
    }

    private void rethrowException(Exception exception)
            throws ServletException, IOException {

        if (exception instanceof IOException ioException) {
            throw ioException;
        }

        if (exception instanceof ServletException servletException) {
            throw servletException;
        }

        throw new ServletException(
                "Unhandled exception in Spring Security filter chain",
                exception
        );
    }
}