package io.hoony.adserver.config;

import org.slf4j.MDC;

import java.util.Map;
import java.util.concurrent.Callable;

public final class MdcTraceUtils {

    private MdcTraceUtils() {
    }

    public static <T> Callable<T> withTraceContext(Callable<T> task) {
        Map<String, String> parentContext = MDC.getCopyOfContextMap();

        return () -> {
            Map<String, String> previousContext = MDC.getCopyOfContextMap();
            setMdcContext(parentContext);
            try {
                return task.call();
            } finally {
                setMdcContext(previousContext);
            }
        };
    }

    public static Runnable withTraceContext(Runnable task) {
        Map<String, String> parentContext = MDC.getCopyOfContextMap();
        return withTraceContext(parentContext, task);
    }

    public static Runnable withTraceContext(Map<String, String> parentContext, Runnable task) {
        return () -> {
            Map<String, String> previousContext = MDC.getCopyOfContextMap();
            setMdcContext(parentContext);
            try {
                task.run();
            } finally {
                setMdcContext(previousContext);
            }
        };
    }

    private static void setMdcContext(Map<String, String> context) {
        if (context == null || context.isEmpty()) {
            MDC.clear();
            return;
        }
        MDC.setContextMap(context);
    }
}
