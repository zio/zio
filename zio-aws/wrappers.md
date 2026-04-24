# Wrappers

> The live implementation depends on a core _AWS configuration layer_:

# Client wrappers

### Service modules
For each AWS Service the library defines a _ZIO service_ with wrapper functions for all the _operations_, a `live` 
implementation calling the Java SDK and a `mock` implementation using [zio-mock](https://github.com/zio/zio-mock).

The live implementation depends on a core _AWS configuration layer_:

```scala
val live: ZLayer[AwsConfig, Throwable, Ec2]
``` 

The `AwsConfig` layer defines how each service's async Java client gets configured, including the http client which is
provided by another layer `AwsConfig` is depending on.

Each module has accessor functions for _all operations_ of the given service.

### Operations

For simple request-response operations the library generates a very light wrapper:

```scala
def deleteVolume(request: DeleteVolumeRequest): ZIO[Ec2, AwsError, DeleteVolumeResponse.ReadOnly]
```

For operations where either the input or the output or both are _byte streams_, a `ZStream` wrapper is generated:

```scala
def getObject(request: GetObjectRequest): ZIO[S3, AwsError, StreamingOutputResult[Any, GetObjectResponse.ReadOnly, Byte]]
def putObject(request: PutObjectRequest, body: ZStream[Any, AwsError, Byte]): ZIO[S3, AwsError, PutObjectResponse.ReadOnly]
```

where the output is a stream packed together with additional response data:

```scala
case class StreamingOutputResult[R, Response, Item](
  response: Response,
  output: ZStream[R, AwsError, Item]
)
```

For operations with _event streams_ a `ZStream` of a model type gets generated:

```scala
def startStreamTranscription(request: StartStreamTranscriptionRequest, input: ZStream[Any, AwsError, AudioStream]): ZStream[TranscribeStreaming, AwsError, TranscriptEvent.ReadOnly]
```

And for all operations that supports _pagination_, streaming wrappers gets generated:

```scala
def scan(request: ScanRequest): ZStream[DynamoDb, AwsError, Map[AttributeName, AttributeValue.ReadOnly]]
```

Note that for event streaming or paginating operations returning a `ZStream` the actual AWS call happens when the stream gets pulled.

For use cases when calling the _paginating_ interface directly is necessary - for example when forwarding paginated results through a HTTP API, the library generates non-streaming wrappers as well for these methods.

For example the DynamoDB `scan` method's non-streaming variant is defined as:

```scala
def scanPaginated(request: ScanRequest): ZIO[DynamoDb, ScanResponse.ReadOnly]
```

### Model wrappers
For each model type a set of wrappers are generated, providing the following functionality:

- Case classes with default parameter values instead of the _builder pattern_
- [zio-prelude's newtype wrappers](https://zio.github.io/zio-prelude/docs/newtypes/) for primitive types
- Automatic conversion to Scala collection types
- ADTs instead of the Java enums 
- ZIO getter functions to "get or fail" the optional model fields
- Using zio-prelude's `Optional` type to eliminate boilerplate when constructing models with many optional fields

The following example from the `zio-aws-elasticsearch` library shows how the generated case classes look like, to be used as input for the service operations:

```scala
case class DescribePackagesFilter(name: Optional[DescribePackagesFilterName] = Optional.Absent, 
                                  value: Optional[Iterable[primitives.DescribePackagesFilterValue]] = Optional.Absent) {
    def buildAwsValue(): software.amazon.awssdk.services.elasticsearch.model.DescribePackagesFilter = {

      software.amazon.awssdk.services.elasticsearch.model.DescribePackagesFilter
        .builder()
        .optionallyWith(name.map(value => value.unwrap))(_.name)
        .optionallyWith(value.map(value => value.map { item => item: java.lang.String }.asJava))(_.value)
        .build()
    }

    def asReadOnly: DescribePackagesFilter.ReadOnly = DescribePackagesFilter.wrap(buildAwsValue())
}
```

When processing the _results_ of the operations (either directly or though the `ZStream` wrappers), the AWS Java model types are wrapped
by a _read-only wrapper interface_. The following example shows one from the `transcribe` module:

```scala
object CreateMedicalVocabularyResponse {
  private lazy val zioAwsBuilderHelper: BuilderHelper[software.amazon.awssdk.services.transcribe.model.CreateMedicalVocabularyResponse] = BuilderHelper.apply

  trait ReadOnly {
    def editable: CreateMedicalVocabularyResponse = CreateMedicalVocabularyResponse(vocabularyNameValue.map(value => value), languageCodeValue.map(value => value), vocabularyStateValue.map(value => value), lastModifiedTimeValue.map(value => value), failureReasonValue.map(value => value))
    def vocabularyName: Optional[VocabularyName]
    def languageCode: Optional[LanguageCode]
    def vocabularyState: Optional[VocabularyState]
    def lastModifiedTime: Optional[DateTime]
    def failureReason: Optional[FailureReason]
    def getVocabularyName: ZIO[Any, AwsError, VocabularyName] = AwsError.unwrapOptionField("vocabularyName", vocabularyNameValue)
    def getLanguageCode: ZIO[Any, AwsError, LanguageCode] = AwsError.unwrapOptionField("languageCode", languageCodeValue)
    def getVocabularyState: ZIO[Any, AwsError, VocabularyState] = AwsError.unwrapOptionField("vocabularyState", vocabularyStateValue)
    def getLastModifiedTime: ZIO[Any, AwsError, DateTime] = AwsError.unwrapOptionField("lastModifiedTime", lastModifiedTimeValue)
    def getFailureReason: ZIO[Any, AwsError, FailureReason] = AwsError.unwrapOptionField("failureReason", failureReasonValue)
  }

  private class Wrapper(impl: software.amazon.awssdk.services.transcribe.model.CreateMedicalVocabularyResponse) extends CreateMedicalVocabularyResponse.ReadOnly {
    // ... implements the ReadOnly interface by querying the underlying Java object
  }

  def wrap(impl: software.amazon.awssdk.services.transcribe.model.CreateMedicalVocabularyResponse): ReadOnly = new Wrapper(impl)
}
```

As a large part of the models in the AWS SDK are defined as _optional_, the generated wrapper also contains ZIO accessor functions,
which lift the option value to make it more comfortable to chain the AWS operations.

### Mocks
Each module also contains generated [_ZIO Test mocks_](https://github.com/zio/zio-mock) for the given service.

The following example shows how to use them with the `zio-aws-athena` library:

```scala
val athena = AthenaMock.StartQueryExecution(
  hasField(
    "queryString",
    (startQueryExecutionRequest: StartQueryExecutionRequest) =>
      startQueryExecutionRequest.queryString,
      equalTo(givenQuery)
  ),
  value(
    StartQueryExecutionResponse.wrap(
      software.amazon.awssdk.services.athena.model.StartQueryExecutionResponse
        .builder()
        .queryExecutionId(executionId)
        build()
    )
  )
)

codeUsingAthena.provide(athena)
```
