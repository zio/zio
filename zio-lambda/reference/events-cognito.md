# Cognito Events

> Reference for the Amazon Cognito event types in zio-lambda-event: the Sync event, user pool trigger events, and which are empty placeholders.

The `zio-lambda-event` module has twelve types for Amazon Cognito. One of them, `CognitoEvent`, models a Cognito Sync dataset event. The other eleven are named `CognitoUserPool...Event` and model user pool triggers. All of them live in the `zio.lambda.event` package, and each has a `JsonDecoder` and no `JsonEncoder`.

Most of the user pool types are empty placeholders: a case class with no fields. A placeholder has a decoder, so you can use it as the event type of a handler, but a decoded value exposes no data from the event. Only two user pool types have real fields. The sections below say which is which.

For the general rules about event types and codecs, see [Event and Response Types](../event-and-response-types.md).

## Returning the Event

A Cognito user pool trigger expects your function to return the event JSON back to Cognito, with your changes written into its `response` section. The module cannot do this for you: it has no Cognito response type (the `zio.lambda.response` package has none), and the Cognito event types have decoders only, so they cannot be passed as the output type of `ZLambdaRunner.serve`, which needs a `JsonEncoder` for the output. To answer a trigger you need your own encodable type, or `ZLambdaRunner.serveRaw`, which gives you the raw JSON string and expects a JSON string back. See [ZLambdaRunner](./zlambda-runner.md).

## CognitoEvent

`CognitoEvent` models a Cognito Sync event, which Cognito sends when a dataset in an identity pool changes. It has seven fields:

- `region` is a `String`.
- `datasetRecords` is a `Map[String, CognitoDatasetRecord]`.
- `identityPoolId` is a `String`.
- `identityId` is a `String`.
- `datasetName` is a `String`.
- `eventType` is a `String`.
- `version` is an `Int`.

`CognitoDatasetRecord` describes one changed record and has three `String` fields: `oldValue`, `newValue` and `op`.

Both types have their decoder in their companion object. The example decodes a Sync event from JSON and reads its records:

```scala
import zio.json._
import zio.lambda.event.CognitoEvent

val json =
  """{
    |  "region": "us-east-1",
    |  "datasetRecords": {
    |    "score": { "oldValue": "1", "newValue": "2", "op": "replace" }
    |  },
    |  "identityPoolId": "us-east-1:pool",
    |  "identityId": "us-east-1:identity",
    |  "datasetName": "game",
    |  "eventType": "SyncTrigger",
    |  "version": 2
    |}""".stripMargin

val decoded: Either[String, CognitoEvent] = json.fromJson[CognitoEvent]

val scores = decoded.map(_.datasetRecords.get("score").map(_.newValue))
```

The `fromJson` call returns `Left` with an error message when the JSON does not match, and `Right` with the event otherwise. The last line reads the `newValue` of the record stored under the key `score`.

## Event Types with Fields

Two user pool events have fields. Both have the same eight top-level fields:

- `version`, `triggerSource`, `region`, `userPoolId` and `userName` are `String` values.
- `callerContext` describes the caller.
- `request` carries the data that Cognito sends to the trigger.
- `response` carries the data that the trigger sends back.

The three nested types differ between the two events.

### CognitoUserPoolCreateAuthChallengeEvent

This event is for the Create Auth Challenge trigger. Its nested types are:

- `CognitoUserPoolCreateAuthChallengeCallerContext` has two `String` fields, `awsSdkVersion` and `clientId`.
- `CognitoUserPoolCreateAuthChallengeRequest` has `clientMetadata` (a `Map[String, String]`), `challengeName` (a `String`) and `userNotFound` (a `Boolean`). The source has a `session` field commented out, so the event does not expose the challenge session.
- `CognitoUserPoolCreateAuthChallengeResponse` has `publicChallengeParameters` and `privateChallengeParameters` (both `Map[String, String]`) and `challengeMetadata` (a `String`).

:::warning[The decoder is in a differently named object]
The decoder for `CognitoUserPoolCreateAuthChallengeEvent` is in an object named `CognitoUserPoolCreateAuthChallenge`, without the `Event` suffix, so it is not in the implicit scope of the event type. Import the object's members before you decode or serve this event:

```scala
import zio.lambda.event.CognitoUserPoolCreateAuthChallenge._
```
:::

### CognitoUserPoolCustomMessageEvent

This event is for the Custom Message trigger. Its nested types are:

- `CognitoUserPoolCustomMessageCallerContext` has two `String` fields, `awsSdkVersion` and `clientId`.
- `CognitoUserPoolCustomMessageRequest` is an empty placeholder with no fields.
- `CognitoUserPoolCustomMessageResponse` is an empty placeholder with no fields.

So for this event you can read the top-level fields and `callerContext`, but `request` and `response` carry no data.

## Empty Placeholder Types

These nine types are case classes with no fields. Each has a decoder, and none of them exposes any data from the event:

- `CognitoUserPoolEvent`
- `CognitoUserPoolDefineAuthChallengeEvent`
- `CognitoUserPoolMigrateUserEvent`
- `CognitoUserPoolPostAuthenticationEvent`
- `CognitoUserPoolPostConfirmationEvent`
- `CognitoUserPoolPreAuthenticationEvent`
- `CognitoUserPoolPreSignUpEvent`
- `CognitoUserPoolPreTokenGenerationEvent`
- `CognitoUserPoolVerifyAuthChallengeResponseEvent`

Because they hold nothing, they are not enough to implement the triggers they are named after. For a trigger that needs the request data, define your own case class with the fields you read and a `JsonDecoder` for it, or use `ZLambdaRunner.serveRaw` and parse the JSON yourself.

:::note[One more decoder in a differently named object]
The decoder of `CognitoUserPoolPreAuthenticationEvent` is in an object named `CognitoUserPoolPreAuthentication`, without the `Event` suffix, as with the Create Auth Challenge event. To use it with `ZLambdaRunner.serve`, import `zio.lambda.event.CognitoUserPoolPreAuthentication._`. The other eight placeholder types have their decoder in the companion object of the same name.
:::
