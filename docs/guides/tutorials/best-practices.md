---
id: best-practices
title: "Best Practices"
sidebar_label: "Best Practices"
---

# ZIO Best Practices

This guide covers common pitfalls and best practices when working with ZIO.

## Compiler Flags

Add these compiler flags to your build to catch common mistakes:

```scala
// In build.sbt
scalacOptions ++= Seq(
  "-Ywarn-value-discard",
  "-Xfatal-warnings",
)
```

## Common Pitfalls

### 1. Discarding Effects

**Bad:** Effects are silently discarded

```scala
def myApp: ZIO[Any, Nothing, Unit] = ZIO.sprintln("Step 1")
  ZIO.sprintln("Step 2")  // WARNING: value discarded!
```

**Good:** Chain effects properly

```scala
def myApp: ZIO[Any, Nothing, Unit] =
  ZIO.sprintln("Step 1") *> ZIO.sprintln("Step 2")
```

### 2. Creating Mutable State Inside Loops

**Bad:** Creating a new Ref on each iteration

```scala
ZIO.foreach(1 to 10) { i =>
  for {
    ref <- Ref.make(0)  // BAD: New ref on each iteration!
    _ <- ref.update(_ + i)
  } yield ()
}
```

**Good:** Create state once, use it everywhere

```scala
for {
  ref <- Ref.make(0)  // GOOD: Create once
  _ <- ZIO.foreach(1 to 10) { i =>
    ref.update(_ + i)
  }
} yield ()
```

### 3. Using assert Incorrectly in Tests

**Bad:** Multiple assert calls (only the last one matters!)

```scala
test("bad test") {
  for {
    result <- myFunction
  } yield {
    assert(result == 1)  // Discarded!
    assert(result > 0)   // Only this is checked!
  }
}
```

**Good:** Combine assertions with &&

```scala
test("good test") {
  for {
    result <- myFunction
  } yield {
    assert(result == 1) && assert(result > 0)
  }
}
```

### 4. Blocking the ZIO Executor

**Bad:** Using Thread.sleep or blocking operations

```scala
ZIO.succeed(Thread.sleep(1000))  // BAD: Blocks the executor!
```

**Good:** Use ZIO sleep or blocking service

```scala
ZIO.sleep(1.second)  // GOOD: Non-blocking
```

### 5. Forgetting to Provide Environment

**Bad:** Using services without providing them

```scala
val myApp: ZIO[Console, Nothing, Unit] = Console.printLine("Hello")
// myApp.run  // ERROR: missing Console
```

**Good:** Provide the environment

```scala
myApp.provide(Console.default)
```

## Summary

| Do | Don't |
|---|---|
| Use compiler flags | Discard effects silently |
| Create state once | Create Ref in loops |
| Combine assertions with && | Use multiple assert calls |
| Use ZIO.sleep | Use Thread.sleep |
| Provide environment | Forget to provide services |
