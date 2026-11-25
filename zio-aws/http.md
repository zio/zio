# HTTP Clients

> Reference for the four HTTP client modules that ZIO AWS can use, with their factory functions, layer requirements and configuration keys.

ZIO AWS sends every request through an `HttpClient` layer, which the `AwsConfig` layer depends on. Each HTTP client is its own module, and each module provides one or more factory functions that build an `HttpClient` layer. The [Choose an HTTP Client](./guide-choose-http-client.md) guide explains how to pick one.

## HttpClient

`HttpClient` is a trait in `zio.aws.core.httpclient`. When a service builds its AWS SDK client, `AwsConfig` asks the `HttpClient` for an SDK HTTP client that fits the service:

```scala
trait HttpClient {
  def clientFor(serviceCaps: ServiceHttpCapabilities): Task[SdkAsyncHttpClient]
}

case class ServiceHttpCapabilities(supportsHttp2: Boolean)
```

`supportsHttp2` is `true` for the services generated with HTTP/2 support, `kinesis` and `transcribestreaming`, and `false` for the rest. The `Protocol` type, with the cases `Protocol.Http11`, `Protocol.Http2` and `Protocol.Dual`, chooses how a client answers:

| Protocol          | HTTP/2 services | Other services                             |
| ----------------- | --------------- | ------------------------------------------ |
| `Protocol.Http11` | HTTP/1.1 client | HTTP/1.1 client                            |
| `Protocol.Http2`  | HTTP/2 client   | fails with `UnsupportedOperationException` |
| `Protocol.Dual`   | HTTP/2 client   | HTTP/1.1 client                            |

Only the Netty module takes a `Protocol`. The http4s, Akka HTTP and AWS CRT modules return the same client for every service.

The following modules are available:

- `zio-aws-netty` uses Netty, which is the HTTP client the AWS SDK uses by default
- `zio-aws-http4s` uses the Blaze client from http4s
- `zio-aws-akka-http` uses Akka HTTP, through the [aws-spi-akka-http library](https://github.com/matsluni/aws-spi-akka-http)
- `zio-aws-crt-http` uses the AWS Common Runtime (CRT) client

## Netty

The object `zio.aws.netty.NettyHttpClient` provides these factories:

```scala
val default: ZLayer[Any, Throwable, HttpClient]
val dual: ZLayer[Any, Throwable, HttpClient]
def customized(protocol: Protocol, customization: NettyNioAsyncHttpClient.Builder => NettyNioAsyncHttpClient.Builder = identity): ZLayer[Any, Throwable, HttpClient]
def configured(tlsKeyManagersProvider: Option[TlsKeyManagersProvider] = None, tlsTrustManagersProvider: Option[TlsTrustManagersProvider] = None): ZLayer[NettyClientConfig, Throwable, HttpClient]
```

`default` uses HTTP/1.1. `dual` uses HTTP/2 for the services that support it and HTTP/1.1 for the rest. `customized` takes the `Protocol` (`Protocol.Http11`, `Protocol.Http2` or `Protocol.Dual`) and a function that changes the Netty builder. `configured` reads the settings from a `NettyClientConfig` layer, which you can load with `ZIO.config(nettyClientConfig)` from `zio.aws.netty.descriptors`. Only the TLS key and trust manager providers are passed as parameters, because they cannot be described in configuration.

The configuration keys are listed in the following table:

## Configuration Details

|FieldName|Format                       |Description|Sources|
|---      |---                          |---        |---    |
|         |all-of|           |       |

#### Field Descriptions

|FieldName      |Format                              |Description                                 |Sources|
|---            |---                                 |---                                         |---    |
|               |any-one-of|                                            |       |
|               |any-one-of|                                            |       |
|               |any-one-of |                                            |       |
|               |any-one-of |                                            |       |
|               |any-one-of |                                            |       |
|               |any-one-of |                                            |       |
|               |any-one-of |                                            |       |
|               |any-one-of |                                            |       |
|               |any-one-of |                                            |       |
|               |any-one-of |                                            |       |
|               |any-one-of |                                            |       |
|sslProvider    |primitive                           |a text property, The SSL provider to be used|       |
|proxy|all-of                    |Proxy configuration                         |       |
|http2|all-of                    |HTTP/2 specific options                     |       |

#### Field Descriptions

|FieldName     |Format   |Description                                                       |Sources|
|---           |---      |---                                                               |---    |
|maxConcurrency|primitive|an integer property, Maximum number of allowed concurrent requests|       |
|              |primitive|a constant property, Maximum number of allowed concurrent requests|       |

#### Field Descriptions

|FieldName                   |Format   |Description                                                        |Sources|
|---                         |---      |---                                                                |---    |
|maxPendingConnectionAcquires|primitive|an integer property, The maximum number of pending acquires allowed|       |
|                            |primitive|a constant property, The maximum number of pending acquires allowed|       |

#### Field Descriptions

|FieldName  |Format   |Description                                                           |Sources|
|---        |---      |---                                                                   |---    |
|readTimeout|primitive|a duration property, The amount of time to wait for a read on a socket|       |
|           |primitive|a constant property, The amount of time to wait for a read on a socket|       |

#### Field Descriptions

|FieldName   |Format   |Description                                                            |Sources|
|---         |---      |---                                                                    |---    |
|writeTimeout|primitive|a duration property, The amount of time to wait for a write on a socket|       |
|            |primitive|a constant property, The amount of time to wait for a write on a socket|       |

#### Field Descriptions

|FieldName        |Format   |Description                                                                                              |Sources|
|---              |---      |---                                                                                                      |---    |
|connectionTimeout|primitive|a duration property, The amount of time to wait when initially establishing a connection before giving up|       |
|                 |primitive|a constant property, The amount of time to wait when initially establishing a connection before giving up|       |

#### Field Descriptions

|FieldName                   |Format   |Description                                                                                               |Sources|
|---                         |---      |---                                                                                                       |---    |
|connectionAcquisitionTimeout|primitive|a duration property, The amount of time to wait when acquiring a connection from the pool before giving up|       |
|                            |primitive|a constant property, The amount of time to wait when acquiring a connection from the pool before giving up|       |

#### Field Descriptions

|FieldName           |Format   |Description                                                                                                                      |Sources|
|---                 |---      |---                                                                                                                              |---    |
|connectionTimeToLive|primitive|a duration property, The maximum amount of time that a connection should be allowed to remain open, regardless of usage frequency|       |
|                    |primitive|a constant property, The maximum amount of time that a connection should be allowed to remain open, regardless of usage frequency|       |

#### Field Descriptions

|FieldName            |Format   |Description                                                                                              |Sources|
|---                  |---      |---                                                                                                      |---    |
|connectionMaxIdleTime|primitive|a duration property, Maximum amount of time that a connection should be allowed to remain open while idle|       |
|                     |primitive|a constant property, Maximum amount of time that a connection should be allowed to remain open while idle|       |

#### Field Descriptions

|FieldName              |Format   |Description                                                                    |Sources|
|---                    |---      |---                                                                            |---    |
|useIdleConnectionReaper|primitive|a boolean property, If true, the idle connections in the pool should be closed |       |
|                       |primitive|a constant property, If true, the idle connections in the pool should be closed|       |

#### Field Descriptions

|FieldName|Format   |Description                                    |Sources|
|---      |---      |---                                            |---    |
|protocol |primitive|a text property, HTTP/1.1 or HTTP/2 or Dual    |       |
|         |primitive|a constant property, HTTP/1.1 or HTTP/2 or Dual|       |

#### Field Descriptions

|FieldName                        |Format                   |Description                                      |Sources|
|---                              |---                      |---                                              |---    |
|channelOptions|all-of|Custom Netty channel options                     |       |
|                                 |primitive                |a constant property, Custom Netty channel options|       |

### channelOptions

|FieldName                     |Format   |Description                                                             |Sources|
|---                           |---      |---                                                                     |---    |
|SO_BROADCAST                  |primitive|a boolean property, Allow transmission of broadcast datagrams           |       |
|SO_KEEPALIVE                  |primitive|a boolean property, Keep connection alive                               |       |
|SO_SNDBUF                     |primitive|an integer property, The size of the socket send buffer                 |       |
|SO_RCVBUF                     |primitive|an integer property, The size of the socket receive buffer              |       |
|SO_REUSEADDR                  |primitive|a boolean property, Re-use address                                      |       |
|SO_LINGER                     |primitive|an integer property, Linger on close if data is present                 |       |
|IP_TOS                        |primitive|an integer property, The ToS octet in the IP header                     |       |
|IP_MULTICAST_IF               |primitive|a text property, The network interface's name for IP multicast datagrams|       |
|IP_MULTICAST_TTL              |primitive|an integer property, The time-to-live for IP multicast datagrams        |       |
|IP_MULTICAST_LOOP             |primitive|a boolean property, Loopback for IP multicast datagrams                 |       |
|TCP_NODELAY                   |primitive|a boolean property, Disable the Nagle algorithm                         |       |
|CONNECT_TIMEOUT_MILLIS        |primitive|a duration property, Connect timeout                                    |       |
|WRITE_SPIN_COUNT              |primitive|an integer property, Write spin count                                   |       |
|ALLOW_HALF_CLOSURE            |primitive|a boolean property, Allow half closure                                  |       |
|AUTO_READ                     |primitive|a boolean property, Auto read                                           |       |
|AUTO_CLOSE                    |primitive|a boolean property, Auto close                                          |       |
|SINGLE_EVENTEXECUTOR_PER_GROUP|primitive|a boolean property, Single event executor per group                     |       |

### proxy

|FieldName|Format                              |Description                           |Sources|
|---      |---                                 |---                                   |---    |
|         |any-one-of|                                      |       |
|host     |primitive                           |a text property, Hostname of the proxy|       |
|port     |primitive                           |an integer property, Port of the proxy|       |
|         |any-one-of|                                      |       |

#### Field Descriptions

|FieldName|Format   |Description                          |Sources|
|---      |---      |---                                  |---    |
|scheme   |primitive|a text property, The proxy scheme    |       |
|         |primitive|a constant property, The proxy scheme|       |

#### Field Descriptions

|FieldName    |Format   |Description                                          |Sources|
|---          |---      |---                                                  |---    |
|nonProxyHosts|list     |a text property, Hosts that should not be proxied    |       |
|             |primitive|a constant property, Hosts that should not be proxied|       |

### http2

|FieldName        |Format                              |Description                                                         |Sources|
|---              |---                                 |---                                                                 |---    |
|maxStreams       |primitive                           |an integer property, Max number of concurrent streams per connection|       |
|initialWindowSize|primitive                           |an integer property, Initial window size of a stream                |       |
|                 |any-one-of|                                                                    |       |

#### Field Descriptions

|FieldName            |Format   |Description                                                                                       |Sources|
|---                  |---      |---                                                                                               |---    |
|healthCheckPingPeriod|primitive|a duration property, The period that the Netty client will send PING frames to the remote endpoint|       |
|                     |primitive|a constant property, The period that the Netty client will send PING frames to the remote endpoint|       |

## Akka HTTP

The object `zio.aws.akkahttp.AkkaHttpClient` provides the `client` factory:

```scala
def client(
    connectionPoolSettings: Option[ConnectionPoolSettings] = None,
    executionContext: Option[ExecutionContext] = None
): ZLayer[ActorSystem, Throwable, HttpClient]
```

The layer needs an `ActorSystem` in its environment. The client uses the [standard akka-http settings](https://doc.akka.io/docs/akka-http/current/configuration.html) from the application's Lightbend config, and it is not described with ZIO Config descriptors.

## http4s

The object `zio.aws.http4s.Http4sClient` provides these factories:

```scala
val default: ZLayer[Any, Throwable, HttpClient]
def customized(f: BlazeClientBuilder[Task] => BlazeClientBuilder[Task]): ZLayer[Any, Throwable, HttpClient]
def configured(
    sslContext: Option[SSLContext] = tryDefaultSslContext,
    maxConnectionsPerRequestKey: Option[RequestKey => Int] = None,
    asynchronousChannelGroup: Option[AsynchronousChannelGroup] = None,
    additionalSocketOptions: Seq[OptionValue[_]] = Seq.empty
): ZLayer[BlazeClientConfig, Throwable, HttpClient]
```

`default` uses the default Blaze settings. `customized` passes the `BlazeClientBuilder` to your function. `configured` reads the timeouts and other settings from a `BlazeClientConfig` layer, and the function parameters cover the settings that cannot be described in configuration:

## Configuration Details

|FieldName|Format                       |Description|Sources|
|---      |---                          |---        |---    |
|         |all-of|           |       |

#### Field Descriptions

|FieldName|Format                              |Description|Sources|
|---      |---                                 |---        |---    |
|         |any-one-of|           |       |
|         |any-one-of|           |       |
|         |any-one-of|           |       |
|         |any-one-of|           |       |
|         |any-one-of|           |       |
|         |any-one-of|           |       |
|         |any-one-of |           |       |
|         |any-one-of |           |       |
|         |any-one-of |           |       |
|         |any-one-of |           |       |
|         |any-one-of |           |       |
|         |any-one-of |           |       |
|         |any-one-of |           |       |
|         |any-one-of |           |       |
|         |any-one-of |           |       |

#### Field Descriptions

|FieldName            |Format   |Description                                                               |Sources|
|---                  |---      |---                                                                       |---    |
|responseHeaderTimeout|primitive|a duration property, Timeout for receiving the header part of the response|       |
|                     |primitive|a constant property, Timeout for receiving the header part of the response|       |

#### Field Descriptions

|FieldName  |Format   |Description                                                    |Sources|
|---        |---      |---                                                            |---    |
|idleTimeout|primitive|a duration property, Timeout for client connection staying idle|       |
|           |primitive|a constant property, Timeout for client connection staying idle|       |

#### Field Descriptions

|FieldName     |Format   |Description                                       |Sources|
|---           |---      |---                                               |---    |
|requestTimeout|primitive|a duration property, Timeout for the whole request|       |
|              |primitive|a constant property, Timeout for the whole request|       |

#### Field Descriptions

|FieldName     |Format   |Description                                              |Sources|
|---           |---      |---                                                      |---    |
|connectTimeout|primitive|a duration property, Timeout for connecting to the server|       |
|              |primitive|a constant property, Timeout for connecting to the server|       |

#### Field Descriptions

|FieldName|Format   |Description                                              |Sources|
|---      |---      |---                                                      |---    |
|userAgent|primitive|a text property, User-Agent header sent by the client    |       |
|         |primitive|a constant property, User-Agent header sent by the client|       |

#### Field Descriptions

|FieldName          |Format   |Description                                                |Sources|
|---                |---      |---                                                        |---    |
|maxTotalConnections|primitive|an integer property, Maximum number of parallel connections|       |
|                   |primitive|a constant property, Maximum number of parallel connections|       |

#### Field Descriptions

|FieldName        |Format   |Description                                             |Sources|
|---              |---      |---                                                     |---    |
|maxWaitQueueLimit|primitive|an integer property, Maximum number of requests in queue|       |
|                 |primitive|a constant property, Maximum number of requests in queue|       |

#### Field Descriptions

|FieldName                  |Format   |Description                              |Sources|
|---                        |---      |---                                      |---    |
|checkEndpointIdentification|primitive|a boolean property, Check https identity |       |
|                           |primitive|a constant property, Check https identity|       |

#### Field Descriptions

|FieldName          |Format   |Description                                                    |Sources|
|---                |---      |---                                                            |---    |
|maxResponseLineSize|primitive|an integer property, Maximum line length of headers in response|       |
|                   |primitive|a constant property, Maximum line length of headers in response|       |

#### Field Descriptions

|FieldName      |Format   |Description                                                      |Sources|
|---            |---      |---                                                              |---    |
|maxHeaderLength|primitive|an integer property, Maximum total length of the response headers|       |
|               |primitive|a constant property, Maximum total length of the response headers|       |

#### Field Descriptions

|FieldName   |Format   |Description                            |Sources|
|---         |---      |---                                    |---    |
|maxChunkSize|primitive|an integer property, Maximum chunk size|       |
|            |primitive|a constant property, Maximum chunk size|       |

#### Field Descriptions

|FieldName         |Format   |Description                                          |Sources|
|---               |---      |---                                                  |---    |
|chunkBufferMaxSize|primitive|an integer property, Maximum size of the chunk buffer|       |
|                  |primitive|a constant property, Maximum size of the chunk buffer|       |

#### Field Descriptions

|FieldName |Format   |Description                                        |Sources|
|---       |---      |---                                                |---    |
|parserMode|primitive|a text property, Parser mode, strict or lenient    |       |
|          |primitive|a constant property, Parser mode, strict or lenient|       |

#### Field Descriptions

|FieldName |Format   |Description                     |Sources|
|---       |---      |---                             |---    |
|bufferSize|primitive|an integer property, Buffer size|       |
|          |primitive|a constant property, Buffer size|       |

#### Field Descriptions

|FieldName                        |Format                   |Description                                      |Sources|
|---                              |---                      |---                                              |---    |
|channelOptions|all-of|Collection of socket options                     |       |
|                                 |primitive                |a constant property, Collection of socket options|       |

### channelOptions

|FieldName        |Format   |Description                                                             |Sources|
|---              |---      |---                                                                     |---    |
|SO_BROADCAST     |primitive|a boolean property, Allow transmission of broadcast datagrams           |       |
|SO_KEEPALIVE     |primitive|a boolean property, Keep connection alive                               |       |
|SO_SNDBUF        |primitive|an integer property, The size of the socket send buffer                 |       |
|SO_RCVBUF        |primitive|an integer property, The size of the socket receive buffer              |       |
|SO_REUSEADDR     |primitive|a boolean property, Re-use address                                      |       |
|SO_LINGER        |primitive|an integer property, Linger on close if data is present                 |       |
|IP_TOS           |primitive|an integer property, The ToS octet in the IP header                     |       |
|IP_MULTICAST_IF  |primitive|a text property, The network interface's name for IP multicast datagrams|       |
|IP_MULTICAST_TTL |primitive|an integer property, The time-to-live for IP multicast datagrams        |       |
|IP_MULTICAST_LOOP|primitive|a boolean property, Loopback for IP multicast datagrams                 |       |
|TCP_NODELAY      |primitive|a boolean property, Disable the Nagle algorithm                         |       |

## AWS CRT

The object `zio.aws.crt.AwsCrtHttpClient` provides these factories:

```scala
val default: ZLayer[Any, Throwable, HttpClient]
def customized(customization: AwsCrtAsyncHttpClient.Builder => AwsCrtAsyncHttpClient.Builder = identity): ZLayer[Any, Throwable, HttpClient]
def configured(): ZLayer[AwsCrtHttpClientConfig, Throwable, HttpClient]
```

`default` uses the CRT defaults, `customized` changes the CRT builder, and `configured` reads its settings from an `AwsCrtHttpClientConfig` layer:

## Configuration Details

|FieldName|Format                       |Description|Sources|
|---      |---                          |---        |---    |
|         |all-of|           |       |

#### Field Descriptions

|FieldName                            |Format                     |Description                                                                                                                                                                                                                                                                                                                                  |Sources|
|---                                  |---                        |---                                                                                                                                                                                                                                                                                                                                          |---    |
|maxConcurrency                       |primitive                  |an integer property, The Maximum number of allowed concurrent requests. For HTTP/1.1 this is the same as max connections.                                                                                                                                                                                                                    |       |
|readBufferSizeInBytes                |primitive                  |an integer property, Configures the number of unread bytes that can be buffered in the client before we stop reading from the underlying TCP socket and wait for the Subscriber to read more data.                                                                                                                                           |       |
|proxy                      |all-of           |Sets the http proxy configuration to use for this client.                                                                                                                                                                                                                                                                                    |       |
|connectionHealth|all-of|Configure the health checks for all connections established by this client.                                                                                                                                                                                                                                                                  |       |
|connectionMaxIdleTime                |primitive                  |a duration property, Configure the maximum amount of time that a connection should be allowed to remain open while idle.                                                                                                                                                                                                                     |       |
|connectionTimeout                    |primitive                  |a duration property, The amount of time to wait when initially establishing a connection before giving up and timing out.                                                                                                                                                                                                                    |       |
|tcpKeepAlive        |all-of    |Configure whether to enable tcpKeepAlive and relevant configuration for all connections established by this client.                                                                                                                                                                                                                          |       |
|postQuantumTlsEnabled                |primitive                  |a boolean property, Configure whether to enable a hybrid post-quantum key exchange option for the Transport Layer Security (TLS) network encryption protocol when communicating with services that support Post Quantum TLS. If Post Quantum cipher suites are not supported on the platform, the SDK will use the default TLS cipher suites.|       |

### proxy

|FieldName|Format   |Description                                |Sources|
|---      |---      |---                                        |---    |
|scheme   |primitive|a text property, The scheme of the proxy   |       |
|host     |primitive|a text property, The host of the proxy     |       |
|port     |primitive|an integer property, The port of the proxy |       |
|username |primitive|a text property, The username for the proxy|       |
|password |primitive|a text property, The password for the proxy|       |

### connectionHealth

|FieldName               |Format   |Description                                                                                                               |Sources|
|---                     |---      |---                                                                                                                       |---    |
|minimumThroughputInBps  |primitive|an integer property, Sets a throughput threshold for connections. Throughput below this value will be considered unhealthy|       |
|minimumThroughputTimeout|primitive|a duration property, Sets how long a connection is allowed to be unhealthy before getting shut down                       |       |

### tcpKeepAlive

|FieldName        |Format   |Description                                                                                                            |Sources|
|---              |---      |---                                                                                                                    |---    |
|keepAliveInterval|primitive|a duration property, The number of seconds between TCP keepalive packets being sent to the peer                        |       |
|keepAliveTimeout |primitive|a duration property, The number of seconds to wait for a keepalive response before considering the connection timed out|       |

## Custom HTTP Clients

You rarely need this; it is an advanced API for using an `SdkAsyncHttpClient` that has no ZIO AWS module, such as one built by other code in your application. `HttpClient.fromScopedPerProtocol` builds an `HttpClient` layer from two scoped effects, one that creates the HTTP/1.1 client and one that creates the HTTP/2 client, and a `Protocol` that chooses between them:

```scala
object HttpClient {
  def fromScopedPerProtocol[R, E, A <: SdkAsyncHttpClient](
      http11Client: ZIO[R & Scope, E, A],
      http2Client: ZIO[R & Scope, E, A]
  )(protocol: Protocol): ZLayer[R, E, HttpClient]
}
```

The effects only run for the protocols the `Protocol` value uses, and the clients are closed when the layer's scope ends. `HttpClient.fromScopedPerProtocolScoped` returns the same `HttpClient` as a scoped `ZIO` instead of a layer. The Netty module uses this function itself:

```scala
import zio._
import zio.aws.core.httpclient.{HttpClient, Protocol}
import software.amazon.awssdk.http.async.SdkAsyncHttpClient
import software.amazon.awssdk.http.nio.netty.NettyNioAsyncHttpClient
import software.amazon.awssdk.http.{Protocol => SdkProtocol}

def nettyFor(protocol: SdkProtocol): ZIO[Scope, Throwable, SdkAsyncHttpClient] =
  ZIO.fromAutoCloseable(
    ZIO.attempt(NettyNioAsyncHttpClient.builder().protocol(protocol).build())
  )

val myHttpClient: ZLayer[Any, Throwable, HttpClient] =
  HttpClient.fromScopedPerProtocol[Any, Throwable, SdkAsyncHttpClient](
    nettyFor(SdkProtocol.HTTP1_1),
    nettyFor(SdkProtocol.HTTP2)
  )(Protocol.Dual)
```
