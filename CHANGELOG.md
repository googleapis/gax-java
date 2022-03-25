# Changelog

## [2.13.0](https://github.com/googleapis/gax-java/compare/v2.12.2...v2.13.0) (2022-03-25)


### Features

* dynamic channel pool scaled by number of outstanding request ([#1569](https://github.com/googleapis/gax-java/issues/1569)) ([fff2bab](https://github.com/googleapis/gax-java/commit/fff2babaf2620686c2e0be1b6d338ac088248cf6))


### Dependencies

* update dependency com_google_protobuf to 3.19.4 ([5c01174](https://github.com/googleapis/gax-java/commit/5c0117408ecfaec4e445b4fd813da6478415ea8e))
* update dependency com.google.api:api-common to 2.1.5 ([5c01174](https://github.com/googleapis/gax-java/commit/5c0117408ecfaec4e445b4fd813da6478415ea8e))
* update dependency com.google.api.grpc:grpc-google-common-protos to 2.8.0 ([5c01174](https://github.com/googleapis/gax-java/commit/5c0117408ecfaec4e445b4fd813da6478415ea8e))
* update dependency com.google.api.grpc:proto-google-common-protos to 2.8.0 ([5c01174](https://github.com/googleapis/gax-java/commit/5c0117408ecfaec4e445b4fd813da6478415ea8e))
* update dependency com.google.guava:guava to 31.1-jre ([5c01174](https://github.com/googleapis/gax-java/commit/5c0117408ecfaec4e445b4fd813da6478415ea8e))
* update dependency com.google.http-client:google-http-client to 1.41.5 ([5c01174](https://github.com/googleapis/gax-java/commit/5c0117408ecfaec4e445b4fd813da6478415ea8e))
* update dependency com.google.http-client:google-http-client-gson to 1.41.5 ([5c01174](https://github.com/googleapis/gax-java/commit/5c0117408ecfaec4e445b4fd813da6478415ea8e))
* update dependency io_grpc to 1.45.0. ([#1639](https://github.com/googleapis/gax-java/issues/1639)) ([5c01174](https://github.com/googleapis/gax-java/commit/5c0117408ecfaec4e445b4fd813da6478415ea8e))

### [2.12.2](https://github.com/googleapis/gax-java/compare/v2.12.1...v2.12.2) (2022-02-09)


### Bug Fixes

* REST: Make make LRO stub accept APIs of different versions ([#1622](https://github.com/googleapis/gax-java/issues/1622)) ([3ae8d85](https://github.com/googleapis/gax-java/commit/3ae8d850c5599ce487778d98d562d5b9b9e85d25))

### [2.12.1](https://github.com/googleapis/gax-java/compare/v2.12.0...v2.12.1) (2022-02-09)


### Bug Fixes

* revert "feat: add api key support ([#1436](https://github.com/googleapis/gax-java/issues/1436))" ([#1617](https://github.com/googleapis/gax-java/issues/1617)) ([47f98b8](https://github.com/googleapis/gax-java/commit/47f98b872c6896ad2af37e30db440321c2adb492))

## [2.12.0](https://github.com/googleapis/gax-java/compare/v2.11.0...v2.12.0) (2022-01-28)


### Features

* add REST interceptors infrastructure ([#1607](https://github.com/googleapis/gax-java/issues/1607)) ([0572eed](https://github.com/googleapis/gax-java/commit/0572eed8aa29879c74794b22e8ae79e414dd5821))

## [2.11.0](https://github.com/googleapis/gax-java/compare/v2.10.0...v2.11.0) (2022-01-27)


### Features

* Add a builder to handle the common logic of extracting routing header values from request ([#1598](https://github.com/googleapis/gax-java/issues/1598)) ([2836baa](https://github.com/googleapis/gax-java/commit/2836baafa1114761cffbfdd4aee2322a4a931f8f))


### Dependencies

* update dependency com_google_protobuf to 3.19.3 ([734906e](https://github.com/googleapis/gax-java/commit/734906efd15064ea0d36b308f217508c9bf5ed42))
* update dependency com.google.api:api-common to 2.1.3 ([734906e](https://github.com/googleapis/gax-java/commit/734906efd15064ea0d36b308f217508c9bf5ed42))
* update dependency com.google.api.grpc:grpc-google-common-protos to 2.7.2 ([734906e](https://github.com/googleapis/gax-java/commit/734906efd15064ea0d36b308f217508c9bf5ed42))
* update dependency com.google.api.grpc:proto-google-common-protos to 2.7.2 ([734906e](https://github.com/googleapis/gax-java/commit/734906efd15064ea0d36b308f217508c9bf5ed42))
* update dependency com.google.errorprone:error_prone_annotations to v2.11.0 ([734906e](https://github.com/googleapis/gax-java/commit/734906efd15064ea0d36b308f217508c9bf5ed42))
* update dependency com.google.http-client:google-http-client to 1.41.2 ([734906e](https://github.com/googleapis/gax-java/commit/734906efd15064ea0d36b308f217508c9bf5ed42))
* update dependency com.google.http-client:google-http-client-gson to 1.41.2 ([734906e](https://github.com/googleapis/gax-java/commit/734906efd15064ea0d36b308f217508c9bf5ed42))
* update dependency io_grpc to 1.44.0 ([#1610](https://github.com/googleapis/gax-java/issues/1610)) ([734906e](https://github.com/googleapis/gax-java/commit/734906efd15064ea0d36b308f217508c9bf5ed42))

## [2.10.0](https://github.com/googleapis/gax-java/compare/v2.9.0...v2.10.0) (2022-01-21)


### Features

* add api key support ([#1436](https://github.com/googleapis/gax-java/issues/1436)) ([5081ec6](https://github.com/googleapis/gax-java/commit/5081ec6541da8ca3f5a4c0d20aa75bd20010a642))
* introduce HttpJsonClientCall, Listeners infrastructure and ServerStreaming support in REST transport ([#1599](https://github.com/googleapis/gax-java/issues/1599)) ([3c97529](https://github.com/googleapis/gax-java/commit/3c97529b8bd0e8141c5d722f887cb7ae1ed30b69))

## [2.9.0](https://github.com/googleapis/gax-java/compare/v2.8.1...v2.9.0) (2022-01-14)


### Features

* pass a CallCredentials to grpc-java for DirectPath ([#1488](https://github.com/googleapis/gax-java/issues/1488)) ([4a7713a](https://github.com/googleapis/gax-java/commit/4a7713ad683e70380087437d6b52cbe3e115d1fb))

### [2.8.1](https://www.github.com/googleapis/gax-java/compare/v2.8.0...v2.8.1) (2022-01-06)


### Dependencies

* update dependency com.google.api:api-common to 2.1.2 ([#1590](https://www.github.com/googleapis/gax-java/issues/1590)) ([1b34870](https://www.github.com/googleapis/gax-java/commit/1b34870358a26bd4542594b6c746dca190f65d24))
* update dependency com.google.auto.value:auto-value to v1.9 ([1b34870](https://www.github.com/googleapis/gax-java/commit/1b34870358a26bd4542594b6c746dca190f65d24))

## [2.8.0](https://www.github.com/googleapis/gax-java/compare/v2.7.1...v2.8.0) (2022-01-06)


### Features

* update DirectPath xds scheme ([#1585](https://www.github.com/googleapis/gax-java/issues/1585)) ([7915f85](https://www.github.com/googleapis/gax-java/commit/7915f850313ddfdf35332d976d2567f31c2aa8a7))


### Dependencies

* update dependency com.google.api.grpc:proto-google-common-protos to v2.7.1 ([#1587](https://www.github.com/googleapis/gax-java/issues/1587)) ([eb0e9d1](https://www.github.com/googleapis/gax-java/commit/eb0e9d106a64af5f583d75d7291cdc17652fd5e7))
* update dependency com.google.http-client:google-http-client-bom to v1.41.0 ([eb0e9d1](https://www.github.com/googleapis/gax-java/commit/eb0e9d106a64af5f583d75d7291cdc17652fd5e7))
* update dependency com.google.protobuf:protobuf-bom to v3.19.2 ([eb0e9d1](https://www.github.com/googleapis/gax-java/commit/eb0e9d106a64af5f583d75d7291cdc17652fd5e7))
* upgrade common-protos to 2.7.0 ([#1579](https://www.github.com/googleapis/gax-java/issues/1579)) ([0a10f5f](https://www.github.com/googleapis/gax-java/commit/0a10f5fe77ab26b3ac7d4c590360945ead72eca1))

### [2.7.1](https://www.github.com/googleapis/gax-java/compare/v2.7.0...v2.7.1) (2021-12-02)


### Bug Fixes

* fix gRPC code conversion ([#1555](https://www.github.com/googleapis/gax-java/issues/1555)) ([09b99d5](https://www.github.com/googleapis/gax-java/commit/09b99d591497b44c3c25b1a54abb0f1cb69d7376))
* pass error message when creating ApiException ([#1556](https://www.github.com/googleapis/gax-java/issues/1556)) ([918ae41](https://www.github.com/googleapis/gax-java/commit/918ae419f84ad5721638ca10eca992333e9f7c3d))
* revert generics syntax change in MockHttpService test utility ([#1574](https://www.github.com/googleapis/gax-java/issues/1574)) ([b629488](https://www.github.com/googleapis/gax-java/commit/b629488ffc7d68158158d9197695158f97229c7b))
* update exception mapping on HTTP error responses ([#1570](https://www.github.com/googleapis/gax-java/issues/1570)) ([8a170d1](https://www.github.com/googleapis/gax-java/commit/8a170d19b42e9b13d4c69dcfbe531d4d4ca69c90))


### Dependencies

* update grpc to 1.42.1 ([#1559](https://www.github.com/googleapis/gax-java/issues/1559)) ([92b7632](https://www.github.com/googleapis/gax-java/commit/92b76325d54604c98c798c489b3a963fdf21a75c))
* upgrade protobuf to 3.19.1 ([#1571](https://www.github.com/googleapis/gax-java/issues/1571)) ([7b354e7](https://www.github.com/googleapis/gax-java/commit/7b354e73b8ce49008bed51076afb255ca5dc68e4))

## [2.7.0](https://www.github.com/googleapis/gax-java/compare/v2.6.0...v2.7.0) (2021-11-03)


### Features

* add batch throttled time to tracer ([#1463](https://www.github.com/googleapis/gax-java/issues/1463)) ([14c25cd](https://www.github.com/googleapis/gax-java/commit/14c25cdfae2c0602d99fcd07c9067138fc172a7f))
* add google-c2p dependence to DirectPath ([#1521](https://www.github.com/googleapis/gax-java/issues/1521)) ([d4222e7](https://www.github.com/googleapis/gax-java/commit/d4222e757d35e874a57d030f2f964e8d61c71d6d))
* next release from main branch is 2.7.0 ([#1530](https://www.github.com/googleapis/gax-java/issues/1530)) ([a96953e](https://www.github.com/googleapis/gax-java/commit/a96953e2c889ad9195b420ce5ae49b20258fb6ea))
* pass request in ApiTracer ([#1491](https://www.github.com/googleapis/gax-java/issues/1491)) ([27bf265](https://www.github.com/googleapis/gax-java/commit/27bf2655120972d929e6118b73d8d556b0440fcf))


### Bug Fixes

* call ResponseMetadataHanlder#onTrailers before calling onClose ([#1549](https://www.github.com/googleapis/gax-java/issues/1549)) ([19a77a4](https://www.github.com/googleapis/gax-java/commit/19a77a4f6c62e84bba4879690992bbc494730d23))
* declare depenencies of API surfaces as api ([#1535](https://www.github.com/googleapis/gax-java/issues/1535)) ([725414f](https://www.github.com/googleapis/gax-java/commit/725414f4ccb1e5ac4625790ea990c6c8dfa4fdff))

## [2.6.0](https://www.github.com/googleapis/gax-java/compare/v2.5.3...v2.6.0) (2021-10-15)


### Features

* remove deprecated Generated annotation ([2d76bff](https://www.github.com/googleapis/gax-java/commit/2d76bff6d64da818a3aff7ea0bdf5a36b82c3464))


### Bug Fixes

* Fix com.google.rpc.Code to StatusCode.Code conversion logic ([2d76bff](https://www.github.com/googleapis/gax-java/commit/2d76bff6d64da818a3aff7ea0bdf5a36b82c3464))


### Dependencies

* update api-common to 2.0.5 ([2d76bff](https://www.github.com/googleapis/gax-java/commit/2d76bff6d64da818a3aff7ea0bdf5a36b82c3464))
* update auto-value to 1.8.2 ([2d76bff](https://www.github.com/googleapis/gax-java/commit/2d76bff6d64da818a3aff7ea0bdf5a36b82c3464))
* update com_google_protobuf to 3.18.1 ([#1519](https://www.github.com/googleapis/gax-java/issues/1519)) ([2d76bff](https://www.github.com/googleapis/gax-java/commit/2d76bff6d64da818a3aff7ea0bdf5a36b82c3464))
* update google-http-client to 1.40.1 ([2d76bff](https://www.github.com/googleapis/gax-java/commit/2d76bff6d64da818a3aff7ea0bdf5a36b82c3464))
* update grpc to 1.41.0 ([2d76bff](https://www.github.com/googleapis/gax-java/commit/2d76bff6d64da818a3aff7ea0bdf5a36b82c3464))
* update guava to v31 ([2d76bff](https://www.github.com/googleapis/gax-java/commit/2d76bff6d64da818a3aff7ea0bdf5a36b82c3464))

### [2.5.3](https://www.github.com/googleapis/gax-java/compare/v2.5.2...v2.5.3) (2021-10-13)


### Bug Fixes

* Fix `com.google.rpc.Code` to `StatusCode.Code` conversion  logic ([#1508](https://www.github.com/googleapis/gax-java/issues/1508)) ([61b1617](https://www.github.com/googleapis/gax-java/commit/61b161799faf292be1394111381f8a35e757b85a))

### [2.5.2](https://www.github.com/googleapis/gax-java/compare/v2.5.1...v2.5.2) (2021-10-13)


### Dependencies

* release multiple artifacts at once ([#1506](https://www.github.com/googleapis/gax-java/issues/1506)) ([8c022f6](https://www.github.com/googleapis/gax-java/commit/8c022f69f7878280e00f200f65a931ff0f8cfe45))

### [2.5.1](https://www.github.com/googleapis/gax-java/compare/v2.5.0...v2.5.1) (2021-10-08)


### Dependencies

* fix release pipeline ([#1500](https://www.github.com/googleapis/gax-java/issues/1500)) ([f8ae03b](https://www.github.com/googleapis/gax-java/commit/f8ae03bbf0389d5fd943d214c1058ee012be757b))

## [2.5.0](https://www.github.com/googleapis/gax-java/compare/v2.4.1...v2.5.0) (2021-09-21)


### Features

* Add REST AIP-151 LRO support ([#1484](https://www.github.com/googleapis/gax-java/issues/1484)) ([95ca348](https://www.github.com/googleapis/gax-java/commit/95ca3482d272b5c5c5ac2c85ba007f0ba9f7b5cf))

### [2.4.1](https://www.github.com/googleapis/gax-java/compare/v2.4.0...v2.4.1) (2021-09-08)


### Bug Fixes

* REGAPIC fix socket timeout for wait calls ([#1476](https://www.github.com/googleapis/gax-java/issues/1476)) ([86c68b3](https://www.github.com/googleapis/gax-java/commit/86c68b3ffd241f6932516d0e7b5d9ae5714b89e0))

## [2.4.0](https://www.github.com/googleapis/gax-java/compare/v2.3.0...v2.4.0) (2021-08-27)


### Bug Fixes

* Fix PATCH being unsupported ([#1465](https://www.github.com/googleapis/gax-java/issues/1465)) ([2c6ac24](https://www.github.com/googleapis/gax-java/commit/2c6ac24b1fce1de356e69370bbe6a4348825e3f9))


### Dependencies

* update google-common-prots and google-http-client ([#1471](https://www.github.com/googleapis/gax-java/issues/1471)) ([80e17a3](https://www.github.com/googleapis/gax-java/commit/80e17a35feb00aff0af3a65876625c705eb6ca46))

## [2.3.0](https://www.github.com/googleapis/gax-java/compare/v2.2.0...v2.3.0) (2021-08-16)


### Features

* add custom options to ApiCallContext ([#1435](https://www.github.com/googleapis/gax-java/issues/1435)) ([0fe20f3](https://www.github.com/googleapis/gax-java/commit/0fe20f379feba1570e562e60e3f0bf7cc4e485bd))
* add UseJwtAccessWithScope to GoogleCredentialsProvider ([#1420](https://www.github.com/googleapis/gax-java/issues/1420)) ([ed39c34](https://www.github.com/googleapis/gax-java/commit/ed39c34693783460fc03effb47e7027914cfb5bc))

## [2.2.0](https://www.github.com/googleapis/gax-java/compare/v2.1.0...v2.2.0) (2021-08-13)


### Features

* Add AIP-151 LRO OperationsClient to gax-httpjson ([#1458](https://www.github.com/googleapis/gax-java/issues/1458)) ([314acb6](https://www.github.com/googleapis/gax-java/commit/314acb6a5c335732e8406bec86f6c37296ebf3f3))

## [2.1.0](https://www.github.com/googleapis/gax-java/compare/v2.0.0...v2.1.0) (2021-08-11)


### Features

* add allowNonDefaultServiceAccount option for DirectPath ([#1433](https://www.github.com/googleapis/gax-java/issues/1433)) ([209b494](https://www.github.com/googleapis/gax-java/commit/209b4944feba1c62be2c9de4545e3b01a806b738))


### Bug Fixes

* fix httpjson executor ([#1448](https://www.github.com/googleapis/gax-java/issues/1448)) ([8f48b70](https://www.github.com/googleapis/gax-java/commit/8f48b7027b95e8e75872d1f9dac537ea697d0acc))
* make closeAsync don't interrupt running thread ([#1446](https://www.github.com/googleapis/gax-java/issues/1446)) ([7c6c298](https://www.github.com/googleapis/gax-java/commit/7c6c29824487346d444730388ea6967408692696))


### Dependencies

* update dependency com.google.api:api-common to v2.0.1 ([#1452](https://www.github.com/googleapis/gax-java/issues/1452)) ([a52f16f](https://www.github.com/googleapis/gax-java/commit/a52f16f6cef8340357acb374ff31c8a6f248403c))

## [2.0.0](https://www.github.com/googleapis/gax-java/compare/v1.67.0...v2.0.0) (2021-07-30)


### Features

* promote to 2.0.0 ([#1444](https://www.github.com/googleapis/gax-java/issues/1444)) ([776b1aa](https://www.github.com/googleapis/gax-java/commit/776b1aa73022bedec55e69732245b73cd04608f8))


### Bug Fixes

* stop overriding default grpc executor ([#1355](https://www.github.com/googleapis/gax-java/issues/1355)) ([b1f8c43](https://www.github.com/googleapis/gax-java/commit/b1f8c43cc90eb8e5ef78d142878841689356738c))


### Dependencies

* update api-common, guava, google-auth-library-credentials ([#1442](https://www.github.com/googleapis/gax-java/issues/1442)) ([2925ed7](https://www.github.com/googleapis/gax-java/commit/2925ed78cfb74db07a87da28839aeebc9027ac72))

## [1.67.0](https://www.github.com/googleapis/gax-java/compare/v1.66.0...v1.67.0) (2021-07-19)


### Features

* introduce closeAsync to Batcher ([#1423](https://www.github.com/googleapis/gax-java/issues/1423)) ([aab5288](https://www.github.com/googleapis/gax-java/commit/aab528803405c2b5f9fc89641f47abff948a876d))
* optimize unary callables to not wait for trailers ([#1356](https://www.github.com/googleapis/gax-java/issues/1356)) ([dd5f955](https://www.github.com/googleapis/gax-java/commit/dd5f955a3ab740c677fbc6f1247094798eb814a3))
* update DirectPath environment variables ([#1412](https://www.github.com/googleapis/gax-java/issues/1412)) ([4f63b61](https://www.github.com/googleapis/gax-java/commit/4f63b61f1259936aa4a1eaf9162218c787b92f2a))


### Bug Fixes

* remove `extends ApiMessage` from `HttpJsonStubCallableFactory` definition ([#1426](https://www.github.com/googleapis/gax-java/issues/1426)) ([87636a5](https://www.github.com/googleapis/gax-java/commit/87636a5812874a77e9004aab07607121efa43736))

## [1.66.0](https://www.github.com/googleapis/gax-java/compare/v1.65.1...v1.66.0) (2021-06-24)


### Features

* make ApiTracer internal API ([#1414](https://www.github.com/googleapis/gax-java/issues/1414)) ([e3e8462](https://www.github.com/googleapis/gax-java/commit/e3e8462a2f9e866480ec2106dc59555d41ea4bb5))

### [1.65.1](https://www.github.com/googleapis/gax-java/compare/v1.65.0...v1.65.1) (2021-06-08)


### Bug Fixes

* fix grammar in StubSetting comment ([#1397](https://www.github.com/googleapis/gax-java/issues/1397)) ([b015910](https://www.github.com/googleapis/gax-java/commit/b0159102b52fd4b778a9bde15b1acd2e9fa6958e))

## [1.65.0](https://www.github.com/googleapis/gax-java/compare/v1.64.0...v1.65.0) (2021-06-02)


### Features

* add mtls feature to http and grpc transport provider ([#1249](https://www.github.com/googleapis/gax-java/issues/1249)) ([b863041](https://www.github.com/googleapis/gax-java/commit/b863041bc4c03c8766e0feca8cb10f531373dc44))

## [1.64.0](https://www.github.com/googleapis/gax-java/compare/v1.63.4...v1.64.0) (2021-05-10)


### Features

* release 1.64.0 ([#1375](https://www.github.com/googleapis/gax-java/issues/1375)) ([499682e](https://www.github.com/googleapis/gax-java/commit/499682ec3b96ddeaac75f6e71cc3bc85a854da97))

### [1.63.4](https://www.github.com/googleapis/gax-java/compare/v1.63.3...v1.63.4) (2021-05-07)


### Bug Fixes

* Make x-goog-api-client header report rest-based transport clients with `rest/` token instead of `httpson/`. ([#1370](https://www.github.com/googleapis/gax-java/issues/1370)) ([b1b0b49](https://www.github.com/googleapis/gax-java/commit/b1b0b498ba188a51b17d179988074bcf34fb7590))

### [1.63.3](https://www.github.com/googleapis/gax-java/compare/v1.63.2...v1.63.3) (2021-05-04)


### Bug Fixes

* fix flaky tests and non blocking semaphore ([#1365](https://www.github.com/googleapis/gax-java/issues/1365)) ([fc8e520](https://www.github.com/googleapis/gax-java/commit/fc8e520acfaf843ac61e806bdb4b5fe393d0b447))
* Remove a flacky test in FlowControllerTest ([#1360](https://www.github.com/googleapis/gax-java/issues/1360)) ([2cca0bf](https://www.github.com/googleapis/gax-java/commit/2cca0bf9e96271dd52e8bffa00b8f2d45d358d35))

### [1.63.2](https://www.github.com/googleapis/gax-java/compare/v1.63.1...v1.63.2) (2021-04-30)


### Bug Fixes

* Remove default value handling ([#1353](https://www.github.com/googleapis/gax-java/issues/1353)) ([ed0fc79](https://www.github.com/googleapis/gax-java/commit/ed0fc791b22db45bd20de890b0abecd1839d2d86))


### Dependencies

* remove codecov.io ([#1354](https://www.github.com/googleapis/gax-java/issues/1354)) ([06a53ac](https://www.github.com/googleapis/gax-java/commit/06a53aca36ed0825122be160479b1ea0ba8635a0))

### [1.63.1](https://www.github.com/googleapis/gax-java/compare/v1.63.0...v1.63.1) (2021-04-26)


### Bug Fixes

* fix dynamic flow control setting checks ([#1347](https://www.github.com/googleapis/gax-java/issues/1347)) ([69458b4](https://www.github.com/googleapis/gax-java/commit/69458b4deefe5b9c2c33a3b51389face968ff52f))
* fix watchdog NPE red herring ([#1344](https://www.github.com/googleapis/gax-java/issues/1344)) ([06dbf12](https://www.github.com/googleapis/gax-java/commit/06dbf129ce63d28430e1022137679c9cfdf433ee))

## [1.63.0](https://www.github.com/googleapis/gax-java/compare/v1.62.0...v1.63.0) (2021-04-05)


### Features

* add setLogicalTimeout helper to RetrySettings ([#1334](https://www.github.com/googleapis/gax-java/issues/1334)) ([97d3214](https://www.github.com/googleapis/gax-java/commit/97d32144e9de38a9e351dc34270aa41aef351151))
* dynamic flow control for batcher part 2 ([#1310](https://www.github.com/googleapis/gax-java/issues/1310)) ([20f6ecf](https://www.github.com/googleapis/gax-java/commit/20f6ecf4807bb4dffd0fa80717302c1f46b1d789))
* dynamic flow control p3: add FlowControllerEventStats ([#1332](https://www.github.com/googleapis/gax-java/issues/1332)) ([5329ea4](https://www.github.com/googleapis/gax-java/commit/5329ea43c024ca14cea1012c5ab46e694e199492))
* support retry settings and retryable codes in call context ([#1238](https://www.github.com/googleapis/gax-java/issues/1238)) ([7f7aa25](https://www.github.com/googleapis/gax-java/commit/7f7aa252ce96413cb09e01cc2e76672b167b1baf))
* wrap non-retryable RPCs in retry machinery ([#1328](https://www.github.com/googleapis/gax-java/issues/1328)) ([51c40ab](https://www.github.com/googleapis/gax-java/commit/51c40abd408ab0637f3b65cf5697a4ee85a544a4))


### Bug Fixes

* add BetaApi tag to setLogicalTimeout ([#1335](https://www.github.com/googleapis/gax-java/issues/1335)) ([fc7169d](https://www.github.com/googleapis/gax-java/commit/fc7169d431baf2db90992241a0ef3d40c72567a5))
* retain user RPC timeout if set via withTimeout ([#1324](https://www.github.com/googleapis/gax-java/issues/1324)) ([3fe1db9](https://www.github.com/googleapis/gax-java/commit/3fe1db913b134e4fddee4c769ee4497847d8e01f))


### Documentation

* cloud rad java doc generation ([#1336](https://www.github.com/googleapis/gax-java/issues/1336)) ([751ccf3](https://www.github.com/googleapis/gax-java/commit/751ccf3cb351cd5a037104242cdb763a104d6bc3))

## [1.62.0](https://www.github.com/googleapis/gax-java/compare/v1.61.0...v1.62.0) (2021-02-25)


### ⚠ BREAKING CHANGES

* deprecate RetrySettings.isJittered [gax-java] (#1308)

### Features

* deprecate RetrySettings.isJittered [gax-java] ([#1308](https://www.github.com/googleapis/gax-java/issues/1308)) ([68644a4](https://www.github.com/googleapis/gax-java/commit/68644a4e24f29223f8f533a3d353dff7457d9737))
* dynamic flow control part 1 - add FlowController to Batcher ([#1289](https://www.github.com/googleapis/gax-java/issues/1289)) ([bae5eb6](https://www.github.com/googleapis/gax-java/commit/bae5eb6070e690c26b95e7b908d15300aa54ef1c))


### Bug Fixes

* prevent unchecked warnings in gax-httpjson ([#1306](https://www.github.com/googleapis/gax-java/issues/1306)) ([ee370f6](https://www.github.com/googleapis/gax-java/commit/ee370f62c5d411738a9b25cf4cfc095aa06d9e07))
* remove unused @InternalExtensionOnly from CallContext classes ([#1304](https://www.github.com/googleapis/gax-java/issues/1304)) ([a8d3a2d](https://www.github.com/googleapis/gax-java/commit/a8d3a2dca96efdb1ce154a976c3e0844e3f501d6))


### Dependencies

* update google-auth-library to 0.24.0 ([#1315](https://www.github.com/googleapis/gax-java/issues/1315)) ([772331e](https://www.github.com/googleapis/gax-java/commit/772331eda5c47e9de376e505e7d8ee502b01ec72))
* update google-common-protos to 2.0.1 ([772331e](https://www.github.com/googleapis/gax-java/commit/772331eda5c47e9de376e505e7d8ee502b01ec72))
* update google-http-client to 1.39.0 ([772331e](https://www.github.com/googleapis/gax-java/commit/772331eda5c47e9de376e505e7d8ee502b01ec72))
* update google-iam ([#1313](https://www.github.com/googleapis/gax-java/issues/1313)) ([327b53c](https://www.github.com/googleapis/gax-java/commit/327b53ca7739d9be6e24305b23af2c7a35cb6f4d))
* update gRPC to 1.36.0 ([772331e](https://www.github.com/googleapis/gax-java/commit/772331eda5c47e9de376e505e7d8ee502b01ec72))
* update opencensus to 0.28.0 ([772331e](https://www.github.com/googleapis/gax-java/commit/772331eda5c47e9de376e505e7d8ee502b01ec72))
* update protobuf to 3.15.2 ([772331e](https://www.github.com/googleapis/gax-java/commit/772331eda5c47e9de376e505e7d8ee502b01ec72))

## [1.61.0](https://www.github.com/googleapis/gax-java/compare/v1.60.1...v1.61.0) (2021-02-17)


### Features

* **operations:** Add WaitOperation API surface [gax-java] ([#1284](https://www.github.com/googleapis/gax-java/issues/1284)) ([68761a7](https://www.github.com/googleapis/gax-java/commit/68761a7de17489c02362e079ca766ee06da5e247))


### Bug Fixes

* InstantiatingGrpcChannelProvider.toBuilder() should carry over all config data ([#1298](https://www.github.com/googleapis/gax-java/issues/1298)) ([0bc5dc5](https://www.github.com/googleapis/gax-java/commit/0bc5dc54c8eea00f2dc0e1d6a4a42e3417c64fc7))
* **lro:** Add Operation name to headers in {Get,List}Operation requests [gax-java] ([#1281](https://www.github.com/googleapis/gax-java/issues/1281)) ([721617b](https://www.github.com/googleapis/gax-java/commit/721617b0cb80ebcd40c3aa2f6c5b86f679dad811))
* **operations:** Make Operations a manual client [gax-java] ([#1282](https://www.github.com/googleapis/gax-java/issues/1282)) ([5be66cd](https://www.github.com/googleapis/gax-java/commit/5be66cd3ebf2ccf34f21db40463a2d9115a77798))


### Dependencies

* update com.google.http-client:google-http-client to 1.38.1 ([#1265](https://www.github.com/googleapis/gax-java/issues/1265)) ([5815a7c](https://www.github.com/googleapis/gax-java/commit/5815a7ce815cc2fc47b39b928010de0c2cea8716))
* update common protos ([#1258](https://www.github.com/googleapis/gax-java/issues/1258)) ([7287e84](https://www.github.com/googleapis/gax-java/commit/7287e84979ba9076e4888ec3678995c08f1ea690))
* update gRPC ([#1263](https://www.github.com/googleapis/gax-java/issues/1263)) ([95a7dab](https://www.github.com/googleapis/gax-java/commit/95a7dab77800dffaf551018c85e1d78596411e8f))
* update Guava ([#1262](https://www.github.com/googleapis/gax-java/issues/1262)) ([cdc0366](https://www.github.com/googleapis/gax-java/commit/cdc0366d23317d6fddd3dc86592664449aaa8a6f))
* update iam protos to v1.0.7 ([#1266](https://www.github.com/googleapis/gax-java/issues/1266)) ([69b6dc4](https://www.github.com/googleapis/gax-java/commit/69b6dc44f5e81d5919553b9de6248346d172adcd))
* update protobuf ([#1256](https://www.github.com/googleapis/gax-java/issues/1256)) ([ef9b3aa](https://www.github.com/googleapis/gax-java/commit/ef9b3aaac30406b0b17a985ab959530c1254b145))

### [1.60.1](https://www.github.com/googleapis/gax-java/compare/v1.60.0...v1.60.1) (2020-11-19)


### Bug Fixes

* check Compute Engine environment for DirectPath ([#1250](https://www.github.com/googleapis/gax-java/issues/1250)) ([656b613](https://www.github.com/googleapis/gax-java/commit/656b613d2fe73e5bd19d43d4a2d8d0c6bb9ad5f2))


### Dependencies

* update api-common to 1.10.1 ([#1240](https://www.github.com/googleapis/gax-java/issues/1240)) ([d8b2bf7](https://www.github.com/googleapis/gax-java/commit/d8b2bf7b59d83a11e2e0eba703ed758fd1adb0ce))
* update auth libaries ([#1251](https://www.github.com/googleapis/gax-java/issues/1251)) ([d455da2](https://www.github.com/googleapis/gax-java/commit/d455da2cd73f1e015d7570e8d634864a38bdb042))
* update autovalue annotations ([#1246](https://www.github.com/googleapis/gax-java/issues/1246)) ([60bb103](https://www.github.com/googleapis/gax-java/commit/60bb10326cd3a0092d69e8388eb5f7fed55a715c))
* update dependency com.google.auto.value:auto-value to v1.7.4 ([#1031](https://www.github.com/googleapis/gax-java/issues/1031)) ([1e7e13c](https://www.github.com/googleapis/gax-java/commit/1e7e13c07bf4c79d0b3cbfd0f15a4908278c1ffa))
* update google-http-client to 1.38.0 ([#1244](https://www.github.com/googleapis/gax-java/issues/1244)) ([6b53f0f](https://www.github.com/googleapis/gax-java/commit/6b53f0fe3a95346596c670f62d34267483a12c68))
* update Guava to 30.0-android ([#1237](https://www.github.com/googleapis/gax-java/issues/1237)) ([64806c4](https://www.github.com/googleapis/gax-java/commit/64806c474f1aab87ed62f59e9746aa22c5982e96))
* update threetenbp to 1.5.0 ([#1243](https://www.github.com/googleapis/gax-java/issues/1243)) ([6232599](https://www.github.com/googleapis/gax-java/commit/6232599506fda164e5675162e71809a78258efbd))

## [1.60.0](https://www.github.com/googleapis/gax-java/compare/v1.59.1...v1.60.0) (2020-10-19)


### Features

* REST Gapic (REGAPIC) Support  ([#1177](https://www.github.com/googleapis/gax-java/issues/1177)) ([12b18ee](https://www.github.com/googleapis/gax-java/commit/12b18ee255d3fabe13bb3969df40753b29f830d5))


### Bug Fixes

* prevent npe caused by missing parentheses ([#1198](https://www.github.com/googleapis/gax-java/issues/1198)) ([b856351](https://www.github.com/googleapis/gax-java/commit/b85635123f987f9808086f14a58dd8c7418a3bd8))


### Dependencies

* upgrade grpc to 1.32.2 ([#1212](https://www.github.com/googleapis/gax-java/issues/1212)) ([03c4c0f](https://www.github.com/googleapis/gax-java/commit/03c4c0f621f439c30752122568d2a9a7703e5e16))

### [1.59.1](https://www.github.com/googleapis/gax-java/compare/v1.59.0...v1.59.1) (2020-10-05)


### Bug Fixes

* Fix race condition in BatcherImpl flush ([#1200](https://www.github.com/googleapis/gax-java/issues/1200)) ([c6308c9](https://www.github.com/googleapis/gax-java/commit/c6308c906171ce05765ccacb716aa7162d95d9a2))
* update owners file with actools-java ([#1194](https://www.github.com/googleapis/gax-java/issues/1194)) ([9977dd2](https://www.github.com/googleapis/gax-java/commit/9977dd2564ff6919fc6a6b658eb69b5ea8a66520))

## [1.59.0](https://www.github.com/googleapis/gax-java/compare/v1.58.3...v1.59.0) (2020-09-28)


### Features

* Allow user-agents to be specified by both internal headers and user headers ([#1190](https://www.github.com/googleapis/gax-java/issues/1190)) ([266329e](https://www.github.com/googleapis/gax-java/commit/266329e89642bfc6be579e600d3f995f4416ae4e)), closes [/github.com/googleapis/java-bigtable/pull/404#pullrequestreview-480972135](https://www.github.com/googleapis//github.com/googleapis/java-bigtable/pull/404/issues/pullrequestreview-480972135)


### Bug Fixes

* truncate RPC timeouts to time remaining in totalTimeout ([#1191](https://www.github.com/googleapis/gax-java/issues/1191)) ([1d0c940](https://www.github.com/googleapis/gax-java/commit/1d0c94061bab124be81a649ac3fa1ce5d9a2df23))


### Dependencies

* update guava to 29.0-android ([#1174](https://www.github.com/googleapis/gax-java/issues/1174)) ([287cada](https://www.github.com/googleapis/gax-java/commit/287cadae528549545da9e7e9d63fd70c1268e3c1)), closes [#1151](https://www.github.com/googleapis/gax-java/issues/1151)

### [1.58.3](https://www.github.com/googleapis/gax-java/compare/v1.58.2...v1.58.3) (2020-09-15)


### Bug Fixes

* [gax-java] Add speedy Bazel builds to Travis ([#1181](https://www.github.com/googleapis/gax-java/issues/1181)) ([2fb85fe](https://www.github.com/googleapis/gax-java/commit/2fb85fed095c6043ee39b63a0f7dff3fd93cbd7b))
* [gax-java] add Vim files to .gitignore ([#1179](https://www.github.com/googleapis/gax-java/issues/1179)) ([2de22b6](https://www.github.com/googleapis/gax-java/commit/2de22b6645fbfd7ada7d0067e5cdd3c2039ec190))
* [gax-java] Fix broken Bazel build ([#1180](https://www.github.com/googleapis/gax-java/issues/1180)) ([834c05e](https://www.github.com/googleapis/gax-java/commit/834c05e1d35a17f90bf8cd1b2cdce40bea451c95))

### [1.58.2](https://www.github.com/googleapis/gax-java/compare/v1.58.1...v1.58.2) (2020-08-07)


### Bug Fixes

* Settings objects should not try to read quotaProjectId from credentials ([#1162](https://www.github.com/googleapis/gax-java/issues/1162)) ([1b09bcf](https://www.github.com/googleapis/gax-java/commit/1b09bcff1ddfaed8cfa58b92c787f8fc9b08abef))

### [1.58.1](https://www.github.com/googleapis/gax-java/compare/v1.58.0...v1.58.1) (2020-08-06)


### Bug Fixes

* fix dependencies.properties resource file creation during deployment ([#1163](https://www.github.com/googleapis/gax-java/issues/1163)) ([3e7e1f1](https://www.github.com/googleapis/gax-java/commit/3e7e1f1e64bdeb23a51b5155faea975beec0bc84))
* Watchdog.shutdownNow() does not shutdown executor ([#1158](https://www.github.com/googleapis/gax-java/issues/1158)) ([6241a21](https://www.github.com/googleapis/gax-java/commit/6241a2118690d07dd28ffb9447423363f3f914e4))

## [1.58.0](https://www.github.com/googleapis/gax-java/compare/v1.57.2...v1.58.0) (2020-07-31)


### Features

* add retry logging ([#1160](https://www.github.com/googleapis/gax-java/issues/1160)) ([1575715](https://www.github.com/googleapis/gax-java/commit/15757151d4965276bd01e6772c10288959bb17ec))
* enable setting quota_project_id ([#1128](https://www.github.com/googleapis/gax-java/issues/1128)) ([20bb200](https://www.github.com/googleapis/gax-java/commit/20bb200c8019ad1df8acbfe210cea7d5e9a9a57c))
* non-retryable RPCs use totalTimeout ([#1149](https://www.github.com/googleapis/gax-java/issues/1149)) ([b7646a3](https://www.github.com/googleapis/gax-java/commit/b7646a3a959b7e5ef40158851f26ce6701da8ca4))


### Bug Fixes

* retain context timeouts in ServerStreamingAttemptCallable ([#1155](https://www.github.com/googleapis/gax-java/issues/1155)) ([461ff84](https://www.github.com/googleapis/gax-java/commit/461ff846ca551c2242bf6c60e61234997d0ba58e))

### [1.57.2](https://www.github.com/googleapis/gax-java/compare/v1.57.1...v1.57.2) (2020-07-21)


### Bug Fixes

* Fix javadoc generation on Java11 ([#1145](https://www.github.com/googleapis/gax-java/issues/1145)) ([c7a039e](https://www.github.com/googleapis/gax-java/commit/c7a039e07be02298d9dd906b08e1e1bb995e85e2))
* Preconditions only supports %s format ([#1153](https://www.github.com/googleapis/gax-java/issues/1153)) ([8145311](https://www.github.com/googleapis/gax-java/commit/8145311b38fdd3bf82a4958f8aef5313857b70c0))

### [1.57.1](https://www.github.com/googleapis/gax-java/compare/v1.57.0...v1.57.1) (2020-07-07)


### Bug Fixes

* add back javax.annotation dependency ([#1129](https://www.github.com/googleapis/gax-java/issues/1129)) ([77a4cc3](https://www.github.com/googleapis/gax-java/commit/77a4cc373914396dd343891e38cf743166668c96))


### Dependencies

* update google-auth-library to 0.21.0 ([#1134](https://www.github.com/googleapis/gax-java/issues/1134)) ([6528e5c](https://www.github.com/googleapis/gax-java/commit/6528e5cb9cec50ef01c0d2601c6db518df825747))
