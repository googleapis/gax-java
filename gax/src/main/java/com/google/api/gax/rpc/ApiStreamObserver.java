/*
 * Copyright 2017 Google LLC
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
package com.google.api.gax.rpc;

/**
 * Receives notifications from an observable stream of messages.
 *
 * <p>It is used for sending messages in bidi (bidirectional) or client-streaming calls, or for
 * receiving messages in bidi or server-streaming calls.
 *
 * <p>For outgoing messages, an {@code ApiStreamObserver} is provided by GAX to the application, and
 * the application then provides the messages to send. For incoming messages, the application
 * implements the {@code ApiStreamObserver} and passes it to GAX, which then calls the observer with
 * the messages for the application to receive them.
 *
 * <p>Implementations are expected to be <a
 * href="http://www.ibm.com/developerworks/library/j-jtp09263/">thread-compatible</a>. Separate
 * {@code ApiStreamObserver}s do not need to be synchronized together; incoming and outgoing
 * directions are independent. Since individual {@code ApiStreamObserver}s are not thread-safe, if
 * multiple threads will be writing to a {@code ApiStreamObserver} concurrently, the application
 * must synchronize calls.
 *
 * <p>This interface is a fork of io.grpc.stub.StreamObserver to enable shadowing of Guava, and also
 * to allow for a transport-agnostic interface that doesn't depend on gRPC.
 */
public interface ApiStreamObserver<V> {
  /**
   * Receives a value from the stream.
   *
   * <p>Can be called many times but is never called after {@link #onError(Throwable)} or {@link
   * #onCompleted()} are called.
   *
   * <p>Clients may invoke onNext at most once for server streaming calls, but may receive many
   * onNext callbacks. Servers may invoke onNext at most once for client streaming calls, but may
   * receive many onNext callbacks.
   *
   * <p>If an exception is thrown by an implementation the caller is expected to terminate the
   * stream by calling {@link #onError(Throwable)} with the caught exception prior to propagating
   * it.
   *
   * @param value the value passed to the stream
   */
  void onNext(V value);

  /**
   * Receives a terminating error from the stream.
   *
   * <p>May only be called once and if called, it must be the last method called. In particular if
   * an exception is thrown by an implementation of {@code onError}, no further calls to any method
   * are allowed.
   *
   * @param t the error occurred on the stream
   */
  void onError(Throwable t);

  /**
   * Receives a notification of successful stream completion.
   *
   * <p>May only be called once, and if called it must be the last method called. In particular if
   * an exception is thrown by an implementation of {@code onCompleted}, no further calls to any
   * method are allowed.
   */
  void onCompleted();
}
