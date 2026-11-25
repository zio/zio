# How to Define Your Own Event Type

> Define your own event type when the AWS event you receive has no type in `zio-lambda-event`, when the provided type is an empty placeholder, or when you only need a few fields. This guide shows how.

## Write a Case Class and a Decoder

Declare only the fields you need, and put a `JsonDecoder` in the companion object so `ZLambdaRunner.serve` finds it. zio-json ignores JSON fields that your case class does not declare. For an Amazon DynamoDB Streams event, a type that reads the event name and the event source ARN of each record could look like this:

```scala
import zio._
import zio.json._
import zio.lambda._

final case class StreamRecord(eventName: String, eventSourceARN: String)

object StreamRecord {
  implicit val decoder: JsonDecoder[StreamRecord] = DeriveJsonDecoder.gen[StreamRecord]
}

final case class StreamEvent(@jsonField("Records") records: List[StreamRecord])

object StreamEvent {
  implicit val decoder: JsonDecoder[StreamEvent] = DeriveJsonDecoder.gen[StreamEvent]
}

object StreamHandler extends ZIOAppDefault {

  val handler = (event: StreamEvent, _: Context) =>
    ZIO.foreachDiscard(event.records)(r => ZIO.logInfo(s"${r.eventName} from ${r.eventSourceARN}")).as("ok")

  override val run = ZLambdaRunner.serve(handler)
}
```

`@jsonField("Records")` maps the capitalised JSON key `Records` to the Scala field `records`.

## Why Not the Provided Type

`DynamoDBEvent` in `zio-lambda-event` has no decoder, so `ZLambdaRunner.serve` does not compile with it. Other events, such as `S3Event` and `SNSEvent`, are empty placeholders. They decode, but they hold no data. See [Event and Response Types](../event-and-response-types.md) for the list.

## Make Fields Optional When They Can Be Missing

If a field is not always present in the JSON, declare it as an `Option`. A required field that is missing makes decoding fail, and the runner then reports an invocation error.
