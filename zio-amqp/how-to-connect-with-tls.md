# How to Connect with TLS

> This guide shows how to encrypt the connection to a RabbitMQ broker: with the JVM's default trust store, with a custom trust store for a private certificate authority, and with a client certificate. The broker must already listen for TLS, by convention on port `5671`. See the [RabbitMQ TLS guide](https://www.rabbitmq.com/docs/ssl) for the broker side.

All snippets assume these imports:

```scala
import com.rabbitmq.client.{ Connection, ConnectionFactory }
import zio._
import zio.amqp._
import zio.amqp.model._

import java.io.FileInputStream
import java.security.KeyStore
import javax.net.ssl.{ KeyManagerFactory, SSLContext, TrustManagerFactory }
```

## Use the Default Trust Store

If the broker's certificate was issued by an authority that the JVM already trusts, set `ssl = true` on the [`AMQPConfig`](./reference/amqp.md#amqpconfig) and point it at the TLS port. The URI scheme becomes `amqps`, and the RabbitMQ client then uses the JVM's default `SSLContext`:

```scala
val connection: ZIO[Scope, Throwable, Connection] =
  Amqp.connect(AMQPConfig.default.copy(host = "rabbit.example.com", port = 5671, ssl = true, user = "app", password = "secret"))
```

## Trust a Private Certificate Authority

If the broker uses a certificate from your own certificate authority, load that authority into a trust store and build an `SSLContext` from it. Loading a file blocks, so do it inside `ZIO.attemptBlocking`:

```scala
def loadKeyStore(path: String, password: Array[Char]): Task[KeyStore] =
  ZIO.attemptBlocking {
    val keyStore = KeyStore.getInstance("PKCS12")
    val in       = new FileInputStream(path)
    try keyStore.load(in, password)
    finally in.close()
    keyStore
  }

def trustingSslContext(trustStorePath: String, password: Array[Char]): Task[SSLContext] =
  loadKeyStore(trustStorePath, password).map { trustStore =>
    val trustManagers = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm)
    trustManagers.init(trustStore)
    val context       = SSLContext.getInstance("TLS")
    context.init(null, trustManagers.getTrustManagers, null)
    context
  }
```

Give the context to a `ConnectionFactory` with `useSslProtocol` and connect with the factory. `enableHostnameVerification` makes the client check that the certificate was issued for the host it connected to:

```scala
def connect(trustStorePath: String, password: Array[Char]): ZIO[Scope, Throwable, Connection] =
  trustingSslContext(trustStorePath, password).flatMap { context =>
    val factory = new ConnectionFactory()
    factory.setHost("rabbit.internal")
    factory.setPort(5671)
    factory.setUsername("app")
    factory.setPassword("secret")
    factory.useSslProtocol(context)
    factory.enableHostnameVerification()
    Amqp.connect(factory)
  }
```

Avoid the no-argument `ConnectionFactory#useSslProtocol()` for a private certificate authority: it uses the JVM's default trust store, so it rejects a certificate the JVM does not trust, and it does not check that the certificate was issued for the host unless you also call `enableHostnameVerification()`.

## Present a Client Certificate

If the broker authenticates clients by certificate (mutual TLS), also pass a key manager, built from a key store that holds the client's private key:

```scala
def mutualTlsContext(
  trustStorePath: String,
  clientStorePath: String,
  password: Array[Char]
): Task[SSLContext] =
  for {
    trustStore  <- loadKeyStore(trustStorePath, password)
    clientStore <- loadKeyStore(clientStorePath, password)
  } yield {
    val keyManagers   = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm)
    keyManagers.init(clientStore, password)
    val trustManagers = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm)
    trustManagers.init(trustStore)
    val context       = SSLContext.getInstance("TLS")
    context.init(keyManagers.getKeyManagers, trustManagers.getTrustManagers, null)
    context
  }
```

Use the resulting context with `ConnectionFactory#useSslProtocol` exactly as above.

## Passwords with Special Characters

[`AMQPConfig#toUri`](./reference/amqp.md#converting-to-a-uri) writes the credentials into the URI without percent-encoding. If a password contains characters such as `@`, `:` or `/`, build a `ConnectionFactory` and call `setUsername` and `setPassword`, as in the snippets above, instead of using an `AMQPConfig`.
