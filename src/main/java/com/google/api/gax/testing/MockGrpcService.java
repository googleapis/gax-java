package com.google.api.gax.testing;

import com.google.protobuf.GeneratedMessage;

import io.grpc.ServerServiceDefinition;

import java.util.List;

/**
 * An interface of mock gRPC service.
 */
public interface MockGrpcService {
  /** Returns all the requests received. */
  public List<GeneratedMessage> getRequests();

  /** Sets the responses. */
  public void setResponses(List<GeneratedMessage> responses);

  /** Returns gRPC service definition used for binding. */
  public ServerServiceDefinition getServiceDefinition();

  /** Resets the state. */
  public void reset();
}
