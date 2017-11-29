/*
 * Copyright 2017, Google LLC All rights reserved.
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
package com.google.api.gax.grpc;

import com.google.api.gax.rpc.ApiStreamObserver;

/**
 * An {@link ApiStreamObserver} that wraps grpc's errors into {@link
 * com.google.api.gax.rpc.ApiException}s.
 *
 * <p>Package-private for internal use.
 *
 * @param <ResponseT>
 */
final class GrpcApiExceptionResponseObserver<ResponseT> implements ApiStreamObserver<ResponseT> {
  private final ApiStreamObserver<ResponseT> innerObserver;
  private final GrpcApiExceptionFactory exceptionFactory;

  GrpcApiExceptionResponseObserver(
      ApiStreamObserver<ResponseT> innerObserver, GrpcApiExceptionFactory exceptionFactory) {
    this.innerObserver = innerObserver;
    this.exceptionFactory = exceptionFactory;
  }

  @Override
  public void onNext(ResponseT value) {
    innerObserver.onNext(value);
  }

  @Override
  public void onError(Throwable t) {
    innerObserver.onError(exceptionFactory.create(t));
  }

  @Override
  public void onCompleted() {
    innerObserver.onCompleted();
  }
}
