# Paginate and Stream Results

> Read every page of a paginated AWS operation as a ZIO stream, limit how much it fetches, and call a single page directly.

In this guide, we will read all the results of a paginated AWS operation without writing a loop that tracks the next-page token. By the end, you will know how to consume a stream of results, how to limit it and how to fetch one page when you need that instead.

We'll take an incremental approach: first we read the whole stream, then we count and limit it, and finally we fetch a single page.

## The Problem

Many AWS operations return only part of their results per call. The response carries a next-page token, and the caller must send that token back to get the next part. A complete listing needs a loop that keeps the token, stops when it is absent and handles failures on every page.

Writing that loop by hand is repetitive, and it is easy to get wrong. A missed token stops the listing early, and an error on the third page can leave the code without a clear way to recover.

ZIO AWS generates a stream for each paginated operation. The stream fetches the next page when it needs one, so your code sees one item at a time.

## Prerequisites

Add the core module and the EC2 service module, plus an HTTP client:

```scala
libraryDependencies ++= Seq(
  "dev.zio" %% "zio-aws-core"  % "7.59.5.2",
  "dev.zio" %% "zio-aws-ec2"   % "7.59.5.2",
  "dev.zio" %% "zio-aws-netty" % "7.59.5.2"
)
```

The examples use these imports:

```scala
import zio._
import zio.stream._
import zio.aws.core.AwsError
import zio.aws.ec2.Ec2
import zio.aws.ec2.model._
```

## Reading the Whole Stream

`Ec2.describeInstances` is a paginated operation, so the wrapper returns a `ZStream` of `Reservation.ReadOnly` items. Each item is one reservation from one page:

```scala
val reservations: ZStream[Ec2, AwsError, Reservation.ReadOnly] =
  Ec2.describeInstances(DescribeInstancesRequest())
```

The stream type is `ZStream[Ec2, AwsError, Reservation.ReadOnly]`. Nothing runs yet. The AWS call happens when a consumer pulls the first item, and the next page is requested only after the previous items have been consumed.

To count every instance across all reservations:

```scala
val instanceCount: ZIO[Ec2, AwsError, Long] =
  Ec2
    .describeInstances(DescribeInstancesRequest())
    .mapZIO(_.getInstances)
    .flatMap(ZStream.fromIterable(_))
    .runCount
```

The code above:

- `mapZIO(_.getInstances)` reads the instances of each reservation. The getter is a ZIO effect, because the field is optional in the AWS model.
- `flatMap(ZStream.fromIterable(_))` turns each list of instances into individual stream items.
- `runCount` consumes the stream and returns the number of instances.

## Limiting the Stream

A stream can contain many pages, and the operation keeps paging until the listing is complete. Use the standard stream operators to stop early. This example reads at most ten reservations:

```scala
val firstTenReservations: ZIO[Ec2, AwsError, Chunk[Reservation.ReadOnly]] =
  Ec2
    .describeInstances(DescribeInstancesRequest())
    .take(10)
    .runCollect
```

`take(10)` stops the stream once it has ten items, so the operation stops requesting further pages. This matters for listings that run to thousands of items.

:::warning
`runCollect` loads every item it receives into memory. For a very large listing, process the items with `foreach` or `runCount` instead.
:::

## Fetching a Single Page

Sometimes you need the page itself, for example to forward it through an HTTP API that has its own paging. For those cases the library also generates a non-streaming wrapper with a `Paginated` suffix. It makes one call and returns the raw response:

```scala
val onePage: ZIO[Ec2, AwsError, DescribeInstancesResponse.ReadOnly] =
  Ec2.describeInstancesPaginated(DescribeInstancesRequest())
```

The response contains the `reservations` for that page and a `nextToken` field. Pass the token in the next request yourself to fetch the following page.

## Going Further

- The [Wrappers](./wrappers.md) reference describes every kind of operation wrapper.
- Errors raised while a stream is running are the same `AwsError` values as in a single call. See [Handle AWS Errors](./guide-handle-errors.md).
