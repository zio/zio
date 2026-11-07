# Credentials

> zio-s3 expose credentials providers from aws https://docs.aws.amazon.com/sdk-for-java/v2/developer-guide/credentials.html
> If credentials cannot be found in one or multiple providers selected the operation will fail with `InvalidCredentials`

```scala
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import zio._
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.model.S3Exception
import zio.s3._
import zio.s3.providers._

// build S3 Layer from basic credentials
val s3: Layer[S3Exception, S3] =
  live(Region.AF_SOUTH_1, AwsBasicCredentials.create("key", "secret"))

// build S3 Layer from System properties or Environment variables
val s3: Layer[S3Exception, S3] =
  liveZIO(Region.AF_SOUTH_1, system <> env)

// build S3 Layer  from Instance profile credentials
val s3: Layer[S3Exception, S3] =
  liveZIO(Region.AF_SOUTH_1, instanceProfile)

// build S3 Layer from web identity token credentials with STS. awssdk sts module required to be on classpath
val s3: Layer[S3Exception, S3] = liveZIO(Region.AF_SOUTH_1, webIdentity)

// build S3 Layer from default available credentials providers
val s3: Layer[S3Exception, S3] = liveZIO(Region.AF_SOUTH_1, default)

// use custom logic to fetch aws credentials
val zcredentials: ZIO[R, S3Exception, AwsCredentials] = ??? // specific implementation to fetch credentials
val s3: ZLayer[Any, S3Exception, S3] = settings(Region.AF_SOUTH_1, zcredentials) >>> live
```
