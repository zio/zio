---
id: debug-a-zio-application
title: "Tutorial: How to Debug a ZIO Application?"
sidebar_label: "Debugging a ZIO Application"
---

## Introduction

Writing applications using functional programming helps us to write a base code that is less error-prone and more predictable. However, we often make mistakes when developing applications. Even though we have written lots of tests, we might have missed some areas of our code that could have caused errors. Finally, in the middle of one night, the alarm starts calling and paging us to take the right action for the error in production. This is where debugging comes in. It is a process of finding the root cause of the error and then fixing it. Sometimes this process takes a large amount of time and effort.

In this article, we are going to learn how to debug a ZIO application. We will start with the simplest example of a ZIO application and then move to the more complex ones.

## Debugging an Ordinary Scala Application

Before talking about debugging functional effects, we need to understand how to debug an ordinary Scala application. In scala, one simple way to debug a code is to use `print` statements to print the intermediate values of the computation to the console.

Assume we have the following fibonacci function:

```scala mdoc:compile-only
def fib(n: Int): Int = {
  @annotation.tailrec
  def go(n: Int, a: Int, b: Int): Int =
    if (n == 0) a
    else go(n - 1, b, a + b) 
  go(n, 0, 1)
}
```

The implementation of this function is correct, but for pedagogical purposes, let's debug it by printing the intermediate values of the computation:

```scala mdoc:compile-only
def fib(n: Int): Int = {
  @annotation.tailrec
  def go(n: Int, a: Int, b: Int): Int =
    if (n == 0) {
      println(s"final result: $a")
      a
    } else {
      println(s"go(${n - 1}, $b, ${a + b})")
      go(n - 1, b, a + b)
    }
  println(s"go($n, 0, 1)")
  go(n, 0, 1)
}
```

Now if we call `fib(3)`, we will see the following output:

```
go(3, 0, 1)
go(2, 1, 1)
go(1, 1, 2)
go(0, 2, 3)
final result: 2
```

The `print` statements are the easiest way for lazy programmers to debug their code. However, they are not the most efficient way to debug code.

## Debugging an Ordinary Scala Using a Debugger

Debugging using print statements is usable in some cases, and sometimes it is not performant. Another way to debug a code is to use a debugger. A debugger is a program that allows us to step through the code and see the intermediate values of the computation. Some IDEs like IntelliJ IDEA or Visual Studio Code have built-in debuggers. We can use these to debug our code.

To learn how to use a debugger in each of the IDEs, we can look at the following links:
- [IntelliJ IDEA](https://www.jetbrains.com/help/idea/debugging-scala.html)
- [Visual Studio Code](https://code.visualstudio.com/docs/editor/debugging)

## Debugging a ZIO Application Using `debug` Effect

When we use functional effects like `ZIO`, we are creating the description of the computation that we want to run. For example, assume we have the following code:

```scala mdoc:compile-only
import zio._

val effect: ZIO[Any, Nothing, Unit] = ZIO.succeed(3).map(_ * 2)
```

The `effect` itself is a description of the computation that we want to run. So we can't use print statements to debug effects directly. For example, if we write `println(effect)`, we will get something like this:

```scala
OnSuccess(<empty>.MainApp.effect(MainApp.scala:4),Sync(<empty>.MainApp.effect(MainApp.scala:4),MainApp$$$Lambda$23/0x00000008000bc440@44a3ec6b),zio.ZIO$$Lambda$25/0x00000008000ba040@71623278)
```

This is not the expected output. We want to see the result of the computation, not the description of the computation. Why did this happen? Because we haven't run the computation yet.

So keep in mind that, unlike the ordinary scala print statements, we can't use print statements directly to debug functional effects, unless we unsafely run the computation:

```scala mdoc:compile-only
import zio._

val effect: ZIO[Any, Nothing, Int] = 
  ZIO.succeed(3).map(_ * 2)

val executedEffect: Int =
  Unsafe.unsafe { implicit unsafe =>
    Runtime.default.unsafe.run(effect).getOrThrowFiberFailure()
  }
  
println(s"executedEffect: $executedEffect")
```

This will print the result of the computation. But, this is not the idiomatic way to debug functional effects.

Simple _print statements_ are not composable with ZIO applications. So we can't use them to debug ZIO applications easily. So instead of print statements, we should use ZIO effects to debug ZIO applications.

For example, assume we have written the Fibonacci function using the `ZIO` data type:

```scala mdoc:compile-only
import zio._

def fib(n: Int): ZIO[Any, Nothing, Int] = {
  if (n <= 1) ZIO.succeed(n)
  else fib(n - 1).zipWith(fib(n - 2))(_ + _)
}
```
