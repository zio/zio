---
id: examples
title: "Examples"
---

Let's write an application that takes numerator and denominator from the user and then print the result back to the user:

```scala mdoc:compile-only
import zio._
import java.io.IOException

object MainApp extends ZIOAppDefault {
  def run =
    for {
      a <- readNumber("Enter the first number  (a): ")
      b <- readNumber("Enter the second number (b): ")
      r <- divide(a, b)
      _ <- Console.printLine(s"a / b: $r")
    } yield ()

  def readNumber(msg: String): ZIO[Any, IOException, Int] =
    Console.print(msg) *> Console.readLine.map(_.toInt)

  def divide(a: Int, b: Int): ZIO[Any, Nothing, Int] =
    if (b == 0)
      ZIO.die(new ArithmeticException("divide by zero")) // unexpected error
    else
      ZIO.succeed(a / b)
}
```

Now let's try to enter the zero for the second number and see what happens:

```scala
Please enter the first number  (a): 5
Please enter the second number (b): 0
timestamp=2022-02-14T09:39:53.981143209Z level=ERROR thread=#zio-fiber-0 message="Exception in thread "zio-fiber-2" java.lang.ArithmeticException: divide by zero
at MainApp$.$anonfun$divide$1(MainApp.scala:16)
at zio.ZIO$.$anonfun$die$1(ZIO.scala:3384)
at zio.internal.FiberContext.runUntil(FiberContext.scala:255)
at zio.internal.FiberContext.run(FiberContext.scala:115)
at java.base/java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1130)
at java.base/java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:630)
at java.base/java.lang.Thread.run(Thread.java:831)
at <empty>.MainApp.divide(MainApp.scala:16)"
```

As we see, because we entered the zero for the denominator, the `ArithmeticException` defect, makes the application crash.

Defects are any _unexpected errors_ that we are not going to handle. They will propagate through our application stack until they crash the whole.

Defects have many roots, most of them are from a programming error. Errors will happen when we haven't written the application with best practices. For example, one of these practices is that we should validate the inputs before providing them to the `divide` function. So if the user entered the zero as the denominator, we can retry and ask the user to return another number:

```scala mdoc:compile-only
import zio._
import java.io.IOException

object MainApp extends ZIOAppDefault {
  def run =
    for {
      a <- readNumber("Enter the first number  (a): ")
      b <- readNumber("Enter the second number (b): ").repeatUntil(_ != 0)
      r <- divide(a, b)
      _ <- Console.printLine(s"a / b: $r")
    } yield ()

  def readNumber(msg: String): ZIO[Any, IOException, Int] =
    Console.print(msg) *> Console.readLine.map(_.toInt)

  def divide(a: Int, b: Int): ZIO[Any, Nothing, Int] = ZIO.succeed(a / b)
}
```

Another note about defects is that they are invisible, and they are not typed. We cannot expect what defects will happen by observing the typed error channel. In the above example, when we run the application and enter noninteger input, another defect, which is called `NumberFormatException` will crash the application:

```scala
Enter the first number  (a): five
timestamp=2022-02-18T06:36:25.984665171Z level=ERROR thread=#zio-fiber-0 message="Exception in thread "zio-fiber-2" java.lang.NumberFormatException: For input string: "five"
	at java.base/java.lang.NumberFormatException.forInputString(NumberFormatException.java:67)
	at java.base/java.lang.Integer.parseInt(Integer.java:660)
	at java.base/java.lang.Integer.parseInt(Integer.java:778)
	at scala.collection.StringOps$.toInt$extension(StringOps.scala:910)
	at MainApp$.$anonfun$readNumber$3(MainApp.scala:16)
	at MainApp$.$anonfun$readNumber$3$adapted(MainApp.scala:16)
  ...
	at <empty>.MainApp.run(MainApp.scala:9)"
```

The cause of this defect is also a programming error, which means we haven't validated input when parsing it. So let's try to validate the input, and make sure that it is a number. We know that if the entered input does not contain a parsable `Int` the `String#toInt` throws the `NumberFormatException` exception. As we want this exception to be typed, we import the `String#toInt` function using the `ZIO.attempt` constructor. Using this constructor the function signature would be as follows:

```scala mdoc:compile-only
import zio._

def parseInput(input: String): ZIO[Any, Throwable, Int] =
  ZIO.attempt(input.toInt)
```

Since the `NumberFormatException` is an expected error, and we want to handle it. So we type the error channel as `NumberFormatException`.

To be more specific, we would like to narrow down the error channel to the `NumberFormatException`, so we can use the `refineToOrDie` operator:

```scala mdoc:compile-only
import zio._

def parseInput(input: String): ZIO[Any, NumberFormatException, Int] =
  ZIO.attempt(input.toInt)                 // ZIO[Any, Throwable, Int]
    .refineToOrDie[NumberFormatException]  // ZIO[Any, NumberFormatException, Int]
```

The same result can be achieved by succeeding the `String#toInt` and then widening the error channel using the `ZIO#unrefineTo` operator:

```scala mdoc:compile-only
import zio._

def parseInput(input: String): ZIO[Any, NumberFormatException, Int] =
  ZIO.succeed(input.toInt)                 // ZIO[Any, Nothing, Int]
    .unrefineTo[NumberFormatException]     // ZIO[Any, NumberFormatException, Int]
```

Now, let's refactor the example with recent changes:

```scala mdoc:compile-only
import zio._
import java.io.IOException

object MainApp extends ZIOAppDefault {
  def run =
    for {
      a <- readNumber("Enter the first number  (a): ")
      b <- readNumber("Enter the second number (b): ").repeatUntil(_ != 0)
      r <- divide(a, b)
      _ <- Console.printLine(s"a / b: $r")
    } yield ()

  def parseInput(input: String): ZIO[Any, NumberFormatException, Int] =
    ZIO.attempt(input.toInt).refineToOrDie[NumberFormatException]

  def readNumber(msg: String): ZIO[Any, IOException, Int] =
    (Console.print(msg) *> Console.readLine.flatMap(parseInput))
      .retryUntil(!_.isInstanceOf[NumberFormatException])
      .refineToOrDie[IOException]

  def divide(a: Int, b: Int): ZIO[Any, Nothing, Int] = ZIO.succeed(a / b)
}
```
