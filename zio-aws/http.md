# HTTP

> By default the AWS Java SDK uses _netty_ under the hood to make the HTTP client calls. `zio-aws` defines the http client
as a _layer_ (`HttpClient`) that has to be provided to the _AWS configuration layer_.

# HTTP implementations

By default the AWS Java SDK uses _netty_ under the hood to make the HTTP client calls. `zio-aws` defines the http client
as a _layer_ (`HttpClient`) that has to be provided to the _AWS configuration layer_.

Currently the following implementations can be used:
- `zio-aws-netty` contains the default netty implementation packed as a layer
- `zio-aws-akka-http` is based on Matthias Lüneberg's [aws-spi-akka-http library](https://github.com/matsluni/aws-spi-akka-http)
- `zio-aws-http4s` is an implementation on top of _http4s_
- `zio-aws-crt-http` is an implementation on top of the _AWS Common Runtime_ (CRT) HTTP client

## Netty
The default HTTP implementation in the AWS Java SDK is _Netty_. To use it with the default settings, use the `netty.default`
layer to provide the `HttpClient` for the `AwsConfig` layer. It is also possible to customize the `NettyNioAsyncHttpClient`
directly by manipulation it's `Builder`, by using the `netty.customized(customization)` layer.

The recommended way for configuration is to use the zio-config support:

```scala
def configured(
      tlsKeyManagersProvider: Option[TlsKeyManagersProvider] = None,
      tlsTrustManagersProvider: Option[TlsTrustManagersProvider] = None
  ): ZLayer[ZConfig[NettyClientConfig], Throwable, HttpClient]
```

Everything except the TLS key and trust managers are described by the zio-config provided `NettyClientConfig` data structure.
See the following table for all the options:

## Configuration Details

|FieldName|Format                     |Description|Sources|
|---      |---                        |---        |---    |
|         |[all-of](fielddescriptions)|           |       |

### Field Descriptions

|FieldName     |Format                            |Description                                 |Sources|
|---           |---                               |---                                         |---    |
|              |[any-one-of](fielddescriptions-11)|                                            |       |
|              |[any-one-of](fielddescriptions-10)|                                            |       |
|              |[any-one-of](fielddescriptions-9) |                                            |       |
|              |[any-one-of](fielddescriptions-8) |                                            |       |
|              |[any-one-of](fielddescriptions-7) |                                            |       |
|              |[any-one-of](fielddescriptions-6) |                                            |       |
|              |[any-one-of](fielddescriptions-5) |                                            |       |
|              |[any-one-of](fielddescriptions-4) |                                            |       |
|              |[any-one-of](fielddescriptions-3) |                                            |       |
|              |[any-one-of](fielddescriptions-2) |                                            |       |
|              |[any-one-of](fielddescriptions-1) |                                            |       |
|sslProvider   |primitive                         |a text property, The SSL provider to be used|       |
|[proxy](proxy)|[all-of](proxy)                   |Proxy configuration                         |       |
|[http2](http2)|[all-of](http2)                   |HTTP/2 specific options                     |       |

### Field Descriptions

|FieldName     |Format   |Description                                                       |Sources|
|---           |---      |---                                                               |---    |
|maxConcurrency|primitive|an integer property, Maximum number of allowed concurrent requests|       |
|              |primitive|a constant property, Maximum number of allowed concurrent requests|       |

### Field Descriptions

|FieldName                   |Format   |Description                                                        |Sources|
|---                         |---      |---                                                                |---    |
|maxPendingConnectionAcquires|primitive|an integer property, The maximum number of pending acquires allowed|       |
|                            |primitive|a constant property, The maximum number of pending acquires allowed|       |

### Field Descriptions

|FieldName  |Format   |Description                                                           |Sources|
|---        |---      |---                                                                   |---    |
|readTimeout|primitive|a duration property, The amount of time to wait for a read on a socket|       |
|           |primitive|a constant property, The amount of time to wait for a read on a socket|       |

### Field Descriptions

|FieldName   |Format   |Description                                                            |Sources|
|---         |---      |---                                                                    |---    |
|writeTimeout|primitive|a duration property, The amount of time to wait for a write on a socket|       |
|            |primitive|a constant property, The amount of time to wait for a write on a socket|       |

### Field Descriptions

|FieldName        |Format   |Description                                                                                              |Sources|
|---              |---      |---                                                                                                      |---    |
|connectionTimeout|primitive|a duration property, The amount of time to wait when initially establishing a connection before giving up|       |
|                 |primitive|a constant property, The amount of time to wait when initially establishing a connection before giving up|       |

### Field Descriptions

|FieldName                   |Format   |Description                                                                                               |Sources|
|---                         |---      |---                                                                                                       |---    |
|connectionAcquisitionTimeout|primitive|a duration property, The amount of time to wait when acquiring a connection from the pool before giving up|       |
|                            |primitive|a constant property, The amount of time to wait when acquiring a connection from the pool before giving up|       |

### Field Descriptions

|FieldName           |Format   |Description                                                                                                                      |Sources|
|---                 |---      |---                                                                                                                              |---    |
|connectionTimeToLive|primitive|a duration property, The maximum amount of time that a connection should be allowed to remain open, regardless of usage frequency|       |
|                    |primitive|a constant property, The maximum amount of time that a connection should be allowed to remain open, regardless of usage frequency|       |

### Field Descriptions

|FieldName            |Format   |Description                                                                                              |Sources|
|---                  |---      |---                                                                                                      |---    |
|connectionMaxIdleTime|primitive|a duration property, Maximum amount of time that a connection should be allowed to remain open while idle|       |
|                     |primitive|a constant property, Maximum amount of time that a connection should be allowed to remain open while idle|       |

### Field Descriptions

|FieldName              |Format   |Description                                                                    |Sources|
|---                    |---      |---                                                                            |---    |
|useIdleConnectionReaper|primitive|a boolean property, If true, the idle connections in the pool should be closed |       |
|                       |primitive|a constant property, If true, the idle connections in the pool should be closed|       |

### Field Descriptions

|FieldName|Format   |Description                                    |Sources|
|---      |---      |---                                            |---    |
|protocol |primitive|a text property, HTTP/1.1 or HTTP/2 or Dual    |       |
|         |primitive|a constant property, HTTP/1.1 or HTTP/2 or Dual|       |

### Field Descriptions

|FieldName                       |Format                  |Description                                      |Sources|
|---                             |---                     |---                                              |---    |
|[channelOptions](channeloptions)|[all-of](channeloptions)|Custom Netty channel options                     |       |
|                                |primitive               |a constant property, Custom Netty channel options|       |

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

|FieldName|Format                            |Description                           |Sources|
|---      |---                               |---                                   |---    |
|         |[any-one-of](fielddescriptions-13)|                                      |       |
|host     |primitive                         |a text property, Hostname of the proxy|       |
|port     |primitive                         |an integer property, Port of the proxy|       |
|         |[any-one-of](fielddescriptions-12)|                                      |       |

### Field Descriptions

|FieldName|Format   |Description                          |Sources|
|---      |---      |---                                  |---    |
|scheme   |primitive|a text property, The proxy scheme    |       |
|         |primitive|a constant property, The proxy scheme|       |

### Field Descriptions

|FieldName    |Format   |Description                                          |Sources|
|---          |---      |---                                                  |---    |
|nonProxyHosts|list     |a text property, Hosts that should not be proxied    |       |
|             |primitive|a constant property, Hosts that should not be proxied|       |

### http2

|FieldName        |Format                            |Description                                                         |Sources|
|---              |---                               |---                                                                 |---    |
|maxStreams       |primitive                         |an integer property, Max number of concurrent streams per connection|       |
|initialWindowSize|primitive                         |an integer property, Initial window size of a stream                |       |
|                 |[any-one-of](fielddescriptions-12)|                                                                    |       |

### Field Descriptions

|FieldName            |Format   |Description                                                                                       |Sources|
|---                  |---      |---                                                                                               |---    |
|healthCheckPingPeriod|primitive|a duration property, The period that the Netty client will send PING frames to the remote endpoint|       |
|                     |primitive|a constant property, The period that the Netty client will send PING frames to the remote endpoint|       |

## Akka HTTP
The _Akka HTTP implementation_ can be chosen by using the `akkahttp.client()` layer for providing `HttpClient` to `AwsConfig`.
This implementation uses the [standard akka-http settings](https://doc.akka.io/docs/akka-http/current/configuration.html) from the application's _Lightbend config_,
it is not described with zio-config descriptors.

## http4s
Another alternative is the _http4s client_. To use the default settings, provide the `http4s.default` layer to `AwsConfig`. Customization by manipulating the builder
is also possible by `http4s.customized(customization)`. And similarly to the _Netty_ client, configuration is also possible via zio-config:

## Configuration Details

|FieldName|Format                     |Description|Sources|
|---      |---                        |---        |---    |
|         |[all-of](fielddescriptions)|           |       |

### Field Descriptions

|FieldName|Format                            |Description|Sources|
|---      |---                               |---        |---    |
|         |[any-one-of](fielddescriptions-15)|           |       |
|         |[any-one-of](fielddescriptions-14)|           |       |
|         |[any-one-of](fielddescriptions-13)|           |       |
|         |[any-one-of](fielddescriptions-12)|           |       |
|         |[any-one-of](fielddescriptions-11)|           |       |
|         |[any-one-of](fielddescriptions-10)|           |       |
|         |[any-one-of](fielddescriptions-9) |           |       |
|         |[any-one-of](fielddescriptions-8) |           |       |
|         |[any-one-of](fielddescriptions-7) |           |       |
|         |[any-one-of](fielddescriptions-6) |           |       |
|         |[any-one-of](fielddescriptions-5) |           |       |
|         |[any-one-of](fielddescriptions-4) |           |       |
|         |[any-one-of](fielddescriptions-3) |           |       |
|         |[any-one-of](fielddescriptions-2) |           |       |
|         |[any-one-of](fielddescriptions-1) |           |       |

### Field Descriptions

|FieldName            |Format   |Description                                                               |Sources|
|---                  |---      |---                                                                       |---    |
|responseHeaderTimeout|primitive|a duration property, Timeout for receiving the header part of the response|       |
|                     |primitive|a constant property, Timeout for receiving the header part of the response|       |

### Field Descriptions

|FieldName  |Format   |Description                                                    |Sources|
|---        |---      |---                                                            |---    |
|idleTimeout|primitive|a duration property, Timeout for client connection staying idle|       |
|           |primitive|a constant property, Timeout for client connection staying idle|       |

### Field Descriptions

|FieldName     |Format   |Description                                       |Sources|
|---           |---      |---                                               |---    |
|requestTimeout|primitive|a duration property, Timeout for the whole request|       |
|              |primitive|a constant property, Timeout for the whole request|       |

### Field Descriptions

|FieldName     |Format   |Description                                              |Sources|
|---           |---      |---                                                      |---    |
|connectTimeout|primitive|a duration property, Timeout for connecting to the server|       |
|              |primitive|a constant property, Timeout for connecting to the server|       |

### Field Descriptions

|FieldName|Format   |Description                                              |Sources|
|---      |---      |---                                                      |---    |
|userAgent|primitive|a text property, User-Agent header sent by the client    |       |
|         |primitive|a constant property, User-Agent header sent by the client|       |

### Field Descriptions

|FieldName          |Format   |Description                                                |Sources|
|---                |---      |---                                                        |---    |
|maxTotalConnections|primitive|an integer property, Maximum number of parallel connections|       |
|                   |primitive|a constant property, Maximum number of parallel connections|       |

### Field Descriptions

|FieldName        |Format   |Description                                             |Sources|
|---              |---      |---                                                     |---    |
|maxWaitQueueLimit|primitive|an integer property, Maximum number of requests in queue|       |
|                 |primitive|a constant property, Maximum number of requests in queue|       |

### Field Descriptions

|FieldName                  |Format   |Description                              |Sources|
|---                        |---      |---                                      |---    |
|checkEndpointIdentification|primitive|a boolean property, Check https identity |       |
|                           |primitive|a constant property, Check https identity|       |

### Field Descriptions

|FieldName          |Format   |Description                                                    |Sources|
|---                |---      |---                                                            |---    |
|maxResponseLineSize|primitive|an integer property, Maximum line length of headers in response|       |
|                   |primitive|a constant property, Maximum line length of headers in response|       |

### Field Descriptions

|FieldName      |Format   |Description                                                      |Sources|
|---            |---      |---                                                              |---    |
|maxHeaderLength|primitive|an integer property, Maximum total length of the response headers|       |
|               |primitive|a constant property, Maximum total length of the response headers|       |

### Field Descriptions

|FieldName   |Format   |Description                            |Sources|
|---         |---      |---                                    |---    |
|maxChunkSize|primitive|an integer property, Maximum chunk size|       |
|            |primitive|a constant property, Maximum chunk size|       |

### Field Descriptions

|FieldName         |Format   |Description                                          |Sources|
|---               |---      |---                                                  |---    |
|chunkBufferMaxSize|primitive|an integer property, Maximum size of the chunk buffer|       |
|                  |primitive|a constant property, Maximum size of the chunk buffer|       |

### Field Descriptions

|FieldName |Format   |Description                                        |Sources|
|---       |---      |---                                                |---    |
|parserMode|primitive|a text property, Parser mode, strict or lenient    |       |
|          |primitive|a constant property, Parser mode, strict or lenient|       |

### Field Descriptions

|FieldName |Format   |Description                     |Sources|
|---       |---      |---                             |---    |
|bufferSize|primitive|an integer property, Buffer size|       |
|          |primitive|a constant property, Buffer size|       |

### Field Descriptions

|FieldName                       |Format                  |Description                                      |Sources|
|---                             |---                     |---                                              |---    |
|[channelOptions](channeloptions)|[all-of](channeloptions)|Collection of socket options                     |       |
|                                |primitive               |a constant property, Collection of socket options|       |

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

## AWS CRT HTTP
The new _AWS Common Runtime_ (CRT) HTTP client can be used by providing the `crt.default` layer for providing `HttpClient` to `AwsConfig`.

## Configuration Details

|FieldName|Format                     |Description|Sources|
|---      |---                        |---        |---    |
|         |[all-of](fielddescriptions)|           |       |

### Field Descriptions

|FieldName                           |Format                    |Description                                                                                                                                                                                                                                                                                                                                  |Sources|
|---                                 |---                       |---                                                                                                                                                                                                                                                                                                                                          |---    |
|maxConcurrency                      |primitive                 |an integer property, The Maximum number of allowed concurrent requests. For HTTP/1.1 this is the same as max connections.                                                                                                                                                                                                                    |       |
|readBufferSizeInBytes               |primitive                 |an integer property, Configures the number of unread bytes that can be buffered in the client before we stop reading from the underlying TCP socket and wait for the Subscriber to read more data.                                                                                                                                           |       |
|[proxy](proxy)                      |[all-of](proxy)           |Sets the http proxy configuration to use for this client.                                                                                                                                                                                                                                                                                    |       |
|[connectionHealth](connectionhealth)|[all-of](connectionhealth)|Configure the health checks for all connections established by this client.                                                                                                                                                                                                                                                                  |       |
|connectionMaxIdleTime               |primitive                 |a duration property, Configure the maximum amount of time that a connection should be allowed to remain open while idle.                                                                                                                                                                                                                     |       |
|connectionTimeout                   |primitive                 |a duration property, The amount of time to wait when initially establishing a connection before giving up and timing out.                                                                                                                                                                                                                    |       |
|[tcpKeepAlive](tcpkeepalive)        |[all-of](tcpkeepalive)    |Configure whether to enable tcpKeepAlive and relevant configuration for all connections established by this client.                                                                                                                                                                                                                          |       |
|postQuantumTlsEnabled               |primitive                 |a boolean property, Configure whether to enable a hybrid post-quantum key exchange option for the Transport Layer Security (TLS) network encryption protocol when communicating with services that support Post Quantum TLS. If Post Quantum cipher suites are not supported on the platform, the SDK will use the default TLS cipher suites.|       |

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
