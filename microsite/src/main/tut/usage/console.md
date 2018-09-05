---
layout: docs
section: usage
title: "Console"
---

# {{page.title}}

ZIO provides a few primitives for interacting with the console.
These can be imported using the following

```tut:silent
import scalaz.zio.console._
```

## Print to screen

Printing to the screen is one of the most basic I/O operations.
In order to do so and preserve referential transparency, we can use `putStr` and `putStrLn`

```tut
// Print without trailing line break
putStr("Hello World")

// Print string and include trailing line break
putStrLn("Hello World")
```

## Read console input

For use cases that require user-provided input via the console, `getStrLn` allows importing
values into a pure program.

```tut
val echo = getStrLn.flatMap(putStrLn)
```
