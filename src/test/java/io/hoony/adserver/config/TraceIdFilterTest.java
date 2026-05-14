package io.hoony.adserver.config;

import jakarta.servlet.ServletException;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class TraceIdFilterTest {

    private final TraceIdFilter filter = new TraceIdFilter();

    @Test
    void usesIncomingTraceId() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/ads/serve");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> traceIdInChain = new AtomicReference<>();

        request.addHeader(TraceIdFilter.TRACE_ID_HEADER, "trace-123");

        filter.doFilter(request, response, (req, res) ->
                traceIdInChain.set(MDC.get(TraceIdFilter.TRACE_ID)));

        assertThat(traceIdInChain.get()).isEqualTo("trace-123");
        assertThat(response.getHeader(TraceIdFilter.TRACE_ID_HEADER)).isEqualTo("trace-123");
        assertThat(MDC.get(TraceIdFilter.TRACE_ID)).isNull();
    }

    @Test
    void createsTraceIdWhenHeaderIsMissing() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/ads/serve");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> traceIdInChain = new AtomicReference<>();

        filter.doFilter(request, response, (req, res) ->
                traceIdInChain.set(MDC.get(TraceIdFilter.TRACE_ID)));

        assertThat(traceIdInChain.get()).isNotBlank();
        assertThat(response.getHeader(TraceIdFilter.TRACE_ID_HEADER)).isEqualTo(traceIdInChain.get());
        assertThat(MDC.get(TraceIdFilter.TRACE_ID)).isNull();
    }
}
