# Response Types

> Reference for the eight response types in zio.lambda.response: fields, the events they pair with, and which ones are empty placeholders.

The `zio-lambda-response` module has eight response types in the `zio.lambda.response` package. Each is a `final case class` with an implicit `JsonEncoder` in its companion object, so [`ZLambdaRunner.serve`](./zlambda-runner.md) finds the encoder without an extra import. You return one of these values from your handler, and the runner encodes it to the JSON that the calling AWS service expects.

The response types do not depend on the event types. The library never ties an event to its response in the types, so pairing them is your job. This page names the event that each response is meant for, and says which responses have no fields yet.

## API Gateway and Load Balancer Responses

Four types answer HTTP-style integrations:

- `APIGatewayProxyResponse` pairs with `APIGatewayProxyRequestEvent`. Its fields are listed in [API Gateway events](./events-api-gateway.md).
- `APIGatewayV2HTTPResponse` pairs with `APIGatewayV2HttpEvent`. Its fields and defaults are listed in [API Gateway events](./events-api-gateway.md).
- `APIGatewayV2WebSocketResponse` pairs with `APIGatewayV2WebSocketEvent`. Its fields are listed in [API Gateway events](./events-api-gateway.md).
- `ApplicationLoadBalancerResponse` pairs with `ApplicationLoadBalancerRequestEvent`. Its fields are listed in [API Gateway events](./events-api-gateway.md).

`APIGatewayV2HTTPResponse` is the most convenient of the four, because a response with only a body is a one-line constructor call. The other three need every field spelled out. See [API Gateway events](./events-api-gateway.md) for the event types.

## Kinesis Analytics Responses

Two types answer Kinesis Data Analytics preprocessing and delivery Lambdas. Both return one result per input record, matched by `recordId`. `KinesisAnalyticsInputPreprocessingResponse` and `KinesisAnalyticsOutputDeliveryResponse` are described with their fields in [Messaging and Streaming Events](./events-messaging.md).

The input response pairs with the input preprocessing events, `KinesisAnalyticsFirehoseInputPreprocessingEvent` and `KinesisAnalyticsStreamsInputPreprocessingEvent`. The output response pairs with `KinesisAnalyticsOutputDeliveryEvent`. See [messaging events](./events-messaging.md) for those events.

## Placeholder Types

Two response types are empty placeholders: `S3BatchResponse` and `SimpleIAMPolicyResponse`. They are case classes with no fields, so the encoder always writes an empty JSON object. A handler that returns one of them compiles and runs, but it cannot tell the caller anything.

`S3BatchResponse` is meant for the S3 Batch Operations job, which sends `S3BatchEvent`. That event is an empty placeholder as well, as described in [Other AWS Events](./events-other.md). `SimpleIAMPolicyResponse` has no event paired with it in this repository. Its name points to an API Gateway authorizer result, but the type has no fields to carry one.

Until these types gain fields, define your own response case class with a `JsonEncoder` in its companion object, as shown in [Getting Started](../getting-started.md). The runner accepts any type that has an encoder.

## Choosing a Response

Pick the response by the service that invokes your function, not by what your code computes. If the event is an API Gateway or load balancer event, return the matching HTTP response. If the invoker is EventBridge, CloudWatch Logs, CloudFormation, CodeCommit or another service without a response type in this module, the module has nothing to offer, and you return your own type, a `String` or another type that has a `JsonEncoder`.
