# Related Libraries, Articles and Talks

> This page collects libraries built on ZIO AWS, and articles and talks about how it works.

## Higher-Level AWS Libraries

The following libraries are built on top of ZIO AWS and provide higher-level interfaces for specific AWS services:

- [ZIO DynamoDB](https://github.com/zio/zio-dynamodb)
- [ZIO Kinesis](https://github.com/svroonland/zio-kinesis)
- [ZIO SQS](https://github.com/zio/zio-sqs)

## Articles and Talks

These articles and talks explain how ZIO AWS is built and show it with other ZIO libraries:

- [ZIO AWS code generation](https://vigoo.github.io/posts/2020-09-23-zioaws-code-generation.html) explains how the code generator is implemented.
- [ZIO AWS with ZIO Query](https://vigoo.github.io/posts/2020-11-01-zioaws-zioquery.html) shows ZIO AWS together with [ZIO Query](https://zio.dev/zio-query/).
- [Generating libraries](https://www.youtube.com/watch?v=HCPTmytex3U) is a talk from Functional Scala 2021.

## Source and Releases

- The source code is on [GitHub](https://github.com/zio/zio-aws).
- Every module is published to [Maven Central](https://central.sonatype.com/search?q=dev.zio+zio-aws) under the `dev.zio` organization.
- The [Changelog](changelog.md) lists the important changes per major version, and the [Migration Guide](migration_guide.md) explains how to upgrade.
