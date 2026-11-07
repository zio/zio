# Testing

> A stub implementation of s3 storage is provided for testing purpose and use internally a filesystem to simulate s3 storage

```scala
import zio.nio.core.file.{Path => ZPath}
import zio.s3._

// build s3 Layer
val stubS3: ZLayer[Any, Nothing, S3] = stub(ZPath("/tmp/s3-data"))

// list all buckets available by using S3 Stub Layer 
// will list all directories of `/tmp/s3-data`
listBuckets.provideLayer(stubS3) 
```

More information here on how to use [ZLayer https://zio.dev/docs/howto/howto_use_layers](https://zio.dev/docs/howto/howto_use_layers)
