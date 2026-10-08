package com.sprint.mission.discodeit.config;

import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class AsyncContextIntegrationTest {

    @Autowired
    @Qualifier("taskExecutor")
    private ThreadPoolTaskExecutor taskExecutor;


    @Test
    void 작업_스레드에_RequestId와_인증정보를_전달한다() throws Exception {
        Map<String, String> previousMdc = MDC.getCopyOfContextMap();
        SecurityContext previousContext = SecurityContextHolder.getContext();

        Authentication authentication = new UsernamePasswordAuthenticationToken(
                "test-user",
                null,
                List.of(new SimpleGrantedAuthority("ROLE_USER"))
        );

        Future<ContextSnapshot> future;

        try {
            MDC.put("requestId", "async-test-request");

            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(authentication);
            SecurityContextHolder.setContext(context);

            future = taskExecutor.submit(() -> new ContextSnapshot(
                    Thread.currentThread().getName(),
                    MDC.get("requestId"),
                    SecurityContextHolder.getContext().getAuthentication()
            ));
        } finally {
            if (previousMdc == null) {
                MDC.clear();
            } else {
                MDC.setContextMap(previousMdc);
            }
            SecurityContextHolder.setContext(previousContext);
        }

        ContextSnapshot result = future.get(5, TimeUnit.SECONDS);

        assertThat(result.threadName()).startsWith("async-");
        assertThat(result.requestId()).isEqualTo("async-test-request");
        assertThat(result.authentication()).isNotNull();
        assertThat(result.authentication().getName()).isEqualTo("test-user");
        assertThat(result.authentication().isAuthenticated()).isTrue();
        assertThat(result.authentication().getAuthorities())
                .extracting(authority -> authority.getAuthority())
                .containsExactly("ROLE_USER");

    }

    private record ContextSnapshot(
            String threadName,
            String requestId,
            Authentication authentication
    ){

    }






}
