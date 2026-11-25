# Getting Started

> This page runs a first ZIO S3 program: it creates a bucket, stores a JSON object in it, and reads the object back. We use a local MinIO server, an object storage server compatible with the S3 API, so we need no AWS account. Add the dependency from the [installation section](./index.md#installation) first.

## Start a Local S3 Server

To start MinIO with the credentials that this page uses, run:

```bash
docker run -p 9000:9000 -e MINIO_ROOT_USER=MyKey -e MINIO_ROOT_PASSWORD=MySecret minio/minio server --compat /data
```

## Write the Program

The program calls `createBucket`, `putObject` and `getObject`. Each call needs an `S3` service, which `live` builds from a region, credentials and the server address. MinIO requires path-style requests, so we set `forcePathStyle` to `Some(true)`:

```scala
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.regions.Region
import zio._
import zio.s3._
import zio.stream.{ ZPipeline, ZStream }

import java.net.URI

object ZIOS3Example extends ZIOAppDefault {

  val json: Chunk[Byte] = Chunk.fromArray("""{  "id" : 1 , "name" : "A1" }""".getBytes)

  val myApp: ZIO[S3, Throwable, Unit] = for {
    _ <- createBucket("docs")
    _ <- putObject(
           bucketName = "docs",
           key = "doc1",
           contentLength = json.length.toLong,
           content = ZStream.fromChunk(json),
           options = UploadOptions.fromContentType("application/json")
         )
    _ <- getObject("docs", "doc1")
           .via(ZPipeline.utf8Decode)
           .foreach(Console.printLine(_))
  } yield ()

  def run =
    myApp.provide(
      live(
        Region.CA_CENTRAL_1,
        AwsBasicCredentials.create("MyKey", "MySecret"),
        Some(URI.create("http://localhost:9000")),
        forcePathStyle = Some(true) // MinIO requires path-style requests
      )
    )
}
```

When the program runs, it prints the JSON text that it stored.

## Next Steps

- To learn the operations step by step without a server, follow the [tutorial](./tutorial.md).
- To use AWS instead of MinIO, see [credentials and region](./credentials.md).
- To look up every operation, see the [operations reference](./s3-operations.md).
- To upload large files, see [upload a large file in parts](./upload-a-large-file-in-parts.md). To read objects, see [download an object](./download-an-object.md).
