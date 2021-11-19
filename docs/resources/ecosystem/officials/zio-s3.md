---
id: zio-s3
title: "ZIO S3"
---

[ZIO S3](https://github.com/zio/zio-s3) is an S3 client for ZIO.

## Installation

In order to use this library, we need to add the following line in our `build.sbt` file:

```scala
libraryDependencies += "dev.zio" %% "zio-s3" % "0.3.5" 
```

## Example

Let's try an example of creating a bucket and adding an object into it. To run this example, we need to run an instance of _Minio_ which is object storage compatible with S3:

```bash
docker run -p 9000:9000 -e MINIO_ACCESS_KEY=MyKey -e MINIO_SECRET_KEY=MySecret minio/minio  server --compat /data
```

In this example we create a bucket and then add a JSON object to it and then retrieve that:

```scala
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.regions.Region
import zio.console.putStrLn
import zio.s3._
import zio.stream.{ZStream, ZTransducer}
import zio.{Chunk, ExitCode, URIO}

import java.net.URI

object ZIOS3Example extends zio.App {

  val myApp = for {
    _ <- createBucket("docs")
    json = Chunk.fromArray("""{  "id" : 1 , "name" : "A1" }""".getBytes)
    _ <- putObject(
      bucketName = "docs",
      key = "doc1",
      contentLength = json.length,
      content = ZStream.fromChunk(json),
      options = UploadOptions.fromContentType("application/json")
    )
    _ <- getObject("docs", "doc1")
      .transduce(ZTransducer.utf8Decode)
      .foreach(putStrLn(_))
  } yield ()

  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] =
    myApp
      .provideCustom(
        live(
          Region.CA_CENTRAL_1,
          AwsBasicCredentials.create("MyKey", "MySecret"),
          Some(URI.create("http://localhost:9000"))
        )
      )
      .exitCode
}
```
