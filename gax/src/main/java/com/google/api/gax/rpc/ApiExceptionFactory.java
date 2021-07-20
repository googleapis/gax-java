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

import java.util.Iterator;
import java.util.ServiceLoader;

/**
 * A factory class which returns a corresponding {@link ApiException} for a generic cause.
 *
 * <p>You can override the default mapping via the {@link ServiceLoader} pattern by declaring an
 * implementation of {@link ExceptionTransformation}.
 */
public class ApiExceptionFactory {

  private static final ExceptionTransformation DEFAULT_EXCEPTION_TRANSFORMATION =
      new IdentityExceptionTransformation();
  private static final ExceptionTransformation transform = loadExceptionTransformation();

  public static ApiException createException(
      Throwable cause, StatusCode statusCode, boolean retryable) {
    ApiException exception = mapStatusToException(cause, statusCode, retryable);
    exception = transform.transform(exception);
    return validate(exception, statusCode);
  }

  public static ApiException createException(
      String message, Throwable cause, StatusCode statusCode, boolean retryable) {
    ApiException exception = mapStatusToException(message, cause, statusCode, retryable);
    exception = transform.transform(exception);
    return validate(exception, statusCode);
  }

  /**
   * Ensures that we will return a valid exception.
   *
   * @throws IllegalStateException if the exception is null.
   */
  private static ApiException validate(ApiException exception, StatusCode statusCode) {
    if (exception == null) {
      throw new IllegalStateException(
          "Unable to map "
              + statusCode
              + " to exception. Possibly a bad ExceptionTransformation service provider?");
    }
    return exception;
  }

  /**
   * Provides an interface to allow applying an arbitrary transformation to the default exception
   * mapping.
   *
   * <p>This can be useful to map the generic {@link ApiException} implementations to a client
   * library specific implementation. For instance, to extract server-provided information from the
   * gRPC trailers and present it to the user.
   */
  public interface ExceptionTransformation {
    /** Transforms a given exception to another exception. */
    ApiException transform(ApiException exception);
  }

  /** Default exception transformation which returns the unmodified input. */
  public static class IdentityExceptionTransformation implements ExceptionTransformation {

    @Override
    public ApiException transform(ApiException exception) {
      return exception;
    }
  }

  private ApiExceptionFactory() {}

  /**
   * Loads an implementation of {@link ApiExceptionFactory} via {@link ServiceLoader}. Defers to a
   * default implementation if no {@link ServiceLoader} is found.
   *
   * @return the implementation of {@link ApiExceptionFactory} to use.
   * @throws IllegalStateException if multiple definitions of {@link ServiceLoader} are found.
   */
  private static ExceptionTransformation loadExceptionTransformation() {
    ServiceLoader<ExceptionTransformation> loader =
        ServiceLoader.load(ExceptionTransformation.class);
    Iterator<ExceptionTransformation> it = loader.iterator();
    if (!it.hasNext()) {
      return DEFAULT_EXCEPTION_TRANSFORMATION;
    }
    ExceptionTransformation transform = it.next();
    if (it.hasNext()) {
      throw new IllegalStateException(
          "Only one service provider supported for "
              + ExceptionTransformation.class
              + ". Found at least 2 providers: "
              + transform.getClass()
              + ", "
              + it.next().getClass());
    }
    return transform;
  }

  public static ApiException mapStatusToException(
      Throwable cause, StatusCode statusCode, boolean retryable) {
    switch (statusCode.getCode()) {
      case CANCELLED:
        return new CancelledException(cause, statusCode, retryable);
      case NOT_FOUND:
        return new NotFoundException(cause, statusCode, retryable);
      case UNKNOWN:
        return new UnknownException(cause, statusCode, retryable);
      case INVALID_ARGUMENT:
        return new InvalidArgumentException(cause, statusCode, retryable);
      case DEADLINE_EXCEEDED:
        return new DeadlineExceededException(cause, statusCode, retryable);
      case ALREADY_EXISTS:
        return new AlreadyExistsException(cause, statusCode, retryable);
      case PERMISSION_DENIED:
        return new PermissionDeniedException(cause, statusCode, retryable);
      case RESOURCE_EXHAUSTED:
        return new ResourceExhaustedException(cause, statusCode, retryable);
      case FAILED_PRECONDITION:
        return new FailedPreconditionException(cause, statusCode, retryable);
      case ABORTED:
        return new AbortedException(cause, statusCode, retryable);
      case OUT_OF_RANGE:
        return new OutOfRangeException(cause, statusCode, retryable);
      case UNIMPLEMENTED:
        return new UnimplementedException(cause, statusCode, retryable);
      case INTERNAL:
        return new InternalException(cause, statusCode, retryable);
      case UNAVAILABLE:
        return new UnavailableException(cause, statusCode, retryable);
      case DATA_LOSS:
        return new DataLossException(cause, statusCode, retryable);
      case UNAUTHENTICATED:
        return new UnauthenticatedException(cause, statusCode, retryable);

      default:
        return new UnknownException(cause, statusCode, retryable);
    }
  }

  public static ApiException mapStatusToException(
      String message, Throwable cause, StatusCode statusCode, boolean retryable) {
    switch (statusCode.getCode()) {
      case CANCELLED:
        return new CancelledException(message, cause, statusCode, retryable);
      case NOT_FOUND:
        return new NotFoundException(message, cause, statusCode, retryable);
      case UNKNOWN:
        return new UnknownException(message, cause, statusCode, retryable);
      case INVALID_ARGUMENT:
        return new InvalidArgumentException(message, cause, statusCode, retryable);
      case DEADLINE_EXCEEDED:
        return new DeadlineExceededException(message, cause, statusCode, retryable);
      case ALREADY_EXISTS:
        return new AlreadyExistsException(message, cause, statusCode, retryable);
      case PERMISSION_DENIED:
        return new PermissionDeniedException(message, cause, statusCode, retryable);
      case RESOURCE_EXHAUSTED:
        return new ResourceExhaustedException(message, cause, statusCode, retryable);
      case FAILED_PRECONDITION:
        return new FailedPreconditionException(message, cause, statusCode, retryable);
      case ABORTED:
        return new AbortedException(message, cause, statusCode, retryable);
      case OUT_OF_RANGE:
        return new OutOfRangeException(message, cause, statusCode, retryable);
      case UNIMPLEMENTED:
        return new UnimplementedException(message, cause, statusCode, retryable);
      case INTERNAL:
        return new InternalException(message, cause, statusCode, retryable);
      case UNAVAILABLE:
        return new UnavailableException(message, cause, statusCode, retryable);
      case DATA_LOSS:
        return new DataLossException(message, cause, statusCode, retryable);
      case UNAUTHENTICATED:
        return new UnauthenticatedException(message, cause, statusCode, retryable);

      default:
        return new UnknownException(cause, statusCode, retryable);
    }
  }
}
