# Raincatcher Demo Benchmark

This stub of benchmark uses two distinct operations:
* portal creates new workorders (by a given rate) and deletes completed ones (as soon as it finds one)
* mobile logs in and simulates the sync protocol in loop

During the sync cycle, with certain probability the mobile user starts filling out a workorder or completes it (always in these two steps).

## Running the benchmark

```
mvn install
java -cp target/raincatcher-benchmark.jar:target/raincatcher-benchmark-tests.jar Engine
```

The test duration, probabilities, number of users etc. can be adjusted using system properties.
See `src/test/scala/com/feedhenry/raincatcher/Options.scala` for details.