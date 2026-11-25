# Getting Started

> A ZIO Lambda function is an ordinary function from an event and a `Context` to a ZIO effect. ZIO Lambda decodes the JSON event into your Scala type, runs the function, and encodes the result back to JSON for AWS.

## Writing a Handler

To write a handler, define an `object` that extends `ZIOAppDefault` and pass your function to `ZLambdaRunner.serve` in its `run` method. The function takes the decoded event and the `Context`, and returns a `ZIO`:

```scala
import zio._
import zio.lambda._
import zio.json._

final case class GreetingEvent(name: String)

object GreetingEvent {
  implicit val decoder: JsonDecoder[GreetingEvent] = DeriveJsonDecoder.gen[GreetingEvent]
}

final case class GreetingResponse(message: String)

object GreetingResponse {
  implicit val encoder: JsonEncoder[GreetingResponse] = DeriveJsonEncoder.gen[GreetingResponse]
}

object GreetingHandler extends ZIOAppDefault {

  val greet = (event: GreetingEvent, context: Context) =>
    ZIO.succeed(GreetingResponse(s"Hello, ${event.name}! Request ${context.awsRequestId}"))

  override val run = ZLambdaRunner.serve(greet)
}
```

`ZLambdaRunner.serve` needs a `JsonDecoder` for the event type and a `JsonEncoder` for the response type. It looks them up as implicits, so put each codec in the companion object of its type, as the example does. The error type of your function must be a subtype of `Throwable`. The example's effect cannot fail, so its error type is `Nothing`, which satisfies that rule.

The runner loops forever. It asks the AWS Lambda Runtime API for the next invocation, decodes the payload, runs your function, and sends back the encoded result. A ZIO failure from your function is reported to Lambda as an invocation error, so the caller sees the failure instead of a silent timeout.

## Reading the Context

The `Context` describes the current invocation. It is a case class with these fields:

- `awsRequestId` is the ID of the current request, which is useful for logging.
- `remainingTimeInMillis` holds the deadline of the invocation as a Unix time in milliseconds, not a duration. See [Context](./reference/context.md).
- `logGroupName` and `logStreamName` name the CloudWatch Logs group and stream.
- `functionName`, `functionVersion` and `invokedFunctionArn` identify the function that runs.
- `memoryLimitInMB` is the memory size configured for the function.
- `clientContext` and `cognitoIdentity` are `Option` values that Lambda fills in only for invocations from the AWS Mobile SDK.

Your function receives the `Context` as its second argument. Use it to read these values, and do not construct one in production code, because the runtime creates it for each invocation.

## Working with Raw JSON

If you want to decode the JSON yourself, use `ZLambdaRunner.serveRaw` instead of `ZLambdaRunner.serve`. It passes the event as a `String` and expects a `String` in return, so no codecs are needed:

```scala
import zio._
import zio.lambda._

object RawHandler extends ZIOAppDefault {

  val echo = (json: String, _: Context) => ZIO.succeed(json)

  override val run = ZLambdaRunner.serveRaw(echo)
}
```

This form suits functions that only forward or log the payload. For typed events, prefer `ZLambdaRunner.serve`.

## Using Event Types from zio-lambda-event

The `zio-lambda-event` module provides types for common AWS event sources. Use them as the event type of your function, and the codecs are already in place. This function logs each message in an SQS batch and returns the number of messages, which the runner encodes as a JSON number:

```scala
import zio._
import zio.lambda._
import zio.lambda.event._

object QueueHandler extends ZIOAppDefault {

  val handle = (event: SQSEvent, _: Context) =>
    ZIO.foreachDiscard(event.records)(record => ZIO.logInfo(s"Message: ${record.body}")).as(event.records.size)

  override val run = ZLambdaRunner.serve(handle)
}
```

The [Event and Response Types](./event-and-response-types.md) page lists the types that the modules provide.
