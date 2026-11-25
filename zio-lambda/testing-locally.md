# How to Test a Handler Locally

> A handler is a plain function, so you can test it on your machine without deploying to AWS. Call the function with an event, a `Context` that you construct yourself, and run the resulting ZIO. This checks your logic and your codecs, which covers most of what a handler does.

ZIO Lambda has no local emulator of the Lambda runtime. The loop that talks to the Runtime API only runs inside Lambda, so a local test does not check the invocation loop, the environment variables that Lambda sets, or the real `Context` that AWS builds. Test those by deploying to a development function, as described in [Deployment](./deployment.md).

## Defining the Handler

The handler under test is the same function you pass to `ZLambdaRunner.serve`. Define it as a top-level `val` so that your tests can call it. The example below uses a simple greeting:

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

val greet = (event: GreetingEvent, context: Context) =>
  ZIO.succeed(GreetingResponse(s"Hello, ${event.name}! Request ${context.awsRequestId}"))
// greet: (GreetingEvent, Context) => ZIO[Any, Nothing, GreetingResponse] = <function2>
```

## Building a Context

The `Context` case class has a public constructor, so a test can build one with fixed values. Named arguments make each value easy to find:

```scala
val testContext = Context(
  awsRequestId = "test-request-id",
  logGroupName = "",
  logStreamName = "",
  functionName = "greeting-function",
  functionVersion = "1",
  invokedFunctionArn = "arn:aws:lambda:us-east-1:000000000000:function:greeting-function",
  remainingTimeInMillis = 30000L,
  memoryLimitInMB = 128,
  clientContext = None,
  cognitoIdentity = None
)
```

Set `clientContext` and `cognitoIdentity` to `Some(...)` only when the handler reads them.

## Running the Handler

To run the effect from a plain `main` or a script, use `Runtime.default.unsafe.run`. The call below runs the greeting handler with an event that the test decodes from JSON, which matches what Lambda sends:

```scala
val event = """{"name":"Ada"}""".fromJson[GreetingEvent].getOrElse(throw new IllegalArgumentException("bad event"))
// event: GreetingEvent = GreetingEvent(name = "Ada")

val response = Unsafe.unsafe { implicit unsafe =>
  Runtime.default.unsafe.run(greet(event, testContext)).getOrThrowFiberFailure()
}
// response: GreetingResponse = GreetingResponse(
//   message = "Hello, Ada! Request test-request-id"
// )

println(response.toJson)
// {"message":"Hello, Ada! Request test-request-id"}
```

The printed JSON is the same text that the runner would send back to Lambda. Compare it with the value you expect to check the wire format.

## Testing in a Test Suite

In a project with a test suite, add `zio-test` to your test dependencies and write the call inside a `test` block. The handler does not depend on any AWS service, so the test needs no mocks, only the `Context` and the event. See the [ZIO Test documentation](https://zio.dev/reference/test/) for the test DSL.

Test each case that matters for your function: a normal event, an event that fails to decode, and a failure your function returns on purpose. To check a decode failure, call `fromJson` on the bad JSON and assert that it returns `Left`. The runner turns that same `Left` into an invocation error when Lambda sends the event.

## Testing Raw Handlers

A handler that uses `ZLambdaRunner.serveRaw` takes and returns a `String`, so you test it the same way, without codecs. Pass the raw JSON string and check the returned string.
