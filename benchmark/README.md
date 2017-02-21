Benchmarking
============

This sub-project contains micro-benchmarks for commonly used GAX functionalities.

To run a benchmark, run the following command
```
# Run from project root directory
./gradlew benchmark:run -PcaliperArgs="['com.google.api.gax.grpc.CallableBenchmark']"
```
substituting any benchmark class for `com.google.api.gax.grpc.CallableBenchmark`.

For help menu explaining various flags,
```
./gradlew benchmark:run -PcaliperArgs="['-h']"
```
