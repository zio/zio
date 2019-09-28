---
id: usecases_streaming
title:  "Streaming"
---

All material on this pages assumes knowledge about the `ZIO[R, E, A]` type.

## Why we need streams?
TBD

## How `zio-streams` are build

The backbone of `zio-streams` are 3 types: `ZStream`, `Pull` and `ZSink`. 
`ZStream[R, E, A]` represents the stream itself, which produces effectful values of type `A` and might end up with 
error `E`. The `R` parameter has exactly the same meaning as as in the `ZIO` type that is to represent the environment
requirements to run this stream.
Second type, `Pull` should be understood as a handle by which consecutive effectful elements are being pulled into the
stream (thus the name). `Pull`'s exact definition is a type alias for the `ZIO` type:
`Pull[-R, +E, +A] = ZIO[R, Option[E], A]`. The contract states that each evaluation of the `Pull` type either produces a
value `A` which will flow through the stream or produces an `Option[E]` which signals an actual error in `Some(_)` case 
and flags the end of the stream in the`None` case. To emphasize this - the same `Pull` is being run multiple times to
get items of the stream produced.

How are `ZStream` and `Pull` related? `ZStream` is constructed based on the `Pull` type - passed as a constructor
argument wrapped in `ZManaged`. Why the `ZManaged` indirection? Since our `Pull` contract says in most cases we need to assume it
is not thread-safe so it should not be evaluated from multiple fibers. The evaluation also cannot be safely restarted after 
interruption in all cases. When the `Pull` fails with a `None` it is not safe to evaluate it again. All of those seem
very restrictive but keeping in mind that a stream will talk to external world possibly multiple times, which can be
stateful, those assumptions are the only safe ones we can make. To give a concrete example, if our stream job is to
interact with an external API that has time throttling of request it can accept from a single user then even if our 
stream implementation does proper buffering and back-off in presence of two concurrent not coordinated clients both of
them will probably end up with failures. Other example for getting `None` as error value. If our stream is responsible 
for reading a file, `None` represents end of the file. Trying to pull more values after the file was wholly read makes
no sense.

The last type widely used in `zio-stream` is the `ZSink` type. Sink should be understood as a place that runs the stream
and where the evaluated items are being passed down to. Sinks also have control over the evaluation of streams, possibly
ending them earlier. `Sink[-R, +E, +A0, -A, +B]` contract states that sinks consume multiple values of type `A` coming
from the `ZStream` type, producing either a single value of type B and reminders of type `A0` or an error of type `E`. 
If we recall the `ZStream` and `Pull` definitions we can fast recognize that there is not space of "state" in both of
them. This responsibility is being passed on to `ZSink` type. Example where we need such state would be a scenario where
we want to count the number of elements that flow though the stream. Here we're moving out of the simple scenario of 
transforming the incoming data into, keeping derived data based on the history of execution. Thus to support the need
for such operators `ZSink` has a standardized interface to make this possible. First of all any sink may have some kind of
state. In the general case `State` is a path-dependent type defined in `ZSink` that is hidden from the client of 
the stream and might be anything that is needed so it is fully abstract. Next there are number of methods exposed on 
`ZSink`. Let's enumerate them with simplified types:
- `initial` - returns initial `state` of the stream which might be anything
- `step` - is a function `(state, a) => state`. Step takes the current state, incoming item and calculates the next state.
- `extract`: `state => (B, Chunk[A0])`, calculates the final value that is the result of running the stream and list of 
  values that were not consumed.
- `cont`: `state => boolean`, controls should the stream be evaluated further.

Let's go through the `run` definition on `ZStream` which allows for connecting any `ZSink` to have a better 
understanding how those two types work together.

```scala
def run[R1 <: R, E1 >: E, A0, A1 >: A, B](sink: ZSink[R1, E1, A0, A1, B]): ZIO[R1, E1, B] =
    sink.initial.flatMap { initial =>
      self.process.use { as =>
        def pull(state: sink.State): ZIO[R1, E1, B] =
          as.foldM(
            {
              case Some(e) => ZIO.fail(e)
              case None    => sink.extract(state).map(_._1)
            },
            sink.step(state, _).flatMap { step =>
              if (sink.cont(step)) pull(step)
              else sink.extract(step).map(_._1)
            }
          )

        pull(initial)
      }
    }
```

The line `sink.initial.flatMap { initial =>` extracts for us the initial state in which the sink is. The second line
`self.process.use { as =>` exposes for us the `Pull` handle for generating values. At this point we need to read the
values for as long as 
- the stream doesn't end
- there is no error
- the `ZSink` decides that it doesn't want any more items

We call `foldM` function on the `Pull` handle. First pair of parenthesis handles the erroneous case. For `Some(e)` there was
an actual error during generation of value `A` and we finish with `ZIO` value set to error. For the `None` case there
are no more values to generate so we should return what was already accumulated in the `ZSink`. For this purpose we're
calling the `extract` method on sink which returns the `B` item and reminder which we throw away in this case as 
irrelevant. The second pair of parenthesis handles the case where an item of type `A` was generated. We use this item
to make progress in the sink calling the `step` method which generates for our our next state. That state is then used 
to asses if we should continues by calling the `cont` function on the sink. If so, we're recursively calling the same
process again (`def pull(state: sink.State): ZIO[R1, E1, B]`) just with new state generated by the previous computation 
we just finished. If it is not the case and the sink instructs us to stop processing we're using the `extract` function
to get the `B` value.




## Pure streams

## Statefully transforming streams

### Creating own streaming combinator
TBD - will be based on `take(n:Int)`

## How to achieve concurrency
TBD
