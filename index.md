Google API Extensions for Java
==============================

[![Build Status](https://travis-ci.org/googleapis/gax-java.svg?branch=master)](https://travis-ci.org/googleapis/gax-java)

[![Code Coverage](https://img.shields.io/codecov/c/github/googleapis/gax-java.svg)](https://codecov.io/github/googleapis/gax-java)

- [Documentation](http://googleapis.github.io/gax-java/apidocs)

Google API Extensions for Java (GAX Java) is a library which aids in the
development of client libraries for server APIs, based on [GRPC](http://grpc.io)
and Google API conventions.

Application code will rarely need to use most of the classes within this
library directly, but code generated automatically from the API definition
files can use services such as paged list iteration, request bundling, and
polling of long-running operations to provide a more convenient and idiomatic
API surface to callers.

[//]: # (_QUICKSTART_ WARNING: This section is automatically inserted by build scripts)

Quickstart
----------

If you are using Maven, add this to your pom.xml file
```xml
<dependency>
  <groupId>com.google.api</groupId>
  <artifactId>gax</artifactId>
  <version>0.1.3</version>
</dependency>
```

If you are using Gradle, add this to your dependencies

```Groovy
compile 'com.google.api:gax:0.1.3'
```

If you are using SBT, add this to your dependencies

```Scala
libraryDependencies += "com.google.api" % "gax" % "0.1.3"
```

[//]: # (/_QUICKSTART_ WARNING: This section is automatically inserted by build scripts)

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

- `com.google.api.gax.bundling` - Contains general-purpose bundling logic.
- `com.google.api.gax.core` - Contains core interfaces and classes that are not
  specific to grpc and could be used in other contexts.
- `com.google.api.gax.grpc` - Contains classes that provide functionality on top
  of gRPC calls, such as retry, paged list iteration, request bundling, and polling
  of long-running operations.
- `com.google.api.gax.protobuf` - Contains classes that provide functionality on
  top of protocol buffers. This includes things like expressions (to evaluate
  conditions on protocol buffers), path templates (to compose and decompose
  resource names), type (to represent field types in protocol buffers), etc.
- `com.google.api.gax.testing` - Contains classes which help with testing code
  that interacts with gRPC.
- `com.google.longrunning` - Contains the mix-in client for long-running operations
  which is implemented by a number of Google APIs.

License
-------

BSD - See [LICENSE] for more information.

[CONTRIBUTING]:https://github.com/googleapis/gax-java/blob/master/CONTRIBUTING.md
[LICENSE]: https://github.com/googleapis/gax-java/blob/master/LICENSE

