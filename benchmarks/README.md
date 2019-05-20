# Benchmarks

Benchmarks are implemented using [JMH](http://openjdk.java.net/projects/code-tools/jmh) and 
run with [sbt-jmh plugin](https://github.com/ktoso/sbt-jmh).

To execute set of benchmarks from one class type following in `sbt` terminal: 
```
benchmarks/jmh:run .*YourClassWithBenchmarks
```
Please consult [JMH](http://openjdk.java.net/projects/code-tools/jmh) and [sbt-jmh plugin](https://github.com/ktoso/sbt-jmh)
websites for more details about creating and running benchmarks.
