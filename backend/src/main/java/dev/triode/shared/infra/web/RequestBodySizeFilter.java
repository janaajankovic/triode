package dev.triode.shared.infra.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.web.servlet.ModelAndView;

/**
 * Rejects requests whose declared {@code Content-Length} exceeds {@code triode.api.max-request-body-bytes} with 413,
 * before any byte of the body is read or parsed.
 *
 * <p>Bodies without a Content-Length (chunked) are capped by Jackson's {@code max-document-length} read constraint,
 * set to the same property in {@code application.properties}; the global handler maps that to 413 as well.
 *
 * <p>Filters run outside Spring MVC, so the exception is handed to MVC's {@link HandlerExceptionResolver}: the problem
 * detail still comes from the one {@link GlobalExceptionHandler}.
 */
@Component
class RequestBodySizeFilter extends OncePerRequestFilter {

    private final long maxBytes;
    private final HandlerExceptionResolver exceptionResolver;

    RequestBodySizeFilter(
            @Value("${triode.api.max-request-body-bytes}") long maxBytes,
            @Qualifier("handlerExceptionResolver") HandlerExceptionResolver exceptionResolver) {
        this.maxBytes = maxBytes;
        this.exceptionResolver = exceptionResolver;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long contentLength = request.getContentLengthLong();
        if (contentLength > maxBytes) {
            ModelAndView handled = exceptionResolver.resolveException(
                    request, response, null, new RequestBodyTooLargeException(contentLength, maxBytes));
            if (handled == null) {
                // No resolver took it: never let the request through or answer with an empty 200.
                response.sendError(HttpStatus.CONTENT_TOO_LARGE.value());
            }
            return;
        }
        chain.doFilter(request, response);
    }
}
