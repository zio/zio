# Scheduling

> ZStream scheduling combinators for controlling emission timing and spacing of stream outputs using configurable schedule policies.

To schedule the output of a stream we use `ZStream#schedule` combinator.

Let's space between each emission of the given stream:

```scala

val stream = ZStream(1, 2, 3, 4, 5).schedule(Schedule.spaced(1.second))
```
