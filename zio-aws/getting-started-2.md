# Summary

> Low-level AWS wrapper for [ZIO](https://zio.dev) for _all_ AWS services using the AWS Java SDK v2.

# Getting started

Low-level AWS wrapper for [ZIO](https://zio.dev) for _all_ AWS services using the AWS Java SDK v2.

The library's goal is to have access to all AWS functionality for cases when only a simple, direct access is needed from a ZIO application, and to be used as a building block for higher level wrappers around specific services.

Check the [list of available artifacts](artifacts.md) to get started. 

The [wrapper page](wrappers.md) shows in details how the library wraps the underlying _Java SDK_. On the [configuration page](configuration.md) you
can learn more about how set the common properties of the AWS clients in addition to setting up one of the [HTTP implementations](http.md).

### Features
- Common [configuration](configuration.md) layer
- ZIO layer per AWS service
- [Wrapper](wrappers.md) for all operations on all services
- [Http service implementations](http.md) for functional Scala http libraries, injected through ZIO's module system
- ZStream wrapper around paginated and streaming operations
- Service-specific extra configuration
- More idiomatic Scala request and response types wrapping the Java classes
- [Aspects](aspects.md) to take care of additional concerns like logging, metrics, circuit breaking, etc.

### Design
The library consists of a core module and one generated library for _each_ AWS service, based on the official JSON
schema from the AWS Java SDK's repository. By only providing a wrapper on top of the Java SDK the code
generator does not have to know all the implementation details and features of the schema. 

### Higher level AWS libraries
The following libraries are built on top of `zio-aws` providing higher level interfaces for specific AWS services:

- [ZIO DynamoDB](https://github.com/zio/zio-dynamodb)
- [ZIO Kinesis](https://github.com/svroonland/zio-kinesis)
- [ZIO SQS](https://github.com/zio/zio-sqs)

### Additional resources

- There is a [blog post](https://vigoo.github.io/posts/2020-09-23-zioaws-code-generation.html) explaining how the code generator is implemented.
- [This post](https://vigoo.github.io/posts/2020-11-01-zioaws-zioquery.html) shows an example of using `zio-aws` together with [ZIO Query](https://zio.github.io/zio-query/) 
- [Talk about generating libraries](https://www.youtube.com/watch?v=HCPTmytex3U) from Functional Scala 2021
