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
files can use services such as paged list iteration, request batching, and
polling of long-running operations to provide a more convenient and idiomatic
API surface to callers.

Currently, this library shouldn't be used independently from google-cloud-java, otherwise there is
a high risk of diamond dependency problems, because google-cloud-java uses beta features from this
library which can change in breaking ways between versions. See [VERSIONING](#versioning) for
more information.

[//]: # (_QUICKSTART_ WARNING: This section is automatically inserted by build scripts)

Quickstart
----------

If you are using Maven, add this to your pom.xml file
```xml
<dependency>
  <groupId>com.google.api</groupId>
  <artifactId>gax</artifactId>
  <version>1.5.1</version>
</dependency>
<dependency>
  <groupId>com.google.api</groupId>
  <artifactId>gax-grpc</artifactId>
  <version>0.22.1</version>
</dependency>
```

If you are using Gradle, add this to your dependencies

```Groovy
compile 'com.google.api:gax:1.5.1',
  'com.google.api:gax-grpc:0.22.1'
```

If you are using SBT, add this to your dependencies

```Scala
libraryDependencies += "com.google.api" % "gax" % "1.5.1"
libraryDependencies += "com.google.api" % "gax-grpc" % "0.22.1"
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

This library follows [Semantic Versioning](http://semver.org/), but with some
additional qualifications:

1. Components marked with `@BetaApi` are considered to be "0.x" features inside
   a "1.x" library. This means they can change between minor and patch releases
   in incompatible ways. These features should not be used by any library "B"
   that itself has consumers, unless the components of library B that use
   `@BetaApi` features are also marked with `@BetaApi`. Features marked as
   `@BetaApi` are on a path to eventually become "1.x" features with the marker
   removed.

   **Special exception for google-cloud-java**: google-cloud-java is
   allowed to depend on `@BetaApi` features without declaring the consuming
   code `@BetaApi`, because gax-java and google-cloud-java move in step
   with each other. For this reason, gax-java should not be used
   independently of google-cloud-java.

1. Components marked with `@InternalApi` are technically public, but are only
   public for technical reasons, because of the limitations of Java's access
   modifiers. For the purposes of semver, they should be considered private.

It is currently in major version zero (``0.y.z``), which means that anything
may change at any time and the public API should not be considered
stable.

Repository Structure
--------------------

This repository contains the following java packages.

### gax

- `com.google.api.gax.batching` - Contains general-purpose batching logic.
- `com.google.api.gax.core` - Contains core interfaces and classes that are not
  specific to grpc and could be used in other contexts.
- `com.google.api.gax.paging` - Contains classes related to list calls that return
  results in pages.
- `com.google.api.gax.retrying` - Contains classes related to retrying API calls.

### gax-grpc

- `com.google.api.gax.grpc` - Contains classes that provide functionality on top
  of gRPC calls, such as retry, paged list iteration, request batching, and polling
  of long-running operations.
- `com.google.api.gax.grpc.testing` - Contains classes which help with testing code
  that interacts with gRPC.
- `com.google.longrunning` - Contains the mix-in client for long-running operations
  which is implemented by a number of Google APIs.

License
-------

BSD - See [LICENSE] for more information.

[CONTRIBUTING]:https://github.com/googleapis/gax-java/blob/master/CONTRIBUTING.md
[LICENSE]: https://github.com/googleapis/gax-java/blob/master/LICENSE

