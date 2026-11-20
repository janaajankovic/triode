package dev.triode.shared.infra.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.web.servlet.ModelAndView;

/** Unit test for the paths the web-slice tests cannot reach, such as no resolver handling the exception. */
class RequestBodySizeFilterTest {

    private static final long MAX = 100;

    private final HandlerExceptionResolver resolver = mock(HandlerExceptionResolver.class);
    private final FilterChain chain = mock(FilterChain.class);
    private final RequestBodySizeFilter filter = new RequestBodySizeFilter(MAX, resolver);
    private final MockHttpServletResponse response = new MockHttpServletResponse();

    private static MockHttpServletRequest requestWithBody(int bytes) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/tickets");
        request.setContent(new byte[bytes]);
        return request;
    }

    @Test
    void bodyWithinTheLimitPassesThrough() throws Exception {
        MockHttpServletRequest request = requestWithBody((int) MAX);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        verifyNoInteractions(resolver);
    }

    @Test
    void oversizedBodyIsHandedToTheExceptionResolver() throws Exception {
        MockHttpServletRequest request = requestWithBody((int) MAX + 1);
        when(resolver.resolveException(any(), any(), isNull(), any(RequestBodyTooLargeException.class)))
                .thenReturn(new ModelAndView());

        filter.doFilter(request, response, chain);

        verifyNoInteractions(chain);
        assertThat(response.isCommitted()).isFalse(); // the resolver wrote the response, not the fallback
    }

    @Test
    void oversizedBodyFallsBackTo413WhenNoResolverHandlesIt() throws Exception {
        MockHttpServletRequest request = requestWithBody((int) MAX + 1);
        when(resolver.resolveException(any(), any(), isNull(), any())).thenReturn(null);

        filter.doFilter(request, response, chain);

        verifyNoInteractions(chain);
        assertThat(response.getStatus()).isEqualTo(413);
        assertThat(response.isCommitted()).isTrue();
    }
}
