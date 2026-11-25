# Changelog

> This is a manually maintained list of important changes. Releases are published automatically from CI, so this list may not show every change right away. Dependency updates are not listed.

ZIO AWS versions have four parts, such as `7.59.2.5`. The first part is the ZIO AWS major version, the second and third parts are the minor and patch version of the AWS Java SDK the release is built on (here AWS SDK `2.59.2`), and the last part counts the releases built on that SDK version. The [Releases](https://github.com/zio/zio-aws/releases) page on GitHub lists every release.

#### 7.41.17.2
- `endpointOverride` in configuration must now start with `http://` or `https://`. A value without a scheme fails when the configuration is loaded, instead of causing a `NullPointerException` in the AWS SDK ([#626](https://github.com/zio/zio-aws/issues/626)).

#### 7.31.77.7
- Scala 2.12 is no longer published. ZIO AWS is published for Scala 2.13 and Scala 3.

#### 7.30.15.2
- New HTTP client module, `zio-aws-crt-http`, built on the AWS Common Runtime (CRT) HTTP client.

#### 7.x.x.x
- ZIO 2.0.18 introduced a `ZLayerAspect` which uses the same operator (`@@`) that ZIO AWS was using on layers, but it is not powerful enough to express this library's aspects. The new operator conflicts with the existing extension method, so starting from this version it has been renamed from `@@` to `@@@` to avoid the conflict. 

#### 6.x.x.x
- Using the new ZIO config providers, compatible with ZIO Config 4.x.x

#### 5.x.x.x
- Migrated to `ZIO 2.0.0` 
- Moved to the ZIO organisation
- Using the new ZIO service pattern
- Changed getter naming to match `zio-k8s`
- Using `zio-prelude` _newtype wrappers_ for primitives
- Using `ZIO` aspects as a base of `AwsCallAspect`
- Exposed lower level pagination API calls beside the streaming ones
- Using the `Optional` type instead of `Option` to reduce boilerplate

See the [migration guide](./migration_guide.md) for more information.

#### 4.17.104.1
- Migrated to `zio-config v2.0.0`

#### 3.15.35.5
- Support for defining dual HTTP/1.1 and HTTP/2 `HttpClient` layers
- Convert model case classes to `.ReadOnly` trait with `.asReadOnly`

#### 3.15.19.10
- Fix for libraries that use the `Integer` alias for ints (such as `zio-aws-sqs`)

#### 3.15.19.8
- Updated [fs2](https://fs2.io) release that fixes the http4s backend

#### 3.15.19.7
- Fix for event streaming wrappers such as `subscribeToShard` in `zio-aws-kinesis`

#### 3.15.16.0

- Introduced automatic release from CI, each new AWS SDK release triggers a new `zio-aws` build now
- zio-config support 

#### 2.14.7.0

- Updated to AWS SDK 2.14.7
- Fix an [issue](https://github.com/vigoo/zio-aws/issues/23) with http4s streaming uploads
- `Iterable` in place of `List` in the request models
- The akka-http client now gets the _actor system_ from the environment
- Code generator rewritten as an sbt plugin

#### 2.14.3.0
API breaking changes to make the streaming interface more ergonomic:
- Input/output byte streams are now flat (`ZStream[Any, AwsError, Byte]` instead of `ZStream[Any, AwsError, Chunk[Byte]`)
- Streaming operations return a `ZStream` that performs the request on first pull instead of a `ZIO[..., ZStream[...]]`
- Streaming for paginated operations that does not have a paginator in the Java SDK
- No `xxxStream` variants, streaming is the default and only interface for paginatable operations
- Updated to AWS SDK 2.14.3
- Fixed handling of some error cases
- Scala 2.12 version is now available

#### 1.13.69.1
Initial release republished with fixed metadata in POMs

#### 1.13.69.0
Initial release based on AWS Java SDK 2.13.69
