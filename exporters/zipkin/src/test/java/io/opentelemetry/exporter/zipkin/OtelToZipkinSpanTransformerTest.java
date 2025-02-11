/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.exporter.zipkin;

import static io.opentelemetry.api.common.AttributeKey.booleanArrayKey;
import static io.opentelemetry.api.common.AttributeKey.booleanKey;
import static io.opentelemetry.api.common.AttributeKey.doubleArrayKey;
import static io.opentelemetry.api.common.AttributeKey.doubleKey;
import static io.opentelemetry.api.common.AttributeKey.longArrayKey;
import static io.opentelemetry.api.common.AttributeKey.longKey;
import static io.opentelemetry.api.common.AttributeKey.stringArrayKey;
import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static io.opentelemetry.exporter.zipkin.ZipkinTestUtil.spanBuilder;
import static io.opentelemetry.exporter.zipkin.ZipkinTestUtil.zipkinSpan;
import static io.opentelemetry.exporter.zipkin.ZipkinTestUtil.zipkinSpanBuilder;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.sdk.common.InstrumentationScopeInfo;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.data.StatusData;
import io.opentelemetry.semconv.resource.attributes.ResourceAttributes;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import java.net.InetAddress;
import java.util.Arrays;
import java.util.Collections;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import zipkin2.Endpoint;
import zipkin2.Span;

class OtelToZipkinSpanTransformerTest {

  private OtelToZipkinSpanTransformer transformer;
  private InetAddress localIp;

  @BeforeEach
  void setup() {
    localIp = mock(InetAddress.class);
    transformer = OtelToZipkinSpanTransformer.create(() -> localIp);
  }

  @Test
  void generateSpan_remoteParent() {
    SpanData data = spanBuilder().build();

    assertThat(transformer.generateSpan(data))
        .isEqualTo(
            zipkinSpanBuilder(Span.Kind.SERVER, localIp)
                .putTag(OtelToZipkinSpanTransformer.OTEL_STATUS_CODE, "OK")
                .build());
  }

  @Test
  void generateSpan_subMicroDurations() {
    SpanData data =
        spanBuilder()
            .setStartEpochNanos(1505855794_194009601L)
            .setEndEpochNanos(1505855794_194009999L)
            .build();

    Span expected =
        zipkinSpanBuilder(Span.Kind.SERVER, localIp)
            .putTag(OtelToZipkinSpanTransformer.OTEL_STATUS_CODE, "OK")
            .duration(1)
            .build();
    assertThat(transformer.generateSpan(data)).isEqualTo(expected);
  }

  @Test
  void generateSpan_ServerKind() {
    SpanData data = spanBuilder().setKind(SpanKind.SERVER).build();

    assertThat(transformer.generateSpan(data))
        .isEqualTo(
            zipkinSpanBuilder(Span.Kind.SERVER, localIp)
                .putTag(OtelToZipkinSpanTransformer.OTEL_STATUS_CODE, "OK")
                .build());
  }

  @Test
  void generateSpan_ClientKind() {
    SpanData data = spanBuilder().setKind(SpanKind.CLIENT).build();

    assertThat(transformer.generateSpan(data))
        .isEqualTo(
            zipkinSpanBuilder(Span.Kind.CLIENT, localIp)
                .putTag(OtelToZipkinSpanTransformer.OTEL_STATUS_CODE, "OK")
                .build());
  }

  @Test
  void generateSpan_InternalKind() {
    SpanData data = spanBuilder().setKind(SpanKind.INTERNAL).build();

    assertThat(transformer.generateSpan(data))
        .isEqualTo(
            zipkinSpanBuilder(null, localIp)
                .putTag(OtelToZipkinSpanTransformer.OTEL_STATUS_CODE, "OK")
                .build());
  }

  @Test
  void generateSpan_ConsumeKind() {
    SpanData data = spanBuilder().setKind(SpanKind.CONSUMER).build();

    assertThat(transformer.generateSpan(data))
        .isEqualTo(
            zipkinSpanBuilder(Span.Kind.CONSUMER, localIp)
                .putTag(OtelToZipkinSpanTransformer.OTEL_STATUS_CODE, "OK")
                .build());
  }

  @Test
  void generateSpan_ProducerKind() {
    SpanData data = spanBuilder().setKind(SpanKind.PRODUCER).build();

    assertThat(transformer.generateSpan(data))
        .isEqualTo(
            zipkinSpanBuilder(Span.Kind.PRODUCER, localIp)
                .putTag(OtelToZipkinSpanTransformer.OTEL_STATUS_CODE, "OK")
                .build());
  }

  @Test
  void generateSpan_ResourceServiceNameMapping() {
    Resource resource =
        Resource.create(Attributes.of(ResourceAttributes.SERVICE_NAME, "super-zipkin-service"));
    SpanData data = spanBuilder().setResource(resource).build();

    Endpoint expectedEndpoint =
        Endpoint.newBuilder().serviceName("super-zipkin-service").ip(localIp).build();
    Span expectedZipkinSpan =
        zipkinSpan(Span.Kind.SERVER, localIp).toBuilder()
            .localEndpoint(expectedEndpoint)
            .putTag(OtelToZipkinSpanTransformer.OTEL_STATUS_CODE, "OK")
            .build();
    assertThat(transformer.generateSpan(data)).isEqualTo(expectedZipkinSpan);
  }

  @Test
  void generateSpan_defaultResourceServiceName() {
    SpanData data = spanBuilder().setResource(Resource.empty()).build();

    Endpoint expectedEndpoint =
        Endpoint.newBuilder()
            .serviceName(Resource.getDefault().getAttribute(ResourceAttributes.SERVICE_NAME))
            .ip(localIp)
            .build();
    Span expectedZipkinSpan =
        zipkinSpan(Span.Kind.SERVER, localIp).toBuilder()
            .localEndpoint(expectedEndpoint)
            .putTag(OtelToZipkinSpanTransformer.OTEL_STATUS_CODE, "OK")
            .build();
    assertThat(transformer.generateSpan(data)).isEqualTo(expectedZipkinSpan);
  }

  @Test
  void generateSpan_WithAttributes() {
    Attributes attributes =
        Attributes.builder()
            .put(stringKey("string"), "string value")
            .put(booleanKey("boolean"), false)
            .put(longKey("long"), 9999L)
            .put(doubleKey("double"), 222.333d)
            .put(booleanArrayKey("booleanArray"), Arrays.asList(true, false))
            .put(stringArrayKey("stringArray"), Collections.singletonList("Hello"))
            .put(doubleArrayKey("doubleArray"), Arrays.asList(32.33d, -98.3d))
            .put(longArrayKey("longArray"), Arrays.asList(33L, 999L))
            .build();
    SpanData data =
        spanBuilder()
            .setAttributes(attributes)
            .setTotalAttributeCount(28)
            .setTotalRecordedEvents(3)
            .setKind(SpanKind.CLIENT)
            .build();

    assertThat(transformer.generateSpan(data))
        .isEqualTo(
            zipkinSpan(Span.Kind.CLIENT, localIp).toBuilder()
                .putTag("string", "string value")
                .putTag("boolean", "false")
                .putTag("long", "9999")
                .putTag("double", "222.333")
                .putTag("booleanArray", "true,false")
                .putTag("stringArray", "Hello")
                .putTag("doubleArray", "32.33,-98.3")
                .putTag("longArray", "33,999")
                .putTag(OtelToZipkinSpanTransformer.OTEL_STATUS_CODE, "OK")
                .putTag(OtelToZipkinSpanTransformer.OTEL_DROPPED_ATTRIBUTES_COUNT, "20")
                .putTag(OtelToZipkinSpanTransformer.OTEL_DROPPED_EVENTS_COUNT, "1")
                .build());
  }

  @Test
  void generateSpan_WithInstrumentationLibraryInfo() {
    SpanData data =
        spanBuilder()
            .setInstrumentationScopeInfo(
                InstrumentationScopeInfo.create("io.opentelemetry.auto", "1.0.0", null))
            .setKind(SpanKind.CLIENT)
            .build();

    assertThat(transformer.generateSpan(data))
        .isEqualTo(
            zipkinSpan(Span.Kind.CLIENT, localIp).toBuilder()
                .putTag("otel.scope.name", "io.opentelemetry.auto")
                .putTag("otel.scope.version", "1.0.0")
                .putTag("otel.library.name", "io.opentelemetry.auto")
                .putTag("otel.library.version", "1.0.0")
                .putTag(OtelToZipkinSpanTransformer.OTEL_STATUS_CODE, "OK")
                .build());
  }

  @Test
  void generateSpan_AlreadyHasHttpStatusInfo() {
    Attributes attributes =
        Attributes.of(
            SemanticAttributes.HTTP_STATUS_CODE, 404L, stringKey("error"), "A user provided error");
    SpanData data =
        spanBuilder()
            .setAttributes(attributes)
            .setKind(SpanKind.CLIENT)
            .setStatus(StatusData.error())
            .setTotalAttributeCount(2)
            .build();

    assertThat(transformer.generateSpan(data))
        .isEqualTo(
            zipkinSpan(Span.Kind.CLIENT, localIp).toBuilder()
                .clearTags()
                .putTag(SemanticAttributes.HTTP_STATUS_CODE.getKey(), "404")
                .putTag(OtelToZipkinSpanTransformer.OTEL_STATUS_CODE, "ERROR")
                .putTag("error", "A user provided error")
                .build());
  }

  @Test
  void generateSpan_WithRpcTimeoutErrorStatus_WithTimeoutErrorDescription() {
    Attributes attributes = Attributes.of(SemanticAttributes.RPC_SERVICE, "my service name");

    String errorMessage = "timeout";

    SpanData data =
        spanBuilder()
            .setStatus(StatusData.create(StatusCode.ERROR, errorMessage))
            .setAttributes(attributes)
            .setTotalAttributeCount(1)
            .build();

    assertThat(transformer.generateSpan(data))
        .isEqualTo(
            zipkinSpan(Span.Kind.SERVER, localIp).toBuilder()
                .putTag(SemanticAttributes.RPC_SERVICE.getKey(), "my service name")
                .putTag(OtelToZipkinSpanTransformer.OTEL_STATUS_CODE, "ERROR")
                .putTag(OtelToZipkinSpanTransformer.STATUS_ERROR.getKey(), errorMessage)
                .build());
  }

  @Test
  void generateSpan_WithRpcErrorStatus_WithEmptyErrorDescription() {
    Attributes attributes = Attributes.of(SemanticAttributes.RPC_SERVICE, "my service name");

    SpanData data =
        spanBuilder()
            .setStatus(StatusData.create(StatusCode.ERROR, ""))
            .setAttributes(attributes)
            .setTotalAttributeCount(1)
            .build();

    assertThat(transformer.generateSpan(data))
        .isEqualTo(
            zipkinSpan(Span.Kind.SERVER, localIp).toBuilder()
                .putTag(SemanticAttributes.RPC_SERVICE.getKey(), "my service name")
                .putTag(OtelToZipkinSpanTransformer.OTEL_STATUS_CODE, "ERROR")
                .putTag(OtelToZipkinSpanTransformer.STATUS_ERROR.getKey(), "")
                .build());
  }

  @Test
  void generateSpan_WithRpcUnsetStatus() {
    Attributes attributes = Attributes.of(SemanticAttributes.RPC_SERVICE, "my service name");

    SpanData data =
        spanBuilder()
            .setStatus(StatusData.create(StatusCode.UNSET, ""))
            .setAttributes(attributes)
            .setTotalAttributeCount(1)
            .build();

    assertThat(transformer.generateSpan(data))
        .isEqualTo(
            zipkinSpan(Span.Kind.SERVER, localIp).toBuilder()
                .putTag(SemanticAttributes.RPC_SERVICE.getKey(), "my service name")
                .build());
  }
}
