# List Objects Page by Page

> S3 returns at most `maxKeys` objects per request and gives a continuation token when more objects remain. ZIO S3 hides that paging for most uses: `listAllObjects` returns one stream of every object in a bucket. When you need one page at a time, you can call `listObjects` and `getNextObjects` yourself. The options that control a listing are in the [options reference](./options.md#listobjectoptions).

## Stream All Objects

To read every object in a bucket, call `listAllObjects`. It requests pages as the stream is consumed, so you can stop early without loading the whole bucket:

```scala
import zio.s3._
import zio.stream.ZStream
import software.amazon.awssdk.services.s3.model.S3Exception

val allKeys: ZStream[S3, S3Exception, String] =
  listAllObjects("docs").map(_.key)
```

## Limit a Listing by Prefix and Page Size

To list only the objects whose keys start with a prefix, and to request 500 objects per page, build the options with `ListObjectOptions.from`:

```scala
import zio.s3._
import zio.stream.ZStream
import software.amazon.awssdk.services.s3.model.S3Exception

val logKeys: ZStream[S3, S3Exception, String] =
  listAllObjects("docs", ListObjectOptions.from("logs/", 500)).map(_.key)
```

`ListObjectOptions.fromMaxKeys` sets only the page size, and `ListObjectOptions.fromStartAfter` starts the listing after a given key. For a combination of settings, call the `ListObjectOptions` constructor directly.

## Fetch One Page at a Time

To read a single page, call `listObjects`. The returned `S3ObjectListing` holds the objects of that page and a `nextContinuationToken` when more remain. Pass the listing to `getNextObjects` to fetch the following page:

```scala
import zio._
import zio.s3._
import software.amazon.awssdk.services.s3.model.S3Exception

val secondPage: ZIO[S3, S3Exception, S3ObjectListing] =
  for {
    first  <- listObjects("docs", ListObjectOptions.fromMaxKeys(100))
    second <- getNextObjects(first)
  } yield second
```

When `nextContinuationToken` is `None`, the listing is the last page, and `getNextObjects` returns an empty listing.

## Stream Pages

To stream whole pages instead of single objects, pass the first listing to `paginate`. It emits the first page, then each following page, and stops after the last one:

```scala
import zio.s3._
import zio.stream.ZStream
import software.amazon.awssdk.services.s3.model.S3Exception

val pageSizes: ZStream[S3, S3Exception, Int] =
  ZStream
    .fromZIO(listObjects("docs", ListObjectOptions.fromMaxKeys(100)))
    .flatMap(paginate)
    .map(_.objectSummaries.size)
```

## Know the Limits of Paging

`getNextObjects` requests each page after the first with the bucket name, the prefix, and the continuation token. It does not send the `delimiter`, `maxKeys` or `starAfter` options again. Two things follow: later pages use the default size of 1000 objects, not the `maxKeys` of the first request, and when you list with a delimiter, later pages can include keys that the first page grouped. Avoid a delimiter when you need every page to be consistent, or list the prefixes you need one at a time.

The stub does not return correct pages, as described in the [testing how-to](./testing.md). Check paging behavior against a MinIO server.
