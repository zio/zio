---
id: howto_fiber_dump
title:  "Sending Interrupt Signal to get fiber dump"
---

You can send a `SIGUSR2` signal from your os to interrupt your app and a get a fiber dump of all fibers.

You need to add the hook by including `onInterruptSignal` method in your runtime environment.

## Example application 

```scala mdoc:reset
import zio.ZIO
import zio.ZEnv
import zio.console._

object Example extends App {

  def run(args: List[String]): ZIO[ZEnv, Nothing, Int] =
    effect.as(0)

  val effect = for {
    _ <- putStrLn("Starting program")
    runtime <- ZIO.runtime[Console]
    _ <- ZIO.effectTotal(runtime.onInterruptSignal)
        _ <- putStrLn("hook added")
    _ <- ZIO.infinity
  } yield ()
}
```

## To send an interrupt

```bash
kill -SIGUSR2 <process id>
```

## Sample Fiber dump

```
#0 (2m136s136306ms) 
   Status: Suspended(interruptible, 0 asyncs, zio.internal.FiberContext.await(FiberContext.scala:707))
Fiber:Id(1584230461711,0) was supposed to continue to:
  a future continuation at zio.Fiber.join(Fiber.scala:180)
  a future continuation at zio.Fiber.join(Fiber.scala:180)
  a future continuation at zio.App.main(App.scala:62)
  a future continuation at zio.App.main(App.scala:56)

Fiber:Id(1584230461711,0) execution trace:
  at zio.internal.FiberContext.await(FiberContext.scala:707)
  at zio.App.main(App.scala:62)
  at zio.App.main(App.scala:57)
  at zio.App.main(App.scala:57)

Fiber:Id(1584230461711,0) was spawned by: <empty trace>


#1 (2m136s136275ms) 
   Status: Suspended(interruptible, 0 asyncs, zio.clock.package$Clock$Service$$anon$1.sleep(package.scala:45))
Fiber:Id(1584230461744,1) was supposed to continue to:
  a future continuation at zio.ZIO$._IdentityFn(ZIO.scala:3224)
  a future continuation at zio.ZIO.onInterrupt(ZIO.scala:902)
  a future continuation at zio.ZIO$.infinity(ZIO.scala:2731)
  a future continuation at zio.Example$.effect(Example.scala:15)
  a future continuation at zio.Example$.run(Example.scala:8)

Fiber:Id(1584230461744,1) execution trace:
  at zio.clock.package$Clock$Service$$anon$1.sleep(package.scala:45)
  at zio.ZIO$.effectAsyncInterrupt(ZIO.scala:2193)
  at zio.ZIO$.effectAsyncInterrupt(ZIO.scala:2193)
  at zio.clock.package$.sleep(package.scala:92)
  at zio.Example$.effect(Example.scala:15)
  at zio.console.package$Console$Service$$anon$1.putStrLn(package.scala:57)
  at zio.console.package$.putStrLn(package.scala:101)
  at zio.Example$.effect(Example.scala:14)
  at zio.Example$.effect(Example.scala:13)
  at zio.Example$.effect(Example.scala:13)
  at zio.ZIO$.runtime(ZIO.scala:3061)
  at zio.ZIO$.runtime(ZIO.scala:3060)
  at zio.ZIO$.runtime(ZIO.scala:3060)
  at zio.ZIO$AccessPartiallyApplied$.apply(ZIO.scala:3338)
  at zio.Example$.effect(Example.scala:12)
  at zio.console.package$Console$Service$$anon$1.putStrLn(package.scala:57)
  at zio.console.package$.putStrLn(package.scala:101)

Fiber:Id(1584230461744,1) was spawned by:

Fiber:Id(1584230461711,0) was supposed to continue to:
  a future continuation at zio.App.main(App.scala:57)
  a future continuation at zio.App.main(App.scala:56)

Fiber:Id(1584230461711,0) ZIO Execution trace: <empty trace>

Fiber:Id(1584230461711,0) was spawned by: <empty trace>
```
