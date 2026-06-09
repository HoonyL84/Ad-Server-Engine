package io.hoony.adserver.config;

import io.micrometer.common.KeyValue;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;

@Component
@RequiredArgsConstructor
public class TracingSupport {

    private final ObservationRegistry observationRegistry;

    public <T> T observe(String name, Supplier<T> supplier) {
        return Observation.createNotStarted(name, observationRegistry)
                .observe(supplier);
    }

    public <T> T observe(String name, String key, String value, Supplier<T> supplier) {
        return Observation.createNotStarted(name, observationRegistry)
                .lowCardinalityKeyValue(KeyValue.of(key, value))
                .observe(supplier);
    }

    public void observe(String name, Runnable runnable) {
        Observation.createNotStarted(name, observationRegistry)
                .observe(runnable);
    }

    public void observe(String name, String key, String value, Runnable runnable) {
        Observation.createNotStarted(name, observationRegistry)
                .lowCardinalityKeyValue(KeyValue.of(key, value))
                .observe(runnable);
    }
}
