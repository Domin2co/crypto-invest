package com.cryptoinvest.common.web;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class SafeRequestLoggingFilterTest {
    @Test
    void logsApiStatusAndDurationWithoutQueryOrAuthorization() throws Exception {
        Logger logger = (Logger) LoggerFactory.getLogger(SafeRequestLoggingFilter.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        logger.setLevel(Level.INFO);
        try {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/health");
            request.setQueryString("token=query-secret");
            request.addHeader("Authorization", "Bearer header-secret");
            MockHttpServletResponse response = new MockHttpServletResponse();
            FilterChain chain = (req, res) -> ((MockHttpServletResponse) res).setStatus(200);

            new SafeRequestLoggingFilter().doFilter(request, response, chain);

            assertThat(appender.list).hasSize(1);
            String message = appender.list.get(0).getFormattedMessage();
            assertThat(message).contains("method=GET", "path=/api/health", "status=200", "duration_ms=")
                    .doesNotContain("query-secret", "header-secret", "token=");
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }
}
