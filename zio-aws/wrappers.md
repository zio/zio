# Wrappers

> Reference for what each generated ZIO AWS service module contains: the service trait and its layers, the operation wrappers, StreamingOutputResult, and the model wrappers.

Every AWS service module, such as `zio-aws-ec2`, is generated from the AWS Java SDK's service model. All of them have the same shape, so this page describes the pattern with examples from a few services.

## Service Modules

Each module has a package `zio.aws.<service>` with a service trait and its companion object, a `model` package with the request, response and data types, and a `model.primitives` package with newtypes. For EC2 the service is `zio.aws.ec2.Ec2`:

```scala
trait Ec2 extends AspectSupport[Ec2] {
  val api: Ec2AsyncClient

  def describeRegions(request: DescribeRegionsRequest): ZIO[Any, AwsError, DescribeRegionsResponse.ReadOnly]
  // ... one method per operation
}
```

The `api` field is the AWS Java SDK's async client that the service wraps. Use it for SDK features that are not operations, such as waiters; the [Customize Clients and Call the AWS SDK Directly](guide-call-the-sdk-client-directly.md) guide shows how. `AspectSupport` lets [aspects](aspects.md) be attached to the service.

### Layers

The companion object builds the service. All three constructors depend on [`AwsConfig`](configuration.md):

```scala
object Ec2 {
  val live: ZLayer[AwsConfig, Throwable, Ec2]
  def customized(customization: Ec2AsyncClientBuilder => Ec2AsyncClientBuilder): ZLayer[AwsConfig, Throwable, Ec2]
  def scoped(customization: Ec2AsyncClientBuilder => Ec2AsyncClientBuilder): ZIO[AwsConfig & Scope, Throwable, Ec2]
}
```

`live` builds the client with the settings of `AwsConfig`. `customized(f)` builds the same client, but first applies `f` to the SDK client builder, after `AwsConfig` has configured it, so `f` can change any setting, including service-specific ones. `scoped(f)` does the same as `customized(f)` but returns a scoped `ZIO` instead of a layer, so you can build several clients and manage their lifetimes yourself. The client is closed when its layer or scope ends.

While it builds a client, the service also:

- Runs the SDK's future completions on the ZIO executor, so the code after an AWS call continues on a ZIO thread.
- Asks the `HttpClient` for an HTTP/2 client for `kinesis` and `transcribestreaming`, the services generated with HTTP/2 support, and for an HTTP/1.1 client for every other service. See [Choose an HTTP Client](guide-choose-http-client.md).
- Sets the region to `aws-global` for the global services `cloudfront`, `globalaccelerator`, `iam`, `networkmanager`, `organizations`, `route53`, `shield` and `waf`, before applying `f`.

### Accessors

The companion object also has one accessor per operation. An accessor calls the operation on the service from the environment, so it has the service in its `R` type:

```scala
object Ec2 {
  def describeRegions(request: DescribeRegionsRequest): ZIO[Ec2, AwsError, DescribeRegionsResponse.ReadOnly]
}
```

Use the accessors in code that has the service in its environment, and the trait's methods when you hold a service value, such as one built with `scoped`.

## Operations

The generator picks the wrapper shape from the operation's input and output. The signatures below are the accessors.

### Request and Response

A simple request-response operation returns a `ZIO` with the read-only response wrapper:

```scala
def deleteVolume(request: DeleteVolumeRequest): ZIO[Ec2, AwsError, DeleteVolumeResponse.ReadOnly]
```

An operation without input has no parameter, and an operation without output returns `Unit`.

### Byte Streams

For operations whose input or output is a byte stream, the generator uses `ZStream[Any, AwsError, Byte]`:

```scala
def getObject(request: GetObjectRequest): ZIO[S3, AwsError, StreamingOutputResult[Any, GetObjectResponse.ReadOnly, Byte]]
def putObject(request: PutObjectRequest, body: ZStream[Any, AwsError, Byte]): ZIO[S3, AwsError, PutObjectResponse.ReadOnly]
```

An input stream is sent with the length from the request's `contentLength` field, when the request has one. A streamed output comes in a `StreamingOutputResult`, described [below](#streamingoutputresult). The [Stream S3 Objects](guide-stream-s3-objects.md) guide uses both.

### Event Streams

For operations with event streams, the input or output is a `ZStream` of model types:

```scala
def startStreamTranscription(
    request: StartStreamTranscriptionRequest,
    input: ZStream[Any, AwsError, AudioStream]
): ZStream[TranscribeStreaming, AwsError, TranscriptEvent.ReadOnly]
```

The AWS call starts when the stream is pulled.

### Paginated Operations

For operations that return their results in pages, the generator creates a streaming wrapper that requests the next page when the stream needs more items, and a `Paginated` variant that makes one call. The streaming wrapper has one of two shapes. Most paginated operations return a `ZStream` of the items:

```scala
def scan(request: ScanRequest): ZStream[DynamoDb, AwsError, Map[AttributeName, AttributeValue.ReadOnly]]
def scanPaginated(request: ScanRequest): ZIO[DynamoDb, AwsError, ScanResponse.ReadOnly]
```

For operations whose request and response both have a `NextToken` field, ZIO AWS pages through the results itself, following the next-page token. Other paginated operations use the AWS Java SDK's paginator. When the response of an operation that ZIO AWS pages itself has fields besides the items and the token, the first page is fetched right away, and the result is a `StreamingOutputResult` whose `response` is the first page's response and whose `output` is the stream of items from all pages:

```scala
def listSomething(request: ListSomethingRequest): ZIO[Svc, AwsError, StreamingOutputResult[Any, ListSomethingResponse.ReadOnly, Item.ReadOnly]]
```

For operations that ZIO AWS pages itself, each page request is a separate AWS call that runs through the service's aspects; for operations that use the Java SDK paginator, the aspect runs once for the whole stream. Use the `Paginated` variant to forward pages through your own API, passing the next-page token yourself. The [Paginate and Stream Results](guide-paginate-and-stream.md) guide shows both.

## StreamingOutputResult

`StreamingOutputResult` pairs a response with a stream, for streamed downloads and for paginated operations whose response has extra fields:

```scala
case class StreamingOutputResult[R, Response, Item](
    response: Response,
    output: ZStream[R, AwsError, Item]
) {
  def mapResponse[Response2](f: Response => Response2): StreamingOutputResult[R, Response2, Item]
  def mapOutput[I](f: ZStream[R, AwsError, Item] => ZStream[R, AwsError, I]): StreamingOutputResult[R, Response, I]
  def provideEnvironment(r: ZEnvironment[R]): StreamingOutputResult[Any, Response, Item]
}
```

`mapResponse` and `mapOutput` change one half and keep the other, and `provideEnvironment` provides the stream's environment. A downloaded byte stream reads the HTTP response body as it arrives, so run it once.

## Model Wrappers

For each model type, the generator creates:

- A case class with default parameter values, used as input, instead of the SDK's builder
- A `ReadOnly` trait, used for output, that wraps the SDK's immutable object
- [ZIO Prelude newtypes](https://zio.dev/zio-prelude/newtypes/) in `model.primitives` for primitive values, such as S3's `BucketName` or `ObjectKey`
- Scala collections instead of Java collections
- A sealed trait with one case object per value instead of each Java enum
- ZIO Prelude's `Optional` type for optional fields, which accepts a plain value or `Optional.Absent` in a constructor

### Input Case Classes

This example from `zio-aws-elasticsearch` shows a generated case class. Every optional field defaults to `Optional.Absent`, and `buildAwsValue()` builds the SDK object:

```scala
case class DescribePackagesFilter(
    name: Optional[DescribePackagesFilterName] = Optional.Absent,
    value: Optional[Iterable[primitives.DescribePackagesFilterValue]] = Optional.Absent
) {
  def buildAwsValue(): software.amazon.awssdk.services.elasticsearch.model.DescribePackagesFilter
  def asReadOnly: DescribePackagesFilter.ReadOnly
}
```

Newtypes are created with their `apply`, such as `ObjectKey("reports/2026.csv")`, and read back with the companion's `unwrap`, such as `ObjectKey.unwrap(key)`. Fields without a dedicated AWS type, such as EC2's region names and instance IDs, are plain Scala types.

### Read-Only Wrappers

Responses and the values inside them are read-only wrappers around the SDK objects. This example is from `zio-aws-transcribe`:

```scala
object CreateMedicalVocabularyResponse {
  trait ReadOnly {
    def asEditable: CreateMedicalVocabularyResponse
    def vocabularyName: Optional[VocabularyName]
    def languageCode: Optional[LanguageCode]
    def getVocabularyName: ZIO[Any, AwsError, VocabularyName]
    def getLanguageCode: ZIO[Any, AwsError, LanguageCode]
    // ... one pair per field
  }

  def wrap(impl: software.amazon.awssdk.services.transcribe.model.CreateMedicalVocabularyResponse): ReadOnly
}
```

Each field has two accessors. The plain one, such as `vocabularyName`, returns the `Optional` value. The `get`-prefixed one, such as `getVocabularyName`, is an effect that fails with [`FieldIsNone`](aws-error.md) when the field is absent, which makes it easy to chain AWS calls in a for-comprehension. `asEditable` copies the values into the case class, so you can change them and send them in a new request, and `wrap` turns an SDK object into the wrapper.

## Testing

The library does not generate mock objects for the services. To test code that calls AWS, put the calls behind a small service of your own and provide a stub in tests. The [Test Code That Uses AWS](guide-test-code-with-stubs.md) guide shows the pattern.
