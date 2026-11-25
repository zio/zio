# API Gateway and Load Balancer Events

> Reference for the API Gateway REST, HTTP, WebSocket and authorizer event types, the Application Load Balancer event, and their response types.

The `zio-lambda-event` module decodes the JSON that API Gateway and Application Load Balancer send to a Lambda function. The `zio-lambda-response` module encodes the JSON that these services expect back. This page lists each event type with its real fields and names the response type that goes with it. For the general model of event and response types, see [Event and Response Types](../event-and-response-types.md).

Event types live in the `zio.lambda.event` package and response types in the `zio.lambda.response` package. Every type is a `final case class` that derives its codec with zio-json. [`ZLambdaRunner.serve`](./zlambda-runner.md) needs a `JsonDecoder` for the event and a `JsonEncoder` for the response, and finds each one as an implicit.

Where a codec lives is not the same for every event. Most events keep it in the companion object of the event class, so no import is needed. Two events keep it in a separate object with a different name, and you must import that object's members. `APIGatewayProxyRequestEvent` has its decoder in `object APIGatewayProxyRequest`, so you need `import zio.lambda.event.APIGatewayProxyRequest._`. `APIGatewayV2CustomAuthorizerEvent` has its decoder in `object APIGatewayV2CustomAuthorizer`, so you need `import zio.lambda.event.APIGatewayV2CustomAuthorizer._`.

:::warning
Without the import, `ZLambdaRunner.serve` does not compile for `APIGatewayProxyRequestEvent`. The compiler reports `could not find implicit value for evidence parameter of type zio.json.JsonDecoder[zio.lambda.event.APIGatewayProxyRequestEvent]`.
:::

Fields written without `Option` are required: a case class field with no `Option` wrapper must be present in the JSON for decoding to succeed. The sections below say where a field is optional.

## REST API Proxy Integration

`APIGatewayProxyRequestEvent` is the event for an API Gateway REST API with the Lambda proxy integration. Its response type is `APIGatewayProxyResponse`.

### APIGatewayProxyRequestEvent

All fields are required. There are no `Option` fields.

- `resource: String`
- `path: String`
- `httpMethod: String`
- `headers: Map[String, String]`
- `multiValueHeaders: Map[String, List[String]]`
- `queryStringParameters: Map[String, String]`
- `multiValueQueryStringParameters: Map[String, List[String]]`
- `pathParameters: Map[String, String]`
- `stageVariables: Map[String, String]`
- `requestContext: APIGatewayProxyRequestRequestContext`
- `body: String`
- `isBase64Encoded: Boolean`

### APIGatewayProxyRequestRequestContext

All fields are required.

- `accountId: String`
- `stage: String`
- `resourceId: String`
- `requestId: String`
- `operationName: String`
- `identity: APIGatewayProxyRequestRequestContextIdentity`
- `resourcePath: String`
- `httpMethod: String`
- `apiId: String`
- `path: String`
- `authorizer: Map[String, String]`

### APIGatewayProxyRequestRequestContextIdentity

All fields are required and are `String`.

- `cognitoIdentityPoolId`
- `accountId`
- `cognitoIdentityId`
- `caller`
- `apiKey`
- `principalOrgId`
- `sourceIp`
- `cognitoAuthenticationType`
- `cognitoAuthenticationProvider`
- `userArn`
- `userAgent`
- `user`
- `accessKey`

### APIGatewayProxyResponse

All fields are required, and none has a default value, so you pass every one when you build a response.

- `statusCode: Int`
- `headers: Map[String, String]`
- `multiValueHeaders: Map[String, List[String]]`
- `body: String`
- `isBase64Encoded: Boolean`

### Example

The handler below reads the method and path of the request and answers with a plain-text body. It imports the `APIGatewayProxyRequest` members to bring the event decoder into scope:

```scala
import zio._
import zio.lambda._
import zio.lambda.event._
import zio.lambda.event.APIGatewayProxyRequest._
import zio.lambda.response._

object RestHandler extends ZIOAppDefault {

  val handle = (event: APIGatewayProxyRequestEvent, _: Context) =>
    ZIO.succeed(
      APIGatewayProxyResponse(
        statusCode = 200,
        headers = Map("Content-Type" -> "text/plain"),
        multiValueHeaders = Map.empty,
        body = s"${event.httpMethod} ${event.path}",
        isBase64Encoded = false
      )
    )

  override val run = ZLambdaRunner.serve(handle)
}
```

The response encoder comes from the companion object of `APIGatewayProxyResponse`, so the `zio.lambda.response._` import is enough.

## HTTP API Payload Format 2.0

`APIGatewayV2HttpEvent` is the event for an API Gateway HTTP API that uses payload format 2.0. Its response type is `APIGatewayV2HTTPResponse`.

### APIGatewayV2HttpEvent

- `version: String`
- `routeKey: String`
- `rawPath: String`
- `rawQueryString: String`
- `cookies: Option[List[String]]`
- `headers: Map[String, String]`
- `queryStringParameters: Option[Map[String, String]]`
- `pathParameters: Option[Map[String, String]]`
- `stageVariables: Option[Map[String, String]]`
- `body: String`
- `isBase64Encoded: Boolean`
- `requestContext: APIGatewayV2HTTPRequestContext`

The optional fields are `cookies`, `queryStringParameters`, `pathParameters` and `stageVariables`. The rest are required.

### APIGatewayV2HTTPRequestContext

- `routeKey: String`
- `accountId: String`
- `stage: String`
- `apiId: String`
- `domainName: String`
- `domainPrefix: String`
- `time: String`
- `timeEpoch: Long`
- `http: APIGatewayV2HTTPRequestContextHttp`
- `authorizer: Option[APIGatewayV2HTTPRequestContextAuthorizer]`
- `requestId: String`

Only `authorizer` is optional.

### APIGatewayV2HTTPRequestContextHttp

All fields are required and are `String`.

- `method`
- `path`
- `protocol`
- `sourceIp`
- `userAgent`

### APIGatewayV2HTTPRequestContextAuthorizer

When the `authorizer` field above is present, all three of its fields are required. That means a present authorizer object must carry `jwt`, `lambda` and `iam` together.

- `jwt: APIGatewayV2HTTPRequestContextAuthorizerJWT`
- `lambda: Map[String, String]`
- `iam: APIGatewayV2HTTPRequestContextAuthorizerIAM`

### APIGatewayV2HTTPRequestContextAuthorizerJWT

- `claims: Map[String, String]`
- `scopes: List[String]`

### APIGatewayV2HTTPRequestContextAuthorizerIAM

All fields are required.

- `accessKey: String`
- `accountId: String`
- `callerId: String`
- `cognitoIdentity: APIGatewayV2HTTPRequestContextAuthorizerIAMCognitoIdentity`
- `principalOrgId: String`
- `userArn: String`
- `userId: String`

### APIGatewayV2HTTPRequestContextAuthorizerIAMCognitoIdentity

- `amr: List[String]`
- `identityId: String`
- `identityPoolId: String`

### APIGatewayV2HTTPResponse

`body` is the only field without a default value. The other fields default as shown, so a minimal response is a body alone.

- `body: String`
- `statusCode: Int = 200`
- `headers: Map[String, String] = Map()`
- `multiValueHeaders: Map[String, List[String]] = Map()`
- `cookies: List[String] = List()`
- `isBase64Encoded: Boolean = false`

### Example

The handler below uses the optional query parameters and the `APIGatewayV2HTTPRequestContext#http` field. Because `queryStringParameters` is an `Option`, the code unwraps it before it looks up a key. The response sets only `body` and `statusCode`, and the defaults fill in the rest:

```scala
import zio._
import zio.lambda._
import zio.lambda.event._
import zio.lambda.response._

object HttpApiHandler extends ZIOAppDefault {

  val handle = (event: APIGatewayV2HttpEvent, _: Context) => {
    val name = event.queryStringParameters.flatMap(_.get("name")).getOrElse("world")

    ZIO.succeed(
      APIGatewayV2HTTPResponse(
        body = s"Hello, $name! You called ${event.requestContext.http.method} ${event.rawPath}",
        statusCode = 200
      )
    )
  }

  override val run = ZLambdaRunner.serve(handle)
}
```

## WebSocket API

`APIGatewayV2WebSocketEvent` is the event for an API Gateway WebSocket API. Its response type is `APIGatewayV2WebSocketResponse`. The source file is `APIGatewayV2WebSocket.scala`.

### APIGatewayV2WebSocketEvent

All fields are required. There are no `Option` fields. The field list is the same as the one in `APIGatewayProxyRequestEvent`, except that `requestContext` has the WebSocket type.

- `resource: String`
- `path: String`
- `httpMethod: String`
- `headers: Map[String, String]`
- `multiValueHeaders: Map[String, List[String]]`
- `queryStringParameters: Map[String, String]`
- `multiValueQueryStringParameters: Map[String, List[String]]`
- `pathParameters: Map[String, String]`
- `stageVariables: Map[String, String]`
- `requestContext: APIGatewayV2WebSocketRequestContext`
- `body: String`
- `isBase64Encoded: Boolean`

### APIGatewayV2WebSocketRequestContext

All fields are required.

- `accountId: String`
- `resourceId: String`
- `stage: String`
- `requestId: String`
- `identity: APIGatewayV2WebSocketRequestContextIdentity`
- `resourcePath: String`
- `authorizer: Map[String, String]`
- `httpMethod: String`
- `apiId: String`
- `connectedAt: Long`
- `connectionId: String`
- `domainName: String`
- `error: String`
- `eventType: String`
- `extendedRequestId: String`
- `integrationLatency: String`
- `messageDirection: String`
- `messageId: String`
- `requestTime: String`
- `requestTimeEpoch: Long`
- `routeKey: String`
- `status: String`

### APIGatewayV2WebSocketRequestContextIdentity

All fields are required and are `String`. It has the same fields as `APIGatewayProxyRequestRequestContextIdentity`, without `principalOrgId`.

- `cognitoIdentityPoolId`
- `accountId`
- `cognitoIdentityId`
- `caller`
- `apiKey`
- `sourceIp`
- `cognitoAuthenticationType`
- `cognitoAuthenticationProvider`
- `userArn`
- `userAgent`
- `user`
- `accessKey`

### APIGatewayV2WebSocketResponse

All fields are required, and none has a default value. `isBase64Encoded` comes first here, unlike in the other response types.

- `isBase64Encoded: Boolean`
- `statusCode: Int`
- `headers: Map[String, String]`
- `multiValueHeaders: Map[String, List[String]]`
- `body: String`

## Custom Authorizer Events

Two event types carry the request that API Gateway sends to a Lambda authorizer: one for REST APIs and one for HTTP APIs. Neither has a matching response type that you can fill in. The response module's only authorizer-related type is `SimpleIAMPolicyResponse`, and it is an empty placeholder, as described under [Placeholder Types](#placeholder-types).

### APIGatewayCustomAuthorizerEvent

`APIGatewayCustomAuthorizerEvent` is the authorizer event for a REST API. All fields are required. Its codec is in the companion object.

- `version: String`
- `type: String`, written with backticks in the source because `type` is a Scala keyword
- `methodArn: String`
- `identitySource: String`
- `authorizationToken: String`
- `resource: String`
- `path: String`
- `httpMethod: String`
- `headers: Map[String, String]`
- `queryStringParameters: Map[String, String]`
- `pathParameters: Map[String, String]`
- `stageVariables: Map[String, String]`
- `requestContext: APIGatewayCustomAuthorizerRequestContext`

`APIGatewayCustomAuthorizerRequestContext` has these required fields:

- `path: String`
- `accountId: String`
- `resourceId: String`
- `stage: String`
- `requestId: String`
- `identity: APIGatewayCustomAuthorizerRequestContextIdentity`
- `resourcePath: String`
- `httpMethod: String`
- `apiId: String`

`APIGatewayCustomAuthorizerRequestContextIdentity` has two required `String` fields: `apiKey` and `sourceIp`.

### APIGatewayV2CustomAuthorizerEvent

`APIGatewayV2CustomAuthorizerEvent` is the authorizer event for an HTTP API. All fields are required. Its decoder is in `object APIGatewayV2CustomAuthorizer`, not in the companion of the event class, so import `zio.lambda.event.APIGatewayV2CustomAuthorizer._` to use it with `ZLambdaRunner.serve`.

- `version: String`
- `type: String`, written with backticks in the source
- `routeArn: String`
- `identitySource: List[String]`, a list here, where the REST event has a single `String`
- `routeKey: String`
- `rawPath: String`
- `rawQueryString: String`
- `cookies: List[String]`
- `headers: Map[String, String]`
- `queryStringParameters: Map[String, String]`
- `requestContext: APIGatewayV2CustomAuthorizerRequestContext`
- `pathParameters: Map[String, String]`
- `stageVariables: Map[String, String]`

`APIGatewayV2CustomAuthorizerRequestContext` has these required fields:

- `accountId: String`
- `apiId: String`
- `domainName: String`
- `domainPrefix: String`
- `http: Http`
- `requestId: String`
- `routeKey: String`
- `stage: String`
- `time: java.time.OffsetDateTime`
- `timeEpoch: Long`

Its companion object defines an implicit `JsonDecoder[OffsetDateTime]` that parses the `time` string with the pattern `dd/MMM/yyyy:HH:mm:ss Z`. A timestamp in another format fails to decode.

The `APIGatewayV2CustomAuthorizerRequestContext#http` field has the type `Http`. This is a top-level case class in the `zio.lambda.event` package, and it is not prefixed with the API Gateway name. Its fields are all required `String` values: `method`, `path`, `protocol`, `sourceIp` and `userAgent`. It is a separate type from `APIGatewayV2HTTPRequestContextHttp`, which has the same fields.

## Application Load Balancer

`ApplicationLoadBalancerRequestEvent` is the event for a Lambda function registered as a target of an Application Load Balancer. Its response type is `ApplicationLoadBalancerResponse`.

### ApplicationLoadBalancerRequestEvent

All fields are required. There are no `Option` fields.

- `requestContext: ApplicationLoadBalancerRequestContext`
- `httpMethod: String`
- `path: String`
- `queryStringParameters: Map[String, String]`
- `multiValueQueryStringParameters: Map[String, List[String]]`
- `headers: Map[String, String]`
- `multiValueHeaders: Map[String, List[String]]`
- `body: String`
- `isBase64Encoded: Boolean`

`ApplicationLoadBalancerRequestContext` has one required field, `elb: ApplicationLoadBalancerRequestContextElb`. `ApplicationLoadBalancerRequestContextElb` has one required field, `targetGroupArn: String`.

### ApplicationLoadBalancerResponse

All fields are required, and none has a default value.

- `statusCode: Int`
- `statusDescription: String`
- `isBase64Encoded: Boolean`
- `headers: Map[String, String]`
- `multiValueHeaders: Map[String, List[String]]`
- `body: String`

## Placeholder Types

`SimpleIAMPolicyResponse` is declared as `final case class SimpleIAMPolicyResponse()`. It has no fields, so it encodes to an empty JSON object and cannot carry an authorizer policy. Do not return it from an authorizer function and expect API Gateway to accept it.

None of the event types on this page is a placeholder: each one declares the fields listed above, and so does each response type except `SimpleIAMPolicyResponse`.

## Choosing a Pair

The following list gives the response type to return for each event.

- `APIGatewayProxyRequestEvent` pairs with `APIGatewayProxyResponse`.
- `APIGatewayV2HttpEvent` pairs with `APIGatewayV2HTTPResponse`.
- `APIGatewayV2WebSocketEvent` pairs with `APIGatewayV2WebSocketResponse`.
- `ApplicationLoadBalancerRequestEvent` pairs with `ApplicationLoadBalancerResponse`.
- `APIGatewayCustomAuthorizerEvent` and `APIGatewayV2CustomAuthorizerEvent` have no usable response type in the module. Write your own case class and `JsonEncoder` for the policy document, as shown in [Getting Started](../getting-started.md).
