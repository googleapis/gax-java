Google API Extensions for Java
==============================

[![Build Status](https://travis-ci.org/googleapis/gax-java.svg?branch=master)]
(https://travis-ci.org/googleapis/gax-java)

[![Code Coverage](https://img.shields.io/codecov/c/github/googleapis/gax-java.svg)]
(https://codecov.io/github/googleapis/gax-java)

- [Documentation] (http://googleapis.github.io/gax-java/apidocs)

Google API Extensions for Java (GAX-Java) is a set of libraries which aids the development of APIs,
client and server, based on [GRPC](http://grpc.io) and Google API conventions.

Application code will rarely need to use most of the classes within this
library directly, but code generated automatically from the API definition
files can use services such as paged list iteration and request bundling to provide
a more convenient and idiomatic API surface to callers.

Quickstart
----------

If you are using Maven, add this to your pom.xml file
```xml
<dependency>
  <groupId>com.google.api</groupId>
  <artifactId>gax</artifactId>
  <version>0.0.23</version>
</dependency>
```
If you are using Gradle, add this to your dependencies
```Groovy
compile 'com.google.api:gax:0.0.23'
```
If you are using SBT, add this to your dependencies
```Scala
libraryDependencies += "com.google.api" % "gax" % "0.0.23"
```

Java Versions
-------------

Java 7 or above is required for using this library.

Contributing
------------

Contributions to this library are always welcome and highly encouraged.

See the [CONTRIBUTING] documentation for more information on how to get started.

Versioning
----------

This library follows [Semantic Versioning](http://semver.org/).

It is currently in major version zero (``0.y.z``), which means that anything
may change at any time and the public API should not be considered
stable.

Repository Structure
--------------------

This repository contains the following java packages.

Generated from common protocol buffer types:

- `com.google.api` - Contains types that comprise the configuration model for
  API services.
- `com.google.longrunning` - Contains the standardized types for defining long
  running operations.
- `com.google.protobuf` - Contains the Well-Known Types of Protocol Buffers
  Language version 3 (proto3). These types have stable and well-defined
  semantics and are natively supported by the proto3 compiler and runtime.
- `com.google.rpc` - Contains the types for general RPC systems. While gRPC
  uses these types, they are not designed specifically to support gRPC.
- `com.google.type` - Common types for Google APIs. All types defined in this
  package are suitable for different APIs to exchange data, and will never break
  binary compatibility.

Non-generated code:

- `com.google.api.gax.internal` - Contains classes that are designed for use by
  generated API code and which may not be very usable by clients.
- `com.google.api.gax.protobuf` - Contains classes that provide functionality on
  top of protocol buffers. This includes things like expressions (to evaluate
  conditions on protocol buffers), path templates (to compose and decompose
  resource names), type (to represent field types in protocol buffers), etc.
- `com.google.api.gax.grpc` - Contains classes that provide functionality on top
  of gRPC calls, such as retry, paged list iteration, and request bundling.

License
-------

BSD - See [LICENSE] for more information.

[CONTRIBUTING]:https://github.com/googleapis/gax-java/blob/master/CONTRIBUTING.md
[LICENSE]: https://github.com/googleapis/gax-java/blob/master/LICENSE

