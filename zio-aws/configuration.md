# Configuration

> Each _service module_ depends on the `AwsConfig` layer. This layer is responsible for setting up the 
AWS Java SDK's async client, by setting the [underlying HTTP engine](http.md) and all the common
settings. You can use the following layers to provide `AwsConfig`:

# Configuration

## Common configuration

Each _service module_ depends on the `AwsConfig` layer. This layer is responsible for setting up the 
AWS Java SDK's async client, by setting the [underlying HTTP engine](http.md) and all the common
settings. You can use the following layers to provide `AwsConfig`:

#### Default
`AwsConfig.default` requires a `HttpClient` as dependency, but does not customize any other setting of the client

#### Fully customized
`AwsConfig.customized(customization)` gives the freedom to customize the creation of the AWS async client directly by modifying it's `Builder`

#### Configured
`AwsConfig.configured()` is the *recommended* way to construct an `AwsConfig`. Beside requiring a `HttpClient` it also has `ZConfig[CommonAwsConfig]` as dependency.
The `CommonAwsConfig` value can be either provided from code for example by `ZLayer.succeed(CommonAwsConfig(...))` or it can
be read from any of the supported config sources by [zio-config](https://zio.dev/zio-config/).

Note that **AWS level retries are disabled** by the configuration layer and it is not exposed in the `CommonAwsConfig` data structure either. The reason for this is that the recommended way to handle retries is to use [aspects on the service layers](aspects.md).
 
See the following table about the possible configuration values. Please note that the underlying HTTP engine also has its own
specific configuration which is described [on the page about the HTTP engines](http.md). 

## Configuration Details

|FieldName|Format                     |Description|Sources|
|---      |---                        |---        |---    |
|         |[all-of](fielddescriptions)|           |       |

### Field Descriptions

|FieldName       |Format                           |Description                                        |Sources|
|---             |---                              |---                                                |---    |
|region          |primitive                        |a text property, AWS region to connect to          |       |
|                |[any-one-of](fielddescriptions-1)|                                                   |       |
|endpointOverride|primitive                        |a text property, Overrides the AWS service endpoint|       |
|[client](client)|[all-of](client)                 |Common settings for AWS service clients            |       |

### Field Descriptions

|FieldName|Format   |Description                                  |Sources|
|---      |---      |---                                          |---    |
|type     |map      |a text property, AWS credentials provider    |       |
|         |primitive|a constant property, AWS credentials provider|       |

### client

|FieldName                   |Format              |Description                                                                                     |Sources|
|---                         |---                 |---                                                                                             |---    |
|[extraHeaders](extraheaders)|[list](extraheaders)|Extra headers to be sent with each request                                                      |       |
|apiCallTimeout              |primitive           |a duration property, Amount of time to allow the client to complete the execution of an API call|       |
|apiCallAttemptTimeout       |primitive           |a duration property, Amount of time to wait for the HTTP request to complete before giving up   |       |
|defaultProfileName          |primitive           |a text property, Default profile name                                                           |       |

### extraHeaders

|FieldName|Format   |Description                  |Sources|
|---      |---      |---                          |---    |
|name     |primitive|a text property, Header name |       |
|value    |list     |a text property, Header value|       |

## Service layer
Each AWS service's generated client has it own layer that depends on `AwsConfig`. It is possible to reuse the same `AwsConfig` layer
for multiple AWS service clients, sharing a common configuration. Usually the service client does not require any additional configuration,
in this case the `live` layer can be used, for example:

```scala
program.provide(
    awsConfig,
    Ec2.live,
    ElasticBeanstalk.live
)
```
