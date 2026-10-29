package dev.nagarjuna.fulfilment.tracing;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.propagation.Propagator;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Carries a trace across work that happens later, on another thread, such as a row written now
 * and sent by a poller a few seconds afterwards. The W3C traceparent is saved with the row and the
 * poller starts its span as a child of it, so the whole flow shows up as one trace.
 */
@Component
public class TraceParents {

    private static final String HEADER = "traceparent";

    private final Tracer tracer;
    private final Propagator propagator;

    public TraceParents(Tracer tracer, ObjectProvider<Propagator> propagator) {
        this.tracer = tracer;
        this.propagator = propagator.getIfAvailable(() -> Propagator.NOOP);
    }

    /** The current span as a traceparent string, or null when nothing is being traced. */
    public String current() {
        TraceContext context = tracer.currentTraceContext().context();
        if (context == null) return null;
        Map<String, String> carrier = new HashMap<>();
        propagator.inject(context, carrier, Map::put);
        return carrier.get(HEADER);
    }

    /** Starts a span under the saved parent, or a new trace if there isn't one. The caller ends it. */
    public Span startChild(String traceParent, String name) {
        Span.Builder builder = traceParent == null
                ? tracer.spanBuilder()
                : propagator.extract(Map.of(HEADER, traceParent), Map::get);
        return builder.name(name).start();
    }

    public Tracer tracer() {
        return tracer;
    }
}
