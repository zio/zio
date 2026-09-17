---
id: fatals
title: "Fatal Errors"
description: "Learn about fatal errors in ZIO — VirtualMachineError and its subtypes on the JVM — that bypass all error handling operators and immediately terminate the application."
keywords:
  - "fatal errors"
  - "VirtualMachineError"
  - "StackOverflowError"
  - "OutOfMemoryError"
  - "reportFatal"
  - "catastrophic errors"
  - "JVM errors"
---

In ZIO on the JVM platform, the `VirtualMachineError` and all its subtypes are the only errors considered fatal by the ZIO runtime. So if during the running application, the JVM throws any of these errors like `StackOverflowError`, the ZIO runtime considers it as a catastrophic fatal error. So it will interrupt the whole application immediately without safe resource interruption. None of the `ZIO#catchAll` and `ZIO#catchAllDefects` can catch these fatal errors. At most, if we set the `Runtime.setReportFatal`, the application will log the stack trace before interrupting the entire application.

:::note[Scala.js]
On Scala.js, the JavaScript engine never throws `VirtualMachineError` instances at runtime, so in practice no error is ever classified as fatal by the ZIO runtime on Scala.js. The fatal-error category exists in ZIO's shared code (the `isFatal` check runs on every platform), but it is never triggered by the JS engine.
:::

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

Note that we can change the default way to report fatal errors using the `Runtime.setReportFatal` layer.
