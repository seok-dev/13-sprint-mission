package com.sprint.mission.discodeit.config;

import org.slf4j.MDC;
import org.springframework.core.task.TaskDecorator;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Map;

public class ContextTaskDecorator implements TaskDecorator {

    @Override
    public Runnable decorate(Runnable runnable) {
        // 작업을 제출하는 스레드에서 정보를 보관한다.
        Map<String, String> capturedMdc = MDC.getCopyOfContextMap();
        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();

        return () -> {
            // 작업을 실행하는 스레드의 기존 상태를 보관한다.
            Map<String, String> previousMdc = MDC.getCopyOfContextMap();
            SecurityContext previousContext =
                    SecurityContextHolder.getContext();

            try {
                applyMdc(capturedMdc);

                SecurityContext context =
                        SecurityContextHolder.createEmptyContext();
                context.setAuthentication(authentication);
                SecurityContextHolder.setContext(context);

                runnable.run();
            } finally {
                applyMdc(previousMdc);
                SecurityContextHolder.setContext(previousContext);
            }
        };
    }

    private void applyMdc(Map<String, String> contextMdc) {
        if (contextMdc == null) {
            MDC.clear();
        } else {
            MDC.setContextMap(contextMdc);
        }
    }
}
