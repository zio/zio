# Event and Response Types

> The `zio-lambda-event` and `zio-lambda-response` modules provide Scala case classes that match the JSON that AWS services send to Lambda and expect back from it. Use them as the event and response types of your handler, so you do not have to write those case classes yourself.

Most types come with their codec in their companion object, so `ZLambdaRunner.serve` finds it without an extra import. The exceptions are `DynamoDBEvent`, which has no codec, and five types whose codec sits in a differently named object, so you must import that object: `APIGatewayProxyRequestEvent` (`APIGatewayProxyRequest`), `APIGatewayV2CustomAuthorizerEvent` (`APIGatewayV2CustomAuthorizer`), `ConfigEvent` (`Config`), `CognitoUserPoolCreateAuthChallengeEvent` (`CognitoUserPoolCreateAuthChallenge`) and `CognitoUserPoolPreAuthenticationEvent` (`CognitoUserPoolPreAuthentication`). The tables below list the most common types. The reference pages describe every type and its fields:

- [API Gateway Events](./reference/events-api-gateway.md): API Gateway REST, HTTP and WebSocket APIs, custom authorizers and Application Load Balancer.
- [Messaging Events](./reference/events-messaging.md): SQS, SNS, Kafka, ActiveMQ, Kinesis, Firehose, Kinesis Analytics and DynamoDB.
- [Cognito Events](./reference/events-cognito.md): Cognito Sync and the user pool triggers.
- [Other Events](./reference/events-other.md): S3, S3 Batch, scheduled events, CloudWatch Logs, CloudFormation, CloudFront, CodeCommit, Config, Connect, IoT Button, Lex, Lambda destinations and Secrets Manager rotation.
- [Response Types](./reference/responses.md): all eight response types.

## Event Types

Event types live in the `zio.lambda.event` package. Each type decodes the JSON that the named AWS service sends:

| Type                                       | AWS source                                              |
| ------------------------------------------ | ------------------------------------------------------- |
| `APIGatewayProxyRequestEvent`              | API Gateway REST API with Lambda proxy integration      |
| `APIGatewayV2HttpEvent`                    | API Gateway HTTP API, payload format 2.0                |
| `APIGatewayV2WebSocketEvent`               | API Gateway WebSocket API                               |
| `ApplicationLoadBalancerRequestEvent`      | Application Load Balancer target group                  |
| `SQSEvent`                                 | Amazon SQS, a batch of messages                         |
| `KinesisEvent`                             | Amazon Kinesis Data Streams, a batch of records         |
| `KafkaEvent`                               | Amazon MSK or self-managed Apache Kafka                 |
| `ActiveMQEvent`                            | Amazon MQ for ActiveMQ                                  |
| `ScheduledEvent`                           | EventBridge scheduled rule                              |
| `CloudWatchLogsEvent`                      | CloudWatch Logs subscription filter                     |
| `CloudFormationCustomResourceEvent`        | CloudFormation custom resource                          |
| `ConnectEvent`                             | Amazon Connect contact flow                             |
| `CodeCommitEvent`                          | AWS CodeCommit repository trigger                       |
| `CloudFrontEvent`                          | CloudFront event for Lambda@Edge                        |

`APIGatewayProxyRequestEvent` is the exception to the companion rule: its codec is in the `APIGatewayProxyRequest` object in the same file, so import that object to use it with `ZLambdaRunner.serve`:

```scala
import zio.lambda.event.APIGatewayProxyRequest._
```

## Response Types

Response types live in the `zio.lambda.response` package. Each type encodes the JSON that the named AWS integration expects:

| Type                                                   | AWS integration                                         |
| ------------------------------------------------------ | ------------------------------------------------------- |
| `APIGatewayProxyResponse`                              | API Gateway REST API with Lambda proxy integration      |
| `APIGatewayV2HTTPResponse`                             | API Gateway HTTP API, payload format 2.0                |
| `APIGatewayV2WebSocketResponse`                        | API Gateway WebSocket API                               |
| `ApplicationLoadBalancerResponse`                      | Application Load Balancer target group                  |
| `KinesisAnalyticsInputPreprocessingResponse`           | Kinesis Analytics input preprocessing Lambda            |
| `KinesisAnalyticsOutputDeliveryResponse`               | Kinesis Analytics output delivery Lambda                |

To use a response type, return it from your function. The example below pairs an HTTP API event with an HTTP API response:

```scala
import zio._
import zio.lambda._
import zio.lambda.event._
import zio.lambda.response._

object HttpHandler extends ZIOAppDefault {

  val hello = (event: APIGatewayV2HttpEvent, _: Context) =>
    ZIO.succeed(APIGatewayV2HTTPResponse(body = s"You called ${event.rawPath}", statusCode = 200))

  override val run = ZLambdaRunner.serve(hello)
}
```

## Placeholder Types

Some types in the two modules are empty case classes with no fields. A placeholder event decodes any payload and discards its contents, so it carries no data. Placeholder events include `S3Event`, `SNSEvent`, `S3BatchEvent`, `LexEvent`, `LambdaDestinationEvent`, `SecretsManagerRotationEvent` and most of the Cognito user pool trigger events. The placeholder responses are `S3BatchResponse` and `SimpleIAMPolicyResponse`. The reference pages mark every placeholder.

`DynamoDBEvent` is different: it has no decoder at all, so it does not compile with `ZLambdaRunner.serve`. Do not rely on a placeholder to read data from an event. Define your own case class for the fields you need, as described in [How to Define Your Own Event Type](./how-to/decode-only-the-fields-you-need.md).

## Custom Types

If the AWS event you receive is not in the modules, or you need only some of its fields, write your own case class and codec, as shown in [How to Define Your Own Event Type](./how-to/decode-only-the-fields-you-need.md). zio-json ignores JSON fields that your case class does not declare, so you can decode a subset of a larger event.
