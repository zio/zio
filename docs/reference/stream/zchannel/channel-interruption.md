---
id: channel-interruption
title: "Channel Interruption"
---

We can interrupt a channel using the `ZChannel.interruptWhen` operator. It takes a ZIO effect that will be evaluated, if it finishes before the channel is closed, it will interrupt the channel, and the terminal value of the returned channel will be the success value of the effect:

```scala mdoc:silent
import zio._
import zio.stream._

def randomNumbers: ZChannel[Any, Any, Any, Any, Nothing, Int, Nothing] =
  ZChannel
    .fromZIO(Random.nextIntBounded(100))
    .flatMap(ZChannel.write) *>
    ZChannel.fromZIO(ZIO.sleep(1.second)) *> randomNumbers

randomNumbers.interruptWhen(ZIO.sleep(3.seconds).as("Done!")).runCollect.debug
// One output: (Chunk(84,57,70),Done!)
```

Another version of `interruptWhen` takes a `Promise` as an argument. It will interrupt the channel when the promise is fulfilled:

```scala mdoc:compile-only
import zio.stream._

for {
  p <- Promise.make[Nothing, Unit]
  f <- randomNumbers
    .interruptWhen(p)
    .mapOutZIO(e => Console.printLine(e))
    .runDrain
    .fork
  _ <- p.succeed(()).delay(5.seconds)
  _ <- f.join
} yield ()

// Output:
// 74
// 60
// 52
// 52
// 79
``` 
