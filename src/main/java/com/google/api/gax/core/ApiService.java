/*
 * Copyright 2017, Google Inc. All rights reserved.
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
 *     * Neither the name of Google Inc. nor the names of its
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
package com.google.api.gax.core;

import com.google.protobuf.ExperimentalApi;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * An object with an operational state, plus asynchronous {@link #startAsync()} and
 * {@link #stopAsync()} lifecycle methods to transition between states. Example services include
 * webservers, RPC servers and timers.
 *
 * <p>
 * The normal lifecycle of a service is:
 * <ul>
 * <li>{@linkplain State#NEW NEW} -&gt;
 * <li>{@linkplain State#STARTING STARTING} -&gt;
 * <li>{@linkplain State#RUNNING RUNNING} -&gt;
 * <li>{@linkplain State#STOPPING STOPPING} -&gt;
 * <li>{@linkplain State#TERMINATED TERMINATED}
 * </ul>
 *
 * <p>
 * There are deviations from this if there are failures or if {@link ApiService#stopAsync} is called
 * before the {@link ApiService} reaches the {@linkplain State#RUNNING RUNNING} state. The set of
 * legal transitions form a <a href="http://en.wikipedia.org/wiki/Directed_acyclic_graph">DAG</a>,
 * therefore every method of the listener will be called at most once. N.B. The {@link State#FAILED}
 * and {@link State#TERMINATED} states are terminal states, once a service enters either of these
 * states it cannot ever leave them.
 *
 * <p>
 * Implementors of this interface are strongly encouraged to extend one of the abstract classes in
 * this package which implement this interface and make the threading and state management easier.
 *
 * Similar to Guava's {@code Service}, but redeclared so that Guava could be shaded.
 */
@ExperimentalApi
public interface ApiService {
  void addListener(Listener listener, Executor executor);

  void awaitRunning();

  void awaitRunning(long timeout, TimeUnit unit) throws TimeoutException;

  void awaitTerminated();

  void awaitTerminated(long timeout, TimeUnit unit) throws TimeoutException;

  Throwable failureCause();

  boolean isRunning();

  ApiService startAsync();

  State state();

  ApiService stopAsync();

  public static enum State {
    FAILED,
    NEW,
    RUNNING,
    STARTING,
    STOPPING,
    TERMINATED
  }

  public abstract static class Listener {
    void failed(State from, Throwable failure) {}

    void running() {}

    void starting() {}

    void stopping(State from) {}

    void terminated(State from) {}
  }
}
