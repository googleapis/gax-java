/*
 * Copyright 2022 Google LLC
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

package com.google.api.gax.grpc.nativeimage;

import static com.google.api.gax.nativeimage.NativeImageUtils.registerClassForReflection;
import static com.google.api.gax.nativeimage.NativeImageUtils.registerClassHierarchyForReflection;
import static com.google.api.gax.nativeimage.NativeImageUtils.registerForReflectiveInstantiation;
import static com.google.api.gax.nativeimage.NativeImageUtils.registerForUnsafeFieldAccess;

import com.oracle.svm.core.annotate.AutomaticFeature;
import org.graalvm.nativeimage.hosted.Feature;

/** Configures Native Image settings for the grpc-netty-shaded dependency. */
@AutomaticFeature
final class GrpcNettyFeature implements Feature {

  private static final String GRPC_NETTY_SHADED_CLASS =
      "io.grpc.netty.shaded.io.grpc.netty.NettyServer";

  private static final String GOOGLE_AUTH_CLASS =
      "com.google.auth.oauth2.ServiceAccountCredentials";

  private static final String NETTY_SHADED_PACKAGE =
      "io.grpc.netty.shaded.io.netty.util.internal.shaded.";

  @Override
  public void beforeAnalysis(BeforeAnalysisAccess access) {
    loadGoogleAuthClasses(access);
    loadGrpcNettyClasses(access);
    loadMiscClasses(access);
  }

  private static void loadGoogleAuthClasses(BeforeAnalysisAccess access) {
    // For com.google.auth:google-auth-library-oauth2-http
    Class<?> authClass = access.findClassByName(GOOGLE_AUTH_CLASS);
    if (authClass != null) {
      registerClassHierarchyForReflection(access, GOOGLE_AUTH_CLASS);
      registerClassHierarchyForReflection(
          access, "com.google.auth.oauth2.ServiceAccountJwtAccessCredentials");
    }
  }

  private static void loadGrpcNettyClasses(BeforeAnalysisAccess access) {
    // For io.grpc:grpc-netty-shaded
    Class<?> nettyShadedClass = access.findClassByName(GRPC_NETTY_SHADED_CLASS);
    if (nettyShadedClass != null) {
      // Misc. classes used by grpc-netty-shaded
      registerForReflectiveInstantiation(
          access, "io.grpc.netty.shaded.io.netty.channel.socket.nio.NioSocketChannel");
      registerClassForReflection(
          access, "io.grpc.netty.shaded.io.netty.util.internal.NativeLibraryUtil");
      registerClassForReflection(access, "io.grpc.netty.shaded.io.netty.util.ReferenceCountUtil");
      registerClassForReflection(
          access, "io.grpc.netty.shaded.io.netty.buffer.AbstractByteBufAllocator");

      // Epoll Libraries
      registerClassForReflection(access, "io.grpc.netty.shaded.io.netty.channel.epoll.Epoll");
      registerClassForReflection(
          access, "io.grpc.netty.shaded.io.netty.channel.epoll.EpollChannelOption");
      registerClassForReflection(
          access, "io.grpc.netty.shaded.io.netty.channel.epoll.EpollEventLoopGroup");
      registerForReflectiveInstantiation(
          access, "io.grpc.netty.shaded.io.netty.channel.epoll.EpollServerSocketChannel");
      registerForReflectiveInstantiation(
          access, "io.grpc.netty.shaded.io.netty.channel.epoll.EpollSocketChannel");

      // Unsafe field accesses
      registerForUnsafeFieldAccess(
          access,
          NETTY_SHADED_PACKAGE + "org.jctools.queues.MpscArrayQueueProducerIndexField",
          "producerIndex");
      registerForUnsafeFieldAccess(
          access,
          NETTY_SHADED_PACKAGE + "org.jctools.queues.MpscArrayQueueProducerLimitField",
          "producerLimit");
      registerForUnsafeFieldAccess(
          access,
          NETTY_SHADED_PACKAGE + "org.jctools.queues.MpscArrayQueueConsumerIndexField",
          "consumerIndex");
      registerForUnsafeFieldAccess(
          access,
          NETTY_SHADED_PACKAGE + "org.jctools.queues.BaseMpscLinkedArrayQueueProducerFields",
          "producerIndex");
      registerForUnsafeFieldAccess(
          access,
          NETTY_SHADED_PACKAGE + "org.jctools.queues.BaseMpscLinkedArrayQueueColdProducerFields",
          "producerLimit");
      registerForUnsafeFieldAccess(
          access,
          NETTY_SHADED_PACKAGE + "org.jctools.queues.BaseMpscLinkedArrayQueueConsumerFields",
          "consumerIndex");
    }
  }

  /** Miscellaneous classes that need to be registered coming from various JARs. */
  private static void loadMiscClasses(BeforeAnalysisAccess access) {
    registerClassHierarchyForReflection(access, "com.google.protobuf.DescriptorProtos");
    registerClassForReflection(access, "com.google.api.FieldBehavior");

    registerForUnsafeFieldAccess(access, "javax.net.ssl.SSLContext", "contextSpi");
    registerClassForReflection(access, "java.lang.management.ManagementFactory");
    registerClassForReflection(access, "java.lang.management.RuntimeMXBean");
  }
}
