---
id: converting-option-on-values-to-option-on-errors-and-vice-versa
title: "Converting Option on Values to Option on Errors and Vice Versa"
---

We can extract a value from a Some using `ZIO#some` and then we can unsome it again using `ZIO#unsome`:

```scala
ZIO.attempt(Option("something")) // ZIO[Any, Throwable, Option[String]]
  .some                          // ZIO[Any, Option[Throwable], String]
  .unsome                        // ZIO[Any, Throwable, Option[String]]
```
