# ZLambdaRunner

> `ZLambdaRunner` is the entry point of the `zio-lambda` module. It turns a function from an event and a [`Context`](./context.md) into a program that serves AWS Lambda invocations. Both of its methods return a `RIO[R, Unit]`, so you return them from the `run` method of a `ZIOAppDefault`.

```scala
object ZLambdaRunner {
  def serve[R, IN: JsonDecoder, OUT: JsonEncoder, ERR <: Throwable](
    appFunction: (IN, Context) => ZIO[R, ERR, OUT]
  ): RIO[R, Unit]

  def serveRaw[R](
    rawFunction: (String, Context) => ZIO[R, Throwable, String]
  ): RIO[R, Unit]
}
```

## The Invocation Loop

Both methods run the same loop. For each invocation, the runner asks the Lambda Runtime API for the next event, builds a `Context`, calls your function and reports the outcome to Lambda. The loop never ends on its own.

- If your effect succeeds, the runner sends the result to Lambda as the invocation response.
- If your effect fails, or the event cannot be decoded, the runner sends an invocation error to Lambda and then waits for the next invocation.

## serve

`serve` decodes the event JSON into `IN` with its `JsonDecoder`, calls your function, and encodes the `OUT` value with its `JsonEncoder`. Both codecs are implicit parameters, so they must be in scope, usually in the companion objects of the types.

The error type `ERR` must be a subtype of `Throwable`. The environment `R` is provided by your application, for example with `.provide(...)` on the effect your function returns.

If decoding fails, the runner fails the invocation with a `Throwable` whose message starts with `Error decoding json. Json=` followed by the received payload and the zio-json error.

## serveRaw

`serveRaw` passes the event to your function as the raw JSON `String` and sends back the `String` you return, with no codecs. Its error type is `Throwable`, and the string you return is sent to Lambda as is, so it must already be valid JSON if the caller expects JSON.

## ZLambda (Deprecated)

`ZLambda[E, A]` is an abstract class that predates `ZLambdaRunner`. It is deprecated since 1.0.3. It extends `ZIOAppDefault`, requires a `JsonDecoder[E]` and a `JsonEncoder[A]`, and asks you to implement `def apply(event: E, context: Context): Task[A]`. To migrate, replace the class with an `object` that extends `ZIOAppDefault` and pass the same function to `ZLambdaRunner.serve`, as shown in [Getting Started](../getting-started.md).
