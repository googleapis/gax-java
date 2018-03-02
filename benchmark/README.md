Benchmarking
============

This sub-project contains micro-benchmarks for commonly used GAX functionalities.

To run all of the benchmarks:
```
# Run from project root directory
./gradlew benchmark:jmh
```


To run a benchmark, run the following command
```
# Run from project root directory
./gradlew benchmark:jmh -Pinclude="com.google.api.gax.grpc.CallableBenchmark"
```
substituting any benchmark class for `com.google.api.gax.grpc.CallableBenchmark`.
