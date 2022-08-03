---
id: fatals
title: "Fatal Errors"
---

In ZIO on the JVM platform, the `VirtualMachineError` and all its subtypes are the only errors considered fatal by the ZIO runtime. So if during the running application, the JVM throws any of these errors like `StackOverflowError`, the ZIO runtime considers it as a catastrophic fatal error. So it will interrupt the whole application immediately without safe resource interruption. None of the `ZIO#catchAll` and `ZIO#catchAllDefects` can catch these fatal errors. At most, if we set the `Runtime.setReportFatal`, the application will log the stack trace before interrupting the entire application.

Here is an example of manually creating a fatal error. Although we are ignoring all expected and unexpected errors, the fatal error interrupts the whole application:

```scala mdoc:compile-only
import zio._

object MainApp extends ZIOAppDefault {
  def run =
    ZIO
      .attempt(
        throw new StackOverflowError(
          "The call stack pointer exceeds the stack bound."
        )
      )
      .catchAll(_ => ZIO.unit)       // ignoring all expected errors
      .catchAllDefect(_ => ZIO.unit) // ignoring all unexpected errors
}
```

The output will be something like this:

```scala
java.lang.StackOverflowError: The call stack pointer exceeds the stack bound.
at zio.examples.MainApp$.$anonfun$run$1(MainApp.scala:10)
at zio.ZIO$.liftedTree1$1(ZIO.scala:2603)
at zio.ZIO$.$anonfun$attempt$1(ZIO.scala:2603)
at zio.ZIO$.$anonfun$isFatalWith$1(ZIO.scala:3571)
at zio.internal.FiberContext.runUntil(FiberContext.scala:410)
at zio.internal.FiberContext.run(FiberContext.scala:111)
at zio.Runtime.unsafeRunWithRefs(Runtime.scala:400)
  ...
**** WARNING ****
Catastrophic error encountered. Application not safely interrupted. Resources may be leaked. Check the logs for more details and consider overriding `Runtime.reportFatal` to capture context.
```

Note that we can change the default way to report fatal errors using `Runtime#reportFatal` or the `Runtime.setReportFatal` layer.
