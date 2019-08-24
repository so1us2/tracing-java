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

package com.palantir.tracing;

import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

public final class RenderTracingExtension implements BeforeEachCallback, AfterEachCallback {

    private final SpanRenderer renderer = new SpanRenderer();

    @Override
    public void beforeEach(ExtensionContext context) {
        Tracer.setSampler(AlwaysSampler.INSTANCE);
        Tracer.subscribe("RenderTracingExtension", renderer);
    }

    @Override
    public void afterEach(ExtensionContext context) {
        // TODO(dfox): this will not behave well if things run in parallel
        Tracer.unsubscribe("RenderTracingExtension");

        renderer.output(
                context.getRequiredTestClass().getName().toString() + "#" + context.getRequiredTestMethod().getName(),
                HtmlOutputFile.createFile(context.getRequiredTestClass(), context.getRequiredTestMethod().getName()));
    }
}
