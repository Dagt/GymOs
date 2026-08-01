package com.gymos.automations.application;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.function.IntSupplier;
import org.springframework.stereotype.Component;

@Component
public class AutomationMetrics {
    private final MeterRegistry meterRegistry;

    public AutomationMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public int measure(String stage, IntSupplier operation) {
        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            int count = operation.getAsInt();
            meterRegistry.counter("gymos.automations.stage.items", "stage", stage, "outcome", "success")
                    .increment(count);
            return count;
        } catch (RuntimeException exception) {
            meterRegistry.counter("gymos.automations.stage.runs", "stage", stage, "outcome", "failure")
                    .increment();
            throw exception;
        } finally {
            sample.stop(meterRegistry.timer("gymos.automations.stage.duration", "stage", stage));
            meterRegistry.counter("gymos.automations.stage.runs", "stage", stage, "outcome", "success")
                    .increment();
        }
    }
}
