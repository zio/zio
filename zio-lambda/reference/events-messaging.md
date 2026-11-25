# Messaging and Streaming Events

> Reference for the SQS, SNS, Kafka, ActiveMQ, Kinesis, Firehose and DynamoDB event types and the Kinesis Analytics response types.

The `zio-lambda-event` module models the events that messaging and streaming services send to a Lambda function: queues (SQS, ActiveMQ), topics (SNS), streams (Kinesis, Kafka, DynamoDB Streams) and the Kinesis Analytics hooks. This page lists each type with its fields. The event types live in the `zio.lambda.event` package, and the Kinesis Analytics response types live in `zio.lambda.response`.

Every type that has fields comes with a `JsonDecoder` in its companion object, so [`ZLambdaRunner.serve`](./zlambda-runner.md) finds it without an extra import. For how to write a handler, see [Getting Started](../getting-started.md). For the full list of event and response types, see [Event and Response Types](../event-and-response-types.md).

## Placeholder Types

Four types in this group are not usable for reading data yet. `SNSEvent`, `KinesisFirehoseEvent` and `KinesisAnalyticsStreamsInputPreprocessingEvent` are empty placeholders. Each is a case class with no fields, and each has a derived decoder. The decoder succeeds on a JSON object of any shape and discards its contents, because zio-json ignores JSON fields that the case class does not declare. Your handler receives the value `SNSEvent()`, with none of the message data, so these types only let a function compile and run. `DynamoDBEvent` is also an empty case class, but it has no decoder at all. It cannot be passed to `ZLambdaRunner.serve`. See [DynamoDBEvent](#dynamodbevent) for a workaround.

For any of these four sources, define your own case class for the fields you need.

## SQSEvent

`SQSEvent` is a batch of messages from an Amazon SQS queue. Its one field, `records: List[SQSRecord]`, is read from the JSON field `Records`, with a capital `R`.

Each `SQSRecord` has these fields:

- `messageId`, `receiptHandle` and `body` are required strings. `body` holds the message text as the sender wrote it.
- `md5OfBody` is a required string.
- `md5OfMessageAttributes` is an `Option[String]`, because SQS omits it when a message has no attributes.
- `eventSourceARN`, `eventSource` and `awsRegion` are required strings that identify the queue and its region.
- `attributes` is a `Map[String, String]` of system attributes, such as the sent timestamp.
- `messageAttributes` is a `Map[String, SQSMessageAttribute]` of the custom attributes. The field is required, so the JSON must contain it, even if the object is empty.

Each `SQSMessageAttribute` has a `stringValue: String` and a `dataType: SQSMessageAttributeDataType`. The data type is a sealed trait with three case objects: `SQSMessageAttributeDataType.String`, `SQSMessageAttributeDataType.Number` and `SQSMessageAttributeDataType.Binary`. The decoder reads the JSON string without regard to case, so `"String"` and `"STRING"` both work. Any other value, including a custom label such as `String.custom`, fails decoding with the message `Unknown SQSMessageAttributeDataType`.

The handler below keeps only the messages that carry an attribute named `priority` with the value `high`, and returns their bodies:

```scala
import zio._
import zio.lambda._
import zio.lambda.event._

object PriorityHandler extends ZIOAppDefault {

  val handle = (event: SQSEvent, _: Context) =>
    ZIO.succeed(
      event.records
        .filter(_.messageAttributes.get("priority").exists(_.stringValue == "high"))
        .map(_.body)
    )

  override val run = ZLambdaRunner.serve(handle)
}
```

The function returns a `List[String]`, which the runner encodes as a JSON array. It reads `messageAttributes` as an ordinary map, so a record without the `priority` attribute is skipped instead of failing.

## SNSEvent

`SNSEvent` is an empty placeholder. It has no fields and decodes any payload to an empty value. See [Placeholder Types](#placeholder-types).

## KafkaEvent

`KafkaEvent` is a batch of records from Amazon MSK or a self-managed Apache Kafka cluster. It has these fields, all required:

- `records: Map[String, List[KafkaRecord]]` groups the records by their topic and partition key, which is the map key.
- `eventSource: String` and `eventSourceArn: String` identify the event source.
- `bootstrapServers: String` is the broker list that Lambda reports.

Each `KafkaRecord` has these fields, all required:

- `topic: String`, `partition: Int` and `offset: Long` locate the record in the log.
- `timestamp: java.time.Instant` is decoded from a JSON number of epoch milliseconds, through a decoder that `KafkaRecord` defines for `Instant`.
- `timestampType: String` says whether the timestamp is the create time or the log-append time.
- `key: String` and `value: String` are the raw strings from the JSON. The type does not decode them for you.

## ActiveMQEvent

`ActiveMQEvent` is a batch of messages from an Amazon MQ for ActiveMQ broker. It has three required fields: `eventSource: String` and `eventSourceArn: String` identify the broker, and `messages: List[ActiveMQMessage]` is the batch.

Each `ActiveMQMessage` has these fields, all required:

- `messageID`, `messageType`, `correlationID` and `replyTo` are strings.
- `` `type` `` is a string. It is a Scala keyword, so write it with backticks.
- `timestamp`, `expiration`, `brokerInTime` and `brokerOutTime` are `Long` values.
- `deliveryMode` and `priority` are `Int` values.
- `redelivered` is a `Boolean`.
- `destination` is an `ActiveMQMessageDestination`, a case class with one field, `physicalName: String`.
- `data: String` is the message payload as a raw string.

## KinesisEvent

`KinesisEvent` is a batch of records from an Amazon Kinesis data stream. Its one field, `records: List[KinesisRecord]`, is read from the JSON field `Records`, with a capital `R`.

Each `KinesisRecord` has eight required fields. `kinesis: KinesisRecordUnit` is the record itself, and `eventSource`, `eventID`, `invokeIdentityArn`, `eventName`, `eventVersion`, `eventSourceARN` and `awsRegion` are strings that describe the source of the record.

`KinesisRecordUnit` has these fields, all required:

- `kinesisSchemaVersion`, `partitionKey` and `sequenceNumber` are strings.
- `data: String` is the raw payload string. The type does not decode it for you.
- `approximateArrivalTimestamp: java.time.Instant` is decoded from a JSON number of seconds, which may have a fractional part. The decoder multiplies it by 1000 and converts it to epoch milliseconds, so the value keeps millisecond precision.
- `encryptionType: KinesisRecordUnitEncryptionType` is a sealed trait with two case objects, `KinesisRecordUnitEncryptionType.None` and `KinesisRecordUnitEncryptionType.Kms`. The decoder reads the JSON string without regard to case and fails on any other value with `Unknown Kinesis event encryptionType`.

The handler below decodes each record's payload and returns the number of records in the batch. It assumes the producer wrote UTF-8 text and that Lambda delivers the `data` field as Base64:

```scala
import java.nio.charset.StandardCharsets
import java.util.Base64

import zio._
import zio.lambda._
import zio.lambda.event._

object StreamHandler extends ZIOAppDefault {

  def payload(record: KinesisRecord): Task[String] =
    ZIO.attempt(new String(Base64.getDecoder.decode(record.kinesis.data), StandardCharsets.UTF_8))

  val handle = (event: KinesisEvent, _: Context) =>
    ZIO
      .foreach(event.records)(record => payload(record).tap(text => ZIO.logInfo(s"${record.kinesis.partitionKey}: $text")))
      .as(event.records.size)

  override val run = ZLambdaRunner.serve(handle)
}
```

`payload` returns a `Task`, so invalid Base64 becomes a failure of the invocation. The runner reports that failure to Lambda, which then retries the batch according to the event source mapping.

## KinesisFirehoseEvent

`KinesisFirehoseEvent` is an empty placeholder. It has no fields and decodes any payload to an empty value. The source file is named `KInesisFirehoseEvent.scala`, but the type is `KinesisFirehoseEvent`. See [Placeholder Types](#placeholder-types).

## Kinesis Analytics Events

Kinesis Analytics calls a Lambda function to preprocess input records or to deliver output records. The module has three event types for these calls.

### KinesisAnalyticsFirehoseInputPreprocessingEvent

This is the event for an input preprocessing function on a Kinesis Data Firehose source. It has four required fields. `invocationId`, `applicationArn` and `streamArn` are strings, and `records: List[KinesisAnalyticsFirehoseInputPreprocessingRecord]` is the batch.

Each `KinesisAnalyticsFirehoseInputPreprocessingRecord` has these fields, all required:

- `recordId: String` identifies the record. The response must echo it.
- `kinesisFirehoseRecordMetadata: KinesisFirehoseRecordMetadata` is a case class with one field, `approximateArrivalTimestamp: Long`.
- `data: String` is the raw payload string. The source keeps it as a `String` and has a note to revisit it, because the original AWS type is a `java.nio.ByteBuffer`.

The source file is named `KinesisAnalyticsFirehoseInputProcessingEvent.scala`, but the type name contains `Preprocessing`.

### KinesisAnalyticsStreamsInputPreprocessingEvent

This is the event for an input preprocessing function on a Kinesis Data Streams source. It is an empty placeholder: it has no fields and decodes any payload to an empty value. See [Placeholder Types](#placeholder-types).

### KinesisAnalyticsOutputDeliveryEvent

This is the event for an output delivery function. It has three required fields. `invocationId` and `applicationArn` are strings, and `records: List[KinesisAnalyticsOutputDeliveryRecord]` is the batch.

Each `KinesisAnalyticsOutputDeliveryRecord` has these fields, all required:

- `recordId: String` identifies the record. The response must echo it.
- `lambdaDeliveryRecordMetadata: LambdaDeliveryRecordMetadata` is a case class with one field, `retryHint: Long`.
- `data: String` is the raw payload string, with the same note about `ByteBuffer` as in the input preprocessing record.

## Kinesis Analytics Responses

The `zio-lambda-response` module has one response type for each of the two functions that return data. Both have only a `JsonEncoder` and no decoder, because a function returns them and never receives them.

### KinesisAnalyticsInputPreprocessingResponse

Return this from an input preprocessing function. Its one field, `records: List[KinesisAnalyticsInputPreprocessingResponseRecord]`, is the list of results, one per input record. Each `KinesisAnalyticsInputPreprocessingResponseRecord` has `recordId: String`, the ID of the input record that this result answers, and `result: KinesisAnalyticsInputPreprocessingResponseRecordResult`, a sealed trait with three case objects: `Ok`, `ProcessingFailed` and `Dropped`.

### KinesisAnalyticsOutputDeliveryResponse

Return this from an output delivery function. Its one field, `records: List[KinesisAnalyticsOutputDeliveryResponseRecord]`, is the list of results, one per delivered record. Each `KinesisAnalyticsOutputDeliveryResponseRecord` has `recordId: String`, the ID of the delivered record that this result answers, and `result: KinesisAnalyticsOutputDeliveryResponseRecordResult`, a sealed trait with two case objects: `Ok` and `DeliveryFailed`.

The result case objects live in the companion object of their sealed trait, for example `KinesisAnalyticsOutputDeliveryResponseRecordResult.Ok`.

:::warning[Check how the result is encoded]
The encoders for the two result traits are derived with `DeriveJsonEncoder.gen`. zio-json writes a sealed trait of case objects as an object that is keyed by the case name, so a record with the result `Ok` encodes as `{"recordId":"1","result":{"Ok":{}}}`, not as `"result":"Ok"`. Compare this output with what Kinesis Analytics expects before you rely on these response types in production. If it does not match, use `ZLambdaRunner.serveRaw` and build the JSON yourself.
:::

## DynamoDBEvent

`DynamoDBEvent` is an empty case class, `final case class DynamoDBEvent()`, and its companion object contains only commented-out code. It has no `JsonDecoder`. Passing it to `ZLambdaRunner.serve` fails to compile with this error:

```
could not find implicit value for evidence parameter of type zio.json.JsonDecoder[DynamoDBEvent]
```

Until the type is filled in, define your own case class and decoder for the fields you need. The handler below reads the event name and the key attributes of each record in a DynamoDB Streams batch. DynamoDB Streams use capitalized field names, so each one needs a `@jsonField` annotation:

```scala
import zio._
import zio.json._
import zio.lambda._

final case class StreamEvent(@jsonField("Records") records: List[StreamRecord])

object StreamEvent {
  implicit val decoder: JsonDecoder[StreamEvent] = DeriveJsonDecoder.gen[StreamEvent]
}

final case class StreamRecord(
  eventID: String,
  eventName: String,
  dynamodb: StreamChange
)

object StreamRecord {
  implicit val decoder: JsonDecoder[StreamRecord] = DeriveJsonDecoder.gen[StreamRecord]
}

final case class StreamChange(@jsonField("Keys") keys: Map[String, Map[String, String]])

object StreamChange {
  implicit val decoder: JsonDecoder[StreamChange] = DeriveJsonDecoder.gen[StreamChange]
}

object TableHandler extends ZIOAppDefault {

  val handle = (event: StreamEvent, _: Context) =>
    ZIO
      .foreachDiscard(event.records)(record => ZIO.logInfo(s"${record.eventName}: ${record.dynamodb.keys}"))
      .as(event.records.size)

  override val run = ZLambdaRunner.serve(handle)
}
```

Each key attribute is a one-entry object that maps a DynamoDB type code, such as `S` or `N`, to a string value, so `Map[String, Map[String, String]]` fits the keys. The `NewImage` and `OldImage` attributes can hold nested types such as lists and maps, so they need a richer attribute type, and this example leaves them out. zio-json ignores the fields that the case classes do not declare.
