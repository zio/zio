# AWS Errors

> Reference for AwsError, the error type of every ZIO AWS call, with its two cases and the helper functions that create them.

Every ZIO AWS operation fails with the type `AwsError`. The type is a sealed trait, so the compiler can check that a pattern match covers every case. The [Handle AWS Errors](./guide-handle-errors.md) guide shows how to use it in practice.

## AwsError

`AwsError` is declared in the `zio.aws.core` package with two cases:

```scala
sealed trait AwsError {
  def toThrowable: Throwable
}
```

Both cases implement `toThrowable`, which returns a `Throwable` that describes the failure.

## GenericAwsError

`GenericAwsError` represents a failure that the AWS SDK reported:

```scala
case class GenericAwsError(reason: Throwable) extends AwsError
```

The `reason` is the exception that the SDK threw. For a service-level failure it is the service's own exception class, such as `Ec2Exception` for EC2, which carries the AWS error code. For a transport failure it can be a different exception, such as one from the HTTP client.

`toThrowable` returns the `reason` unchanged.

## FieldIsNone

`FieldIsNone` represents a field that the response must contain but that is absent:

```scala
case class FieldIsNone(field: String) extends AwsError
```

The `field` is the name of the missing field, as it appears in the AWS model. Getter functions on the response wrappers, such as `getRegions`, produce this case when the field is `None` in the response.

`toThrowable` returns a `NoSuchElementException` whose message is the field name.

## Helper Functions

The companion object of `AwsError` has two functions that the library uses internally and that you can also call.

`AwsError.fromThrowable(reason: Throwable): AwsError` wraps a `Throwable` in a `GenericAwsError`. When the throwable is a `CompletionException`, the function unwraps its cause first, so the `reason` is the original failure.

`AwsError.unwrapOptionField[T](name: String, value: => Optional[T]): ZIO[Any, AwsError, T]` returns the value of an optional field. It succeeds with the value when the field is present, and it fails with `FieldIsNone(name)` when the field is absent. The `Optional` type is `zio.prelude.data.Optional`.

## Error Flow

Errors flow in one direction. The SDK throws an exception, `AwsError.fromThrowable` wraps it into `GenericAwsError`, and the effect fails with that value. A missing field fails with `FieldIsNone`, created by `AwsError.unwrapOptionField`. Aspects see both cases on the same error channel, so a logging or retry aspect handles them the same way. See [Add Aspects](./guide-add-aspects.md).

## Related Pages

- [Handle AWS Errors](./guide-handle-errors.md) shows pattern matching and conversion.
- [Wrappers](./wrappers.md) describes the getters that produce `FieldIsNone`.
