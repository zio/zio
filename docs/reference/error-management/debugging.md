---
id: debugging
title: "Debugging"
---

When we are writing an application using the ZIO effect, we are writing workflows as data transformers. So there are lots of cases where we need to debug our application by seeing how the data transformed through the workflow. We can add or remove debugging capability without changing the signature of our effect:

```scala mdoc:silent:nest
import zio._

ZIO.ifZIO(
  Random.nextIntBounded(10)
    .debug("random number")
    .map(_ % 2)
    .debug("remainder")
    .map(_ == 0)
)(
  onTrue = ZIO.succeed("Success"),
  onFalse = ZIO.succeed("Failure")
).debug.repeatWhile(_ != "Success")
```

The following could be one of the results of this program:

```
random number: 5
remainder: 1
Failure
random number: 1
remainder: 1
Failure
random number: 2
remainder: 0
Success
```
