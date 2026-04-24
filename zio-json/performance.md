# Performance

> The following benchmarks are freely available to run on your hardware with `sbt "zioJsonJVM/jmh:run -prof gc"` and can be extended to include more niche libraries. We only compare `zio-json` against Circe and Play as they are the incumbent solutions used by most of the Scala ecosystem.

The following benchmarks are freely available to run on your hardware with `sbt "zioJsonJVM/jmh:run -prof gc"` and can be extended to include more niche libraries. We only compare `zio-json` against Circe and Play as they are the incumbent solutions used by most of the Scala ecosystem.

`zio-json`, when used in legacy mode (i.e. using a `StringReader`), is typically x2 faster than Circe and x5 faster than Play. When used with Loom, `zio-json` has finished its work before the others even begin. The following benchmarks are therefore only for legacy mode comparisons.

There are two main factors to consider when comparing the performance of JSON libraries: memory usage and operations per second. We perform measurements in one thread at a time but in a real server situation, there are multiple threads each consuming resources.

Here are JMH benchmarks (higher `ops/sec` is better, lower `MB/sec` is better) on a standard Google Maps API performance-testing dataset (stressing array and number parsing). Note that a better metric for memory usage might be `MB` per decode or encode, since it can be misleading to have the same `MB/sec` but be processing more JSON: the library that consumes the least amount of memory is likely to have highest throughput.

<!-- zioJsonJVM/jmh:run -prof gc GoogleMaps.*Success1 -->
<!-- zioJsonJVM/jmh:run -prof gc GoogleMaps.*encode* -->

```
       Decoding                    | Encoding
       ops/sec       MB/sec        | ops/sec      MB/sec
zio    15761 ± 283   1633 ± 29     | 14289 ±  84  2214 ± 12
circe   8832 ± 269   1816 ± 55     | 11980 ± 142  2030 ± 24
play    5756 ±  47   2260 ± 19     |  6669 ± 160  2677 ± 64
```

on a standard Twitter API performance-testing dataset (stressing nested case classes with lots of fields)

<!-- zioJsonJVM/jmh:run -prof gc Twitter.*Success1 -->
<!-- zioJsonJVM/jmh:run -prof gc Twitter.*encode* -->

```
       Decoding                    | Encoding
       ops/sec       MB/sec        | ops/sec      MB/sec
zio    16989 ± 113    827 ±  6     | 23085 ± 641  1791 ± 50
circe  16010 ±  72   1349 ±  6     | 15664 ± 209  1627 ± 22
play    5256 ± 165   1231 ± 39     | 15580 ± 314  2260 ± 45
```

on a standard GeoJSON performance-testing dataset (stressing nested sealed traits that use a discriminator)

<!-- zioJsonJVM/jmh:run -prof gc GeoJSON.*Success1 -->
<!-- zioJsonJVM/jmh:run -prof gc GeoJSON.*encode* -->

```
       Decoding                    | Encoding
       ops/sec       MB/sec        | ops/sec       MB/sec
zio    17104 ± 155   2768 ± 25     | 5372 ± 26      817 ±  4
circe   8388 ± 118   2879 ± 41     | 4762 ± 47      592 ±  6
play     704 ±   9   3946 ± 55     | 2587 ± 24     1091 ± 10
```

and on a standard synthetic performance-testing dataset (stressing nested recursive types)

<!-- zioJsonJVM/jmh:run -prof gc Synthetic.*Success -->
<!-- zioJsonJVM/jmh:run -prof gc Synthetic.*encode* -->

```
       Decoding                    | Encoding
       ops/sec       MB/sec        | ops/sec       MB/sec
zio    59099 ± 1307  2108 ± 46     | 32048 ±  240  2573 ± 19
circe  19609 ±  370  2873 ± 53     | 13830 ±  109  1730 ± 14
play    9001 ±  182  3348 ± 67     | 14529 ±  200  3533 ± 48
```

`zio-json` easily wins every benchmark except ops/sec for the Twitter test data where Circe matches ops/sec but loses heavily on memory usage. Play loses on every benchmark.

## Even More Performance

If `zio-json` isn't fast enough for you, then try out [jsoniter-scala](https://github.com/plokhotnyuk/jsoniter-scala); whereas `zio-json` is fully integrated into ZIO, including streams and pipeline support, jsoniter is library agnostic.

JSON is an inefficient transport format and everybody would benefit from a port of this library to msgpack or protobuf. For legacy services, a port supporting XML is also be possible.
