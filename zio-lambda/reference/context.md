# Context

> `Context` describes one invocation. The runner builds a new `Context` for every event and passes it as the second argument of your function. It is a case class:

```scala
final case class Context(
  awsRequestId: String,
  logGroupName: String,
  logStreamName: String,
  functionName: String,
  functionVersion: String,
  invokedFunctionArn: String,
  remainingTimeInMillis: Long,
  memoryLimitInMB: Int,
  clientContext: Option[ClientContext],
  cognitoIdentity: Option[CognitoIdentity]
)
```

## Fields

- `awsRequestId` is the ID of the current request.
- `logGroupName` and `logStreamName` name the CloudWatch Logs group and stream of the function. The runner reads them from the environment that Lambda sets.
- `functionName` and `functionVersion` identify the running function and version. The runner reads them from the environment.
- `invokedFunctionArn` is the ARN that was used to invoke the function, taken from the invocation.
- `remainingTimeInMillis` is named like a duration, but the runtime fills it with the value of the `Lambda-Runtime-Deadline-Ms` header, which is the Unix time in milliseconds at which Lambda stops the invocation. To get the time left, subtract the current time: `context.remainingTimeInMillis - java.lang.System.currentTimeMillis()`.
- `memoryLimitInMB` is the memory size configured for the function.
- `clientContext` is `Some` only for invocations from the AWS Mobile SDK that carry client context.
- `cognitoIdentity` is `Some` only for invocations that carry Amazon Cognito identity information.

## ClientContext

`ClientContext` has three fields: `client` (a `Client`), `custom` (a `Map[String, String]`) and `env` (a `Map[String, String]`). It has a `JsonDecoder` in its companion object.

`Client` describes the mobile app and has five `String` fields: `installationId`, `appTitle`, `appVersionName`, `appVersionCode` and `appPackageName`.

## CognitoIdentity

`CognitoIdentity` has two `String` fields: `cognitoIdentityId` and `cognitoIdentityPoolId`.

## Constructing a Context

`Context` has a public constructor, so tests can create one with fixed values. The `Context.from` method that the runner uses is private to the package. See [Testing a Handler Locally](../testing-locally.md) for an example.
