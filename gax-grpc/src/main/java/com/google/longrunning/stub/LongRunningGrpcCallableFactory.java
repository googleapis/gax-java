/*
 * Copyright 2018, Google LLC All rights reserved.
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
package com.google.longrunning.stub;

import com.google.api.core.ApiFuture;
import com.google.api.core.BetaApi;
import com.google.api.gax.grpc.GrpcCallSettings;
import com.google.api.gax.grpc.GrpcCallableFactory;
import com.google.api.gax.rpc.ApiCallContext;
import com.google.api.gax.rpc.ApiException;
import com.google.api.gax.rpc.ClientContext;
import com.google.api.gax.rpc.UnaryCallSettings;
import com.google.api.gax.rpc.UnaryCallable;
import com.google.common.base.Preconditions;

@BetaApi("The surface for use by generated code is not stable yet and may change in the future.")
public class LongRunningGrpcCallableFactory extends GrpcCallableFactory {
  // This class is intentionally empty to allow manual modifications to the call stack.

  // Code below here is manually added:

  @Override
  protected <RequestT, ResponseT> UnaryCallable<RequestT, ResponseT> createDirectUnaryCallable(
      GrpcCallSettings<RequestT, ResponseT> grpcCallSettings
  ) {
    return new SomeNewAlternativeToDirectCallable<>(grpcCallSettings.getMethodDescriptor());
  }

  @Override
  protected <RequestT, ResponseT> UnaryCallable<RequestT, ResponseT> createBaseUnaryCallable(
      UnaryCallable<RequestT, ResponseT> callable,
      GrpcCallSettings<RequestT, ResponseT> grpcCallSettings,
      UnaryCallSettings<?, ?> callSettings,
      ClientContext clientContext) {

    callable = super.createBaseUnaryCallable(callable, grpcCallSettings, callSettings, clientContext);

    return new ExceptionTransformingCallable<>(callable, new ApiExceptionTransformation() {
      @Override
      public ApiException transform(ApiException e) {
        if (someConditionOn(e)) {
          throw new CustomException(e);
        } else {
          throw e;
        }
      }
    });
  }

  public interface ApiExceptionTransformation {
    ApiException transform(ApiException e);
  }

  class ExceptionTransformingCallable<RequestT, ResponseT> extends UnaryCallable<RequestT, ResponseT> {
    private final UnaryCallable<RequestT, ResponseT> callable;
    private final ApiExceptionTransformation transformation;

    ExceptionTransformingCallable(
        UnaryCallable<RequestT, ResponseT> callable,
        ApiExceptionTransformation transformation) {
      this.callable = Preconditions.checkNotNull(callable);
      this.transformation = Preconditions.checkNotNull(transformation);
    }

    @Override
    public ApiFuture<ResponseT> futureCall(RequestT request, ApiCallContext context) {
      try {
        return callable.futureCall(request, context);
      } catch (ApiException e) {
        throw transformation.transform(e);
      }
    }
  }
}
