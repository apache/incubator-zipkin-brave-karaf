/*
 * Copyright 2016-2020 The OpenZipkin Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package io.zipkin.brave.exporter;

import brave.Tracing;
import brave.handler.SpanHandler;
import brave.sampler.RateLimitingSampler;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.metatype.annotations.Designate;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

@Component(
    immediate = true,
    name = "io.zipkin.brave"
)
@Designate(ocd = TracingExporter.Config.class)
public class TracingExporter {
  @Reference(cardinality = ReferenceCardinality.AT_LEAST_ONE) List<SpanHandler> spanHandlers;

  private Tracing tracing;
  private ServiceRegistration<Tracing> reg;

  @Activate
  public void activate(Config config, BundleContext context, Map<String, String> properties) {
    Tracing.Builder builder = Tracing.newBuilder()
        .localServiceName(config.localServiceName())
        .supportsJoin(config.supportsJoin())
        .traceId128Bit(config.traceId128Bit())
        .sampler(RateLimitingSampler.create(config.tracesPerSecond()));
    for (SpanHandler spanHandler : spanHandlers) {
      builder.addSpanHandler(spanHandler);
    }
    tracing = builder.build();
    reg =
        context.registerService(Tracing.class, tracing, new Hashtable<String, String>(properties));
  }

  @Deactivate public void deactive() {
    reg.unregister();
    if (tracing != null) tracing.close();
  }

  public static @ObjectClassDefinition(name = "Tracing") @interface Config {
    String localServiceName() default "unknown";

    boolean supportsJoin() default true;

    boolean traceId128Bit() default false;

    int tracesPerSecond() default 10;
  }
}
