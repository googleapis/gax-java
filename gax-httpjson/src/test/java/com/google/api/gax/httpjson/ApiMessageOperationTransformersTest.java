/*
 * Copyright 2019 Google LLC
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *
 *     * Redistributions of source code must retain the above copyright
 * notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above
 * copyright notice, this list of conditions and the following disclaimer
 * in the documentation and/or other materials provided with the
 * distribution.
 *     * Neither the name of Google LLC nor the names of its
 * contributors may be used to endorse or promote products derived from
 * this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.google.api.gax.httpjson;

import com.google.api.gax.httpjson.ApiMessageOperationTransformers.MetadataTransformer;
import com.google.api.gax.httpjson.ApiMessageOperationTransformers.ResponseTransformer;
import com.google.api.gax.httpjson.testing.FakeApiMessage;
import com.google.api.gax.longrunning.OperationSnapshot;
import com.google.api.gax.rpc.ApiException;
import com.google.api.gax.rpc.StatusCode;
import com.google.api.gax.rpc.StatusCode.Code;
import com.google.api.gax.rpc.UnavailableException;
import com.google.common.collect.ImmutableMap;
import com.google.common.truth.Truth;
import java.util.List;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for ApiMessageOperationTransformers. */
@RunWith(JUnit4.class)
public class ApiMessageOperationTransformersTest {
  @Rule public ExpectedException thrown = ExpectedException.none();

  @Test
  public void testResponseTransformer() {
    ResponseTransformer<EmptyMessage> transformer = ResponseTransformer.create(EmptyMessage.class);
    EmptyMessage emptyResponse = EmptyMessage.getDefaultInstance();

    FakeMetadataMessage metadata = new FakeMetadataMessage(Status.PENDING, Code.OK);
    OperationSnapshot operationSnapshot =
        new OperationSnapshotImpl(
            new FakeOperationMessage<>("Pending; no response method", emptyResponse, metadata));

    Truth.assertThat(transformer.apply(operationSnapshot)).isEqualTo(emptyResponse);
  }

  @Test
  public void testResponseTransformer_exception() {
    thrown.expect(UnavailableException.class);
    ResponseTransformer<EmptyMessage> transformer = ResponseTransformer.create(EmptyMessage.class);
    EmptyMessage emptyResponse = EmptyMessage.getDefaultInstance();
    FakeMetadataMessage metadata = new FakeMetadataMessage(Status.PENDING, Code.UNAVAILABLE);
    OperationSnapshot operationSnapshot =
        new OperationSnapshotImpl(
            new FakeOperationMessage<>("Unavailable; no response method", emptyResponse, metadata));

    Truth.assertThat(transformer.apply(operationSnapshot)).isEqualTo(emptyResponse);
  }

  @Test
  public void testResponseTransformer_mismatchedTypes() {
    thrown.expect(ApiException.class);
    thrown.expectMessage("cannot be cast");
    ResponseTransformer<EmptyMessage> transformer = ResponseTransformer.create(EmptyMessage.class);
    FakeMetadataMessage metadata = new FakeMetadataMessage(Status.PENDING, Code.OK);
    ApiMessage bananaResponse =
        new FakeApiMessage(ImmutableMap.<String, Object>of("name", "banana"), null, null);
    EmptyMessage emptyResponse = EmptyMessage.getDefaultInstance();
    OperationSnapshot operationSnapshot =
        new OperationSnapshotImpl(
            new FakeOperationMessage<>("No response method", bananaResponse, metadata));
    Truth.assertThat(transformer.apply(operationSnapshot)).isEqualTo(emptyResponse);
  }

  @Test
  public void testMetadataTransformer() {
    MetadataTransformer<FakeMetadataMessage> transformer =
        MetadataTransformer.create(FakeMetadataMessage.class);
    EmptyMessage returnType = EmptyMessage.getDefaultInstance();
    FakeMetadataMessage metadataMessage = new FakeMetadataMessage(Status.PENDING, Code.OK);
    FakeOperationMessage operation = new FakeOperationMessage<>("foo", returnType, metadataMessage);
    OperationSnapshot operationSnapshot = new OperationSnapshotImpl(operation);
    Truth.assertThat(transformer.apply(operationSnapshot)).isEqualTo(metadataMessage);
  }

  @Test
  public void testMetadataTransformer_mismatchedTypes() {
    thrown.expect(ApiException.class);
    thrown.expectMessage("cannot be cast");
    MetadataTransformer<FakeOperationMessage> transformer =
        MetadataTransformer.create(FakeOperationMessage.class);
    FakeMetadataMessage metadataMessage = new FakeMetadataMessage(Status.PENDING, Code.OK);
    ApiMessage bananaResponse =
        new FakeApiMessage(ImmutableMap.<String, Object>of("name", "banana"), null, null);
    FakeOperationMessage metadata =
        new FakeOperationMessage<>("No response method", bananaResponse, metadataMessage);
    OperationSnapshot operationSnapshot = new OperationSnapshotImpl(metadata);
    Truth.assertThat(transformer.apply(operationSnapshot)).isEqualTo(bananaResponse);
  }

  private enum Status {
    PENDING,
    DONE
  }

  private static class FakeMetadataMessage<ResponseT extends ApiMessage> implements ApiMessage {

    private final Status status;
    private final Code code;

    public FakeMetadataMessage(Status status, Code code) {
      this.status = status;
      this.code = code;
    }

    public Object getFieldValue(String fieldName) {
      if ("status".equals(fieldName)) {
        return status;
      }
      if ("code".equals(fieldName)) {
        return code;
      }
      return null;
    }

    public List<String> getFieldMask() {
      return null;
    }

    public ApiMessage getApiMessageRequestBody() {
      return null;
    }
  }

  private static class FakeOperationMessage<
          ResponseT extends ApiMessage, MetadataT extends ApiMessage>
      implements ApiMessage {

    private final String name;
    private final ResponseT responseT;
    private final MetadataT metadata;

    public FakeOperationMessage(String name, ResponseT responseT, MetadataT metadata) {
      this.name = name;
      this.responseT = responseT;
      this.metadata = metadata;
    }

    public Object getFieldValue(String fieldName) {
      if ("name".equals(fieldName)) {
        return name;
      }
      if ("responseT".equals(fieldName)) {
        return responseT;
      }
      if ("metadata".equals(fieldName)) {
        return metadata;
      }
      return null;
    }

    public List<String> getFieldMask() {
      return null;
    }

    public ResponseT getApiMessageRequestBody() {
      return responseT;
    }
  }

  private static class OperationSnapshotImpl implements OperationSnapshot {

    private final FakeOperationMessage operation;

    public OperationSnapshotImpl(FakeOperationMessage operation) {
      this.operation = operation;
    }

    @Override
    public String getName() {
      return (String) operation.getFieldValue("name");
    }

    @Override
    public Object getMetadata() {
      return operation.metadata;
    }

    @Override
    public boolean isDone() {
      return operation.metadata.getFieldValue("status") != Status.PENDING;
    }

    @Override
    public Object getResponse() {
      return operation.getApiMessageRequestBody();
    }

    @Override
    public StatusCode getErrorCode() {
      return HttpJsonStatusCode.of((Code) operation.metadata.getFieldValue("code"));
    }

    @Override
    public String getErrorMessage() {
      return ((Code) operation.metadata.getFieldValue("code")).name();
    }
  }
}
