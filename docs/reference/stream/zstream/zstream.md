---
id: zstream
title: "ZStream"
---

## Scheduling

To schedule the output of a stream we use `ZStream#schedule` combinator.

Let's space between each emission of the given stream:

```scala mdoc:silent:nest
val stream = ZStream(1, 2, 3, 4, 5).schedule(Schedule.spaced(1.second))
```



