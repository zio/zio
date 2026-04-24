# Merging the Error Channel into the Success Channel

> With `ZIO#merge` we can merge the error channel into the success channel:

With `ZIO#merge` we can merge the error channel into the success channel:

```scala

val merged : ZIO[Any, Nothing, String] =
  ZIO.fail("Oh uh!") // ZIO[Any, String, Nothing]
    .merge           // ZIO[Any, Nothing, String]
```

If the error and success channels were of different types, it would choose the supertype of both.
