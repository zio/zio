# Tutorial: Build an HTTP API on API Gateway

> In this tutorial you build a small HTTP API as an AWS Lambda function. The API creates greetings: a `GET` request returns a default greeting, and a `POST` request with a JSON body returns a greeting for the name in the body. You start with a handler that answers every request, then add routing, then JSON body handling and error responses. At the end you test the handler on your machine.

You need the `zio-lambda`, `zio-lambda-event` and `zio-lambda-response` modules from the [installation instructions](../index.md#installation). The handler uses an API Gateway HTTP API with payload format 2.0, so the event type is `APIGatewayV2HttpEvent` and the response type is `APIGatewayV2HTTPResponse`.

## Step 1: Answer Every Request

An API Gateway HTTP API sends your function an `APIGatewayV2HttpEvent` and expects an `APIGatewayV2HTTPResponse` back. The handler below ignores the request and always returns the same greeting:

```scala
import zio._
import zio.lambda._
import zio.lambda.event._
import zio.lambda.response._

object GreetingApi extends ZIOAppDefault {

  val handler = (_: APIGatewayV2HttpEvent, _: Context) =>
    ZIO.succeed(APIGatewayV2HTTPResponse(body = "Hello, world!"))

  override val run = ZLambdaRunner.serve(handler)
}
```

`APIGatewayV2HTTPResponse` has defaults for everything except the body: `statusCode` is 200, and the header maps and cookie list are empty.

## Step 2: Route by Method and Path

The event carries the HTTP method in `requestContext.http.method` and the path in `rawPath`. Match on both to decide what to return, and answer anything else with a 404:

```scala
import zio._
import zio.lambda._
import zio.lambda.event._
import zio.lambda.response._

object GreetingApi extends ZIOAppDefault {

  def route(event: APIGatewayV2HttpEvent): APIGatewayV2HTTPResponse =
    (event.requestContext.http.method, event.rawPath) match {
      case ("GET", "/greeting") => APIGatewayV2HTTPResponse(body = "Hello, world!")
      case _                    => APIGatewayV2HTTPResponse(body = "Not found", statusCode = 404)
    }

  val handler = (event: APIGatewayV2HttpEvent, _: Context) => ZIO.succeed(route(event))

  override val run = ZLambdaRunner.serve(handler)
}
```

Keeping `route` as a plain function makes it easy to test, which you do in step 4.

## Step 3: Read a JSON Body and Report Errors

For a `POST`, the JSON text arrives in the `body` field of the event. Decode it with zio-json, and return a 400 response when it is not valid. Define the request type with its decoder in the companion object:

```scala
import zio._
import zio.json._
import zio.lambda._
import zio.lambda.event._
import zio.lambda.response._

final case class GreetingRequest(name: String)

object GreetingRequest {
  implicit val decoder: JsonDecoder[GreetingRequest] = DeriveJsonDecoder.gen[GreetingRequest]
}

def route(event: APIGatewayV2HttpEvent): APIGatewayV2HTTPResponse =
  (event.requestContext.http.method, event.rawPath) match {
    case ("GET", "/greeting") =>
      APIGatewayV2HTTPResponse(body = "Hello, world!")

    case ("POST", "/greeting") =>
      event.body.fromJson[GreetingRequest] match {
        case Right(request) => APIGatewayV2HTTPResponse(body = s"Hello, ${request.name}!")
        case Left(error)    => APIGatewayV2HTTPResponse(body = s"Invalid body: $error", statusCode = 400)
      }

    case _ =>
      APIGatewayV2HTTPResponse(body = "Not found", statusCode = 404)
  }
```

Because `route` returns a response for every case, the handler's effect cannot fail:

```scala
object GreetingApi extends ZIOAppDefault {

  val handler = (event: APIGatewayV2HttpEvent, _: Context) => ZIO.succeed(route(event))

  override val run = ZLambdaRunner.serve(handler)
}
```

A failure in your effect that you do not handle, for example a `ZIO.fail` with a `Throwable`, is different: the runner reports it to Lambda as an invocation error. A defect, such as an exception thrown inside `ZIO.succeed`, is not caught by the runner and stops it.

## Step 4: Test It on Your Machine

Because `route` is a plain function, you can test it without Lambda. Build an `APIGatewayV2HttpEvent` with the fields your routing reads, call `route`, and check the response:

```scala
val postEvent = APIGatewayV2HttpEvent(
  version = "2.0",
  routeKey = "POST /greeting",
  rawPath = "/greeting",
  rawQueryString = "",
  cookies = None,
  headers = Map.empty,
  queryStringParameters = None,
  pathParameters = None,
  stageVariables = None,
  body = """{"name":"Ada"}""",
  isBase64Encoded = false,
  requestContext = APIGatewayV2HTTPRequestContext(
    routeKey = "POST /greeting",
    accountId = "000000000000",
    stage = "$default",
    apiId = "api-id",
    domainName = "example.com",
    domainPrefix = "api-id",
    time = "",
    timeEpoch = 0L,
    http = APIGatewayV2HTTPRequestContextHttp(
      method = "POST",
      path = "/greeting",
      protocol = "HTTP/1.1",
      sourceIp = "127.0.0.1",
      userAgent = "test"
    ),
    authorizer = None,
    requestId = "request-id"
  )
)
// postEvent: APIGatewayV2HttpEvent = APIGatewayV2HttpEvent(
//   version = "2.0",
//   routeKey = "POST /greeting",
//   rawPath = "/greeting",
//   rawQueryString = "",
//   cookies = None,
//   headers = Map(),
//   queryStringParameters = None,
//   pathParameters = None,
//   stageVariables = None,
//   body = "{\"name\":\"Ada\"}",
//   isBase64Encoded = false,
//   requestContext = APIGatewayV2HTTPRequestContext(
//     routeKey = "POST /greeting",
//     accountId = "000000000000",
//     stage = "$default",
//     apiId = "api-id",
//     domainName = "example.com",
//     domainPrefix = "api-id",
//     time = "",
//     timeEpoch = 0L,
//     http = APIGatewayV2HTTPRequestContextHttp(
//       method = "POST",
//       path = "/greeting",
//       protocol = "HTTP/1.1",
//       sourceIp = "127.0.0.1",
//       userAgent = "test"
//     ),
//     authorizer = None,
//     requestId = "request-id"
//   )
// )

route(postEvent)
// res4: APIGatewayV2HTTPResponse = APIGatewayV2HTTPResponse(
//   body = "Hello, Ada!",
//   statusCode = 200,
//   headers = Map(),
//   multiValueHeaders = Map(),
//   cookies = List(),
//   isBase64Encoded = false
// )
```

The response has status code 200 and the body `Hello, Ada!`. Change `body` to invalid JSON to see the 400 response, or change `method` and `rawPath` to see the 404. To run an effect from a test suite, see [How to Test a Handler Locally](../testing-locally.md).

## Next Steps

- To package the handler and deploy it, follow [Deploy to AWS Lambda](../deployment.md).
- For every field of the event and response types, see [API Gateway Events](../reference/events-api-gateway.md).
