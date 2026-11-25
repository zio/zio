# Other AWS Events

> Reference for the S3, EventBridge, CloudWatch Logs, CloudFormation, CloudFront, CodeCommit, Config, Connect and IoT event types.

This page documents the event types in `zio.lambda.event` that are not API Gateway, load balancer, messaging or Cognito events. They decode the JSON that services such as EventBridge, CloudWatch Logs, CloudFormation, CloudFront, CodeCommit, AWS Config, Amazon Connect and AWS IoT send to a Lambda function.

Every type is a `final case class` whose companion object holds an implicit `JsonDecoder`, so [`ZLambdaRunner.serve`](./zlambda-runner.md) finds the decoder without an extra import. The one exception, `ConfigEvent`, is described below. Field names follow the Scala source, and a field that is not wrapped in `Option` is required: the type declares no default for it. None of the types on this page has an `Option` field.

For the API Gateway types see [API Gateway events](./events-api-gateway.md), and for the queue and stream types see [messaging events](./events-messaging.md).

## Placeholder Types

Five event types are empty placeholders. They are case classes with no fields, and their decoder accepts a JSON object and discards its contents:

- `S3Event`
- `S3BatchEvent`
- `LexEvent`
- `LambdaDestinationEvent`
- `SecretsManagerRotationEvent`

A handler that takes one of these types runs, but it cannot read anything from the invocation. Define your own case class and `JsonDecoder` with the fields you need until the library fills the type in, as shown in [Getting Started](../getting-started.md). zio-json ignores JSON fields that your case class does not declare, so a small class that lists only the fields you read is enough.

The response side has the same gap: `S3BatchResponse` and `SimpleIAMPolicyResponse` in `zio.lambda.response` are empty placeholders too. See [Response Types](./responses.md).

## Types With Fields

The eight types below have fields, and each section lists them with their types.

### ScheduledEvent

`ScheduledEvent` is the envelope that EventBridge sends for a scheduled rule. All eight fields are required:

- `id: String`
- `account: String`
- `region: String`
- `detail: Map[String, String]`
- `source: String`
- `resources: List[String]`
- `time: java.time.ZonedDateTime`
- `detailType: String`, which decodes from the JSON key `detail-type` through `@jsonField("detail-type")`

The example below reads the `detailType` and `time` fields to build a response string:

```scala
import zio._
import zio.lambda._
import zio.lambda.event._

object ScheduledHandler extends ZIOAppDefault {

  val tick = (event: ScheduledEvent, _: Context) =>
    ZIO.succeed(s"${event.detailType} fired at ${event.time}")

  override val run = ZLambdaRunner.serve(tick)
}
```

The handler returns a `String`, which `ZLambdaRunner.serve` encodes with zio-json's built-in string encoder. A scheduled invocation has no caller waiting for the result, so a real handler often does its work and returns a small constant.

### CloudWatchLogsEvent

`CloudWatchLogsEvent` is the payload of a CloudWatch Logs subscription filter. It has one required field, `awsLogs: CloudWatchLogsAWSLogs`, and `CloudWatchLogsAWSLogs` has one required field, `data: String`. The type does not unpack the log data for you: `data` is the string exactly as it arrives, so decoding and decompressing it is up to your handler.

### CloudFormationCustomResourceEvent

`CloudFormationCustomResourceEvent` is the request CloudFormation sends to a custom resource. It is a flat case class with ten required fields:

- `requestType: String`, `serviceToken: String` and `responseUrl: String`
- `stackId: String`, `requestId: String` and `logicalResourceId: String`
- `physicalResourceId: String` and `resourceType: String`
- `resourceProperties: Map[String, String]` and `oldResourceProperties: Map[String, String]`

`physicalResourceId` and `oldResourceProperties` are declared as required, although AWS does not send every field with every request type. The type offers no `Option` for them.

### CloudFrontEvent

`CloudFrontEvent` is the event for a CloudFront trigger on Lambda@Edge. It wraps `records: List[CloudFrontRecord]`. The nested types, from the outside in, are:

- `CloudFrontRecord` has one field, `cf: CloudFrontRecordCF`.
- `CloudFrontRecordCF` has `config: CloudFrontRecordCFConfig`, `request: CloudFrontRecordCFRequest` and `response: CloudFrontRecordCFResponse`.
- `CloudFrontRecordCFConfig` has `distributionId: String`.
- `CloudFrontRecordCFRequest` has `uri`, `method`, `httpVersion` and `clientIp`, all `String`, and `headers: Map[String, List[CloudFrontRecordCFHttpHeader]]`.
- `CloudFrontRecordCFResponse` has `status`, `statusDescription` and `httpVersion`, all `String`, and the same kind of `headers` map.
- `CloudFrontRecordCFHttpHeader` has `key: String` and `value: String`.

Both `request` and `response` are required, so the decoder expects every record to carry both parts.

### CodeCommitEvent

`CodeCommitEvent` is the trigger payload from an AWS CodeCommit repository. It holds `records: Seq[CodeCommitRecord]`. Each `CodeCommitRecord` has fourteen required fields:

- `eventId`, `eventVersion`, `eventTriggerName`, `eventName`, `eventTriggerConfigId`, `eventSourceArn`, `userIdentityArn`, `eventSource`, `awsRegion` and `customData`, all `String`
- `eventTime: java.time.Instant`
- `eventPartNumber: Int` and `eventTotalParts: Int`
- `codeCommit: CodeCommitRecordCommit`

`CodeCommitRecordCommit` has `references: List[CodeCommitRecordCommitReference]`. Each reference has `commit: String`, `ref: String` and `created: Boolean`.

### ConfigEvent

`ConfigEvent` is the event that AWS Config sends to a custom rule. It has ten required fields: `version`, `invokingEvent`, `ruleParameters`, `resultToken`, `configRuleArn`, `configRuleId`, `configRuleName`, `accountId` and `executionRoleArn`, all `String`, and `eventLeftScope: Boolean`. `invokingEvent` and `ruleParameters` stay as JSON text in a `String`, so parse them yourself.

:::warning[The Config decoder is not in the companion]
In the source, the implicit `JsonDecoder[ConfigEvent]` sits in an object named `Config`, not in `ConfigEvent`. The compiler therefore does not find it through implicit scope the way it does for the other types. Import `zio.lambda.event.Config._` where you call `ZLambdaRunner.serve`, or define your own decoder.
:::

### ConnectEvent

`ConnectEvent` is the event for an Amazon Connect contact flow. It has two required fields: `details: ConnectDetails` and `name: String`. The nested types are:

- `ConnectDetails` has `contactData: ConnectContactData` and `parameters: Map[String, String]`.
- `ConnectContactData` has `attributes: Map[String, String]`, `customerEndpoint: Endpoint`, `systemEndpoint: Endpoint`, and the `String` fields `channel`, `contactId`, `initialContactId`, `initiationMethod`, `instanceArn`, `previousContactId` and `queue`.
- `Endpoint` has `address: String` and `type: String`. The Scala field is written with backticks, as `` `type` ``, because `type` is a keyword.

### IoTButtonEvent

`IoTButtonEvent` is the event from an AWS IoT button. It has three required fields, all `String`: `serialNumber`, `clickType` and `batteryVoltage`. The voltage arrives as a string, so convert it yourself if you need a number.
