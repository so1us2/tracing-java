/*
 * (c) Copyright 2019 Palantir Technologies Inc. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.palantir.tracing2.ergo;

import static org.assertj.core.api.Assertions.assertThat;

import com.palantir.tracing2.CompletedSpan;
import com.palantir.tracing2.Ids;
import com.palantir.tracing2.Span;
import com.palantir.tracing2.Spans;
import com.palantir.tracing2.Trace;
import com.palantir.tracing2.Traces;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests in this class both demonstrate anticipated usage and expected behavior of that usage. This test lives in a
 * distinct package from the tracing library to ensure that usage is representative of outside-this-library consumers.
 */
public final class TracingErgonomicsTests {

    @BeforeEach
    public void before() {
        Spans.clearAllConsumers();
    }

    @Test
    public void explicitTracing_rootSpans() {
        List<CompletedSpan> spans = new ArrayList<>();
        Consumer<CompletedSpan> listener = spans::add;
        Spans.register(listener);

        String traceId = Ids.randomId();
        Trace sampledTrace = Traces.create(traceId, true);
        try (Span ignored = sampledTrace.rootSpan("root1")) {
            // no-op
        }
        assertThat(spans).hasSize(1);
        assertThat(spans.get(0).opName()).isEqualTo("root1");
        assertThat(spans.get(0).parentId()).isEmpty();
        assertThat(spans.get(0).traceId()).isEqualTo(traceId);

        spans.clear();

        // neighboring root span maintains same traceId
        try (Span ignored = sampledTrace.rootSpan("root2")) {
            // no-op
        }
        assertThat(spans).hasSize(1);
        assertThat(spans.get(0).opName()).isEqualTo("root2");
        assertThat(spans.get(0).parentId()).isEmpty();
        assertThat(spans.get(0).traceId()).isEqualTo(traceId);
    }

    @Test
    public void explicitTracing_implicitChildSpans() {
        List<CompletedSpan> spans = new ArrayList<>();
        Consumer<CompletedSpan> listener = spans::add;
        Spans.register(listener);

        String traceId = Ids.randomId();
        Trace sampledTrace = Traces.create(traceId, true);
        try (Span ignored = sampledTrace.rootSpan("root")) {
            try (Span ignored2 = Spans.forCurrentTrace("child")) {
                // no-op
            }
        }
        assertThat(spans).hasSize(2);
        assertThat(spans.get(1).opName()).isEqualTo("root");
        assertThat(spans.get(1).parentId()).isEmpty();
        assertThat(spans.get(1).traceId()).isEqualTo(traceId);
        assertThat(spans.get(0).opName()).isEqualTo("child");
        assertThat(spans.get(0).parentId()).contains(spans.get(1).spanId());
        assertThat(spans.get(0).traceId()).isEqualTo(traceId);
    }

    @Test
    public void explicitTracing_transitThreads_child() throws InterruptedException {
        List<CompletedSpan> spans = new ArrayList<>();
        Consumer<CompletedSpan> listener = spans::add;
        Spans.register(listener);

        String traceId = Ids.randomId();
        Trace sampledTrace = Traces.create(traceId, true);
        try (Span rootSpan = sampledTrace.rootSpan("root")) {
            Thread thread = new Thread(() -> {
                try (Span ignored = rootSpan.child("child-in-new-thread")) {
                    // no-op
                }
            });
            thread.start();
            thread.join();
        }
        assertThat(spans).hasSize(2);
        assertThat(spans.get(1).opName()).isEqualTo("root");
        assertThat(spans.get(1).parentId()).isEmpty();
        assertThat(spans.get(1).traceId()).isEqualTo(traceId);
        assertThat(spans.get(0).opName()).isEqualTo("child-in-new-thread");
        assertThat(spans.get(0).parentId()).contains(spans.get(1).spanId());
        assertThat(spans.get(0).traceId()).isEqualTo(traceId);
    }

    @Test
    public void explicitTracing_transitThreads_sibling() throws InterruptedException {
        List<CompletedSpan> spans = new ArrayList<>();
        Consumer<CompletedSpan> listener = spans::add;
        Spans.register(listener);

        String traceId = Ids.randomId();
        Trace sampledTrace = Traces.create(traceId, true);
        try (Span rootSpan = sampledTrace.rootSpan("root")) {
            Span childSpan = rootSpan.child("child");
            Thread thread = new Thread(() -> {
                try (Span ignored = childSpan.sibling("sibling-in-new-thread")) {
                    // no-op
                }
            });
            thread.start();
            thread.join();
        }
        assertThat(spans).hasSize(3);
        assertThat(spans.get(2).opName()).isEqualTo("root");
        assertThat(spans.get(2).parentId()).isEmpty();
        assertThat(spans.get(2).traceId()).isEqualTo(traceId);
        assertThat(spans.get(0).opName()).isEqualTo("child");
        assertThat(spans.get(0).parentId()).contains(spans.get(2).spanId());
        assertThat(spans.get(0).traceId()).isEqualTo(traceId);
        assertThat(spans.get(1).opName()).isEqualTo("sibling-in-new-thread");
        assertThat(spans.get(1).parentId()).contains(spans.get(2).spanId());
        assertThat(spans.get(1).traceId()).isEqualTo(traceId);
    }
}