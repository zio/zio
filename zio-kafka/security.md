# Security: TLS and Credentials

> Zio-kafka clients connect to Kafka through the Java clients, so encryption (TLS) and authentication (mutual TLS or SASL) are configured with the usual Java client properties. Zio-kafka adds two things on top: `KafkaCredentialStore`, which turns PEM-encoded certificates into the TLS properties, and an SSL endpoint check that catches a client configured for plain text that connects to a TLS listener, a mistake that otherwise crashes the application with an `OutOfMemoryError`.

## Security Protocols

The Java property `security.protocol` (constant `CommonClientConfigs.SECURITY_PROTOCOL_CONFIG`) selects how a client talks to the brokers. It must match the broker listener the bootstrap servers point to:

| `security.protocol`   | Encryption | Authentication                                                            |
| --------------------- | ---------- | ------------------------------------------------------------------------- |
| `PLAINTEXT` (default) | None       | None                                                                      |
| `SSL`                 | TLS        | Optional: a client certificate (mutual TLS)                               |
| `SASL_PLAINTEXT`      | None       | SASL (`PLAIN`, `SCRAM-SHA-256`, `SCRAM-SHA-512`, `OAUTHBEARER`, `GSSAPI`) |
| `SASL_SSL`            | TLS        | SASL, as above                                                            |

Every zio-kafka settings type accepts arbitrary Java client properties, so any security setup the Java clients support also works in zio-kafka:

| Settings type                   | Generic properties                                                       | TLS credentials                          |
| ------------------------------- | ------------------------------------------------------------------------ | ---------------------------------------- |
| `ConsumerSettings`              | `ConsumerSettings#withProperty`, `ConsumerSettings#withProperties`       | `ConsumerSettings#withCredentials`       |
| `ProducerSettings`              | `ProducerSettings#withProperty`, `ProducerSettings#withProperties`       | `ProducerSettings#withCredentials`       |
| `AdminClientSettings`           | `AdminClientSettings#withProperty`, `AdminClientSettings#withProperties` | `AdminClientSettings#withCredentials`    |
| `TransactionalProducerSettings` | Configure the wrapped `ProducerSettings`                                 | Configure the wrapped `ProducerSettings` |

`TransactionalProducerSettings` has no `with` methods of its own: configure security on a `ProducerSettings` and pass it to `TransactionalProducerSettings.apply(producerSettings, transactionalId)`. In all settings types, a property set later replaces a property with the same key set earlier.

## TLS from PEM Strings

`KafkaCredentialStore` holds a set of TLS properties. Its only constructor, `KafkaCredentialStore.fromPemStrings`, takes PEM-encoded strings, which is convenient when the certificates come from environment variables or a secret manager rather than from key store files:

```scala
sealed trait KafkaCredentialStore {
  def properties: Map[String, String]
}

object KafkaCredentialStore {
  def fromPemStrings(caCertificate: String, clientPrivateKey: String, clientCertificate: String): KafkaCredentialStore
}
```

`KafkaCredentialStore.fromPemStrings` sets the following properties. It replaces every newline in the PEM strings with a space, so the strings can be passed as single-line property values:

| Property                         | Value                                                    |
| -------------------------------- | -------------------------------------------------------- |
| `security.protocol`              | `SSL`                                                    |
| `ssl.truststore.type`            | `PEM`                                                    |
| `ssl.truststore.certificates`    | `caCertificate`: the CA certificate(s) the client trusts |
| `ssl.keystore.type`              | `PEM`                                                    |
| `ssl.keystore.key`               | `clientPrivateKey`: the client's private key             |
| `ssl.keystore.certificate.chain` | `clientCertificate`: the client's certificate chain      |

The result is a mutual TLS setup: the client verifies the brokers with the CA certificate, and the brokers authenticate the client by its certificate. The Java client only accepts a private key in PKCS#8 format; if the key is encrypted, also set its password with `withProperty("ssl.key.password", ...)`.

`ConsumerSettings#withCredentials`, `ProducerSettings#withCredentials` and `AdminClientSettings#withCredentials` add `KafkaCredentialStore#properties` to the settings. To read the PEM strings from environment variables and use the same credentials for a consumer, a producer and an admin client:

```scala
import zio._
import zio.kafka.admin.AdminClientSettings
import zio.kafka.consumer.ConsumerSettings
import zio.kafka.producer.ProducerSettings
import zio.kafka.security.KafkaCredentialStore

val bootstrapServers = List("kafka-1.example.com:9093", "kafka-2.example.com:9093")

def requiredEnv(name: String): Task[String] =
  System.env(name).someOrFail(new IllegalStateException(s"Missing environment variable $name"))

val credentials: Task[KafkaCredentialStore] =
  for {
    ca   <- requiredEnv("KAFKA_CA_PEM")
    key  <- requiredEnv("KAFKA_CLIENT_KEY_PEM")
    cert <- requiredEnv("KAFKA_CLIENT_CERT_PEM")
  } yield KafkaCredentialStore.fromPemStrings(caCertificate = ca, clientPrivateKey = key, clientCertificate = cert)

val settings: Task[(ConsumerSettings, ProducerSettings, AdminClientSettings)] =
  credentials.map { creds =>
    (
      ConsumerSettings(bootstrapServers).withGroupId("billing").withCredentials(creds),
      ProducerSettings(bootstrapServers).withCredentials(creds),
      AdminClientSettings(bootstrapServers).withCredentials(creds)
    )
  }
```

`KafkaCredentialStore.fromPemStrings` always includes a client key and certificate. For one-way TLS, where only the client verifies the brokers, set the properties yourself. To trust a private CA from a PEM string, without a client certificate:

```scala
import org.apache.kafka.clients.CommonClientConfigs
import org.apache.kafka.common.config.SslConfigs
import zio.kafka.producer.ProducerSettings

val caPem: String = "-----BEGIN CERTIFICATE-----\n...\n-----END CERTIFICATE-----"

val settings: ProducerSettings =
  ProducerSettings(List("kafka.example.com:9093")).withProperties(
    CommonClientConfigs.SECURITY_PROTOCOL_CONFIG    -> "SSL",
    SslConfigs.SSL_TRUSTSTORE_TYPE_CONFIG           -> "PEM",
    SslConfigs.SSL_TRUSTSTORE_CERTIFICATES_CONFIG   -> caPem.replace('\n', ' ')
  )
```

When the brokers use certificates from a public CA that the JVM already trusts, `security.protocol` set to `SSL` is enough. Key store and trust store files (JKS or PKCS12) work too, through `ssl.truststore.location`, `ssl.truststore.password`, `ssl.keystore.location` and the related properties.

## SASL Authentication

SASL authenticates the client with a user name and password (or a token) instead of a certificate. Configure it with three properties: `security.protocol` set to `SASL_SSL` (or `SASL_PLAINTEXT` on an unencrypted listener), `sasl.mechanism` (constant `SaslConfigs.SASL_MECHANISM`), and `sasl.jaas.config` (constant `SaslConfigs.SASL_JAAS_CONFIG`) with the login module and credentials. To connect a consumer with SCRAM over TLS:

```scala
import org.apache.kafka.clients.CommonClientConfigs
import org.apache.kafka.common.config.SaslConfigs
import zio.kafka.consumer.ConsumerSettings

val username = "billing-service"
val password = "change-me" // load from a secret store in real code

val settings: ConsumerSettings =
  ConsumerSettings(List("kafka.example.com:9094"))
    .withGroupId("billing")
    .withProperties(
      CommonClientConfigs.SECURITY_PROTOCOL_CONFIG -> "SASL_SSL",
      SaslConfigs.SASL_MECHANISM                   -> "SCRAM-SHA-512",
      SaslConfigs.SASL_JAAS_CONFIG ->
        s"""org.apache.kafka.common.security.scram.ScramLoginModule required username="$username" password="$password";"""
    )
```

The `PLAIN` mechanism works the same way, with `sasl.mechanism` set to `PLAIN` and the login module `org.apache.kafka.common.security.plain.PlainLoginModule`. Managed Kafka services usually document the exact `sasl.mechanism` and `sasl.jaas.config` values to use; copy them into the settings as shown. To combine SASL with a private CA, add the `ssl.truststore.*` properties from the previous section.

## SSL Endpoint Check

A long-standing Kafka client bug ([KAFKA-4090](https://issues.apache.org/jira/browse/KAFKA-4090)) makes a client that uses a plain-text protocol (`PLAINTEXT` or `SASL_PLAINTEXT`) against a TLS listener read the broker's TLS handshake bytes as the size of a response. It then tries to allocate a buffer of that size, and the application dies with an `OutOfMemoryError`. Zio-kafka protects against this with `zio.kafka.utils.SslHelper.validateEndpoint`, which probes the brokers before the Java client is created and turns the mismatch into an ordinary error.

### When the Check Runs

The check runs while a client is created, before the Java client exists, in these constructors:

- `Consumer.make` and `Consumer.live`.
- `Producer.make` and `Producer.live`.
- `TransactionalProducer.make` and `TransactionalProducer.live`, using the properties of the wrapped `ProducerSettings`.
- `AdminClient.make`, `AdminClient.live` and `AdminClient.javaClientFromSettings`.

It is skipped when `security.protocol` contains `SSL`, ignoring case, that is for `SSL` and `SASL_SSL`: such a client speaks TLS and is not affected by the bug. It does not run in `Producer.fromJavaProducer`, `Consumer.fromJavaConsumerWithPermit`, `AdminClient.fromJavaClient` and `AdminClient.fromScopedJavaClient`, because these wrap a Java client that you create yourself. There is no setting to turn the check off.

### What the Check Does

For a client with a plain-text protocol, the check goes through these steps:

1. It reads `bootstrap.servers`, and fails with `IllegalArgumentException("Empty bootstrapServers list")` when the property is missing. This step runs for every protocol, before the `SSL` skip described above. An empty value, such as the one `AdminClientSettings(Nil)` produces, passes this step and fails at the address lookup in step 2 instead.
2. It resolves every bootstrap server to all of its IP addresses and opens a plain socket to each address, in parallel, on a blocking thread.
3. On each socket it sends a Kafka `ApiVersions` request, reads the first 5 bytes of the answer, and closes the socket.
4. If the first byte is a TLS record type, the broker answered with TLS, and the check fails with `IllegalArgumentException("Received an unexpected SSL packet from the server. Please ensure the client is properly configured with SSL enabled")`.

Each probe times out after the client's `request.timeout.ms` (30 seconds when it is not set or not a positive number). The overall outcome is:

| Situation                                                   | Outcome                                                                                                |
| ----------------------------------------------------------- | ------------------------------------------------------------------------------------------------------ |
| At least one address answers without TLS, none with TLS     | Succeeds; unreachable addresses are ignored                                                            |
| Any address answers with TLS                                | Fails with the "unexpected SSL packet" error                                                           |
| Any non-timeout error after a connection is open            | Fails with that error                                                                                  |
| No address can be reached (connection refused or timed out) | Fails with the first connection error, for example a `TimeoutException("Failed to contact <address>")` |

Every failure is wrapped in an `org.apache.kafka.common.KafkaException` with the message `"Failed to connect to Kafka"`, for every kind of client; the actual reason is the exception's cause. Because the check needs at least one reachable broker, `Consumer.make`, `Producer.make`, `TransactionalProducer.make` and `AdminClient.make` with a plain-text protocol fail when none of the bootstrap servers is reachable at startup.

To log the real reason when a client cannot be created:

```scala
import org.apache.kafka.common.KafkaException
import zio._
import zio.kafka.producer.{ Producer, ProducerSettings }

val producer: ZIO[Scope, Throwable, Producer] =
  Producer
    .make(ProducerSettings(List("localhost:9092")))
    .tapSomeError { case e: KafkaException if e.getCause != null =>
      ZIO.logError(s"Cannot connect to Kafka: ${e.getCause.getMessage}")
    }
```

## Testing Secured Clients

The `zio-kafka-testkit` module starts embedded brokers with security enabled. `Kafka.saslEmbedded` starts a broker whose listener uses `SASL_PLAINTEXT` with the `PLAIN` mechanism and an ACL authorizer, with `admin` (password `admin-secret`) as super user; `KafkaTestUtils.makeSaslAdminClient` connects to it. `Kafka.sslEmbedded` starts a broker with a TLS listener, but clients cannot actually use it: it only serves to test the SSL endpoint check. See [Writing Tests](writing-tests.md) for details.

## See Also

- [Admin Client](admin-client.md) for managing ACLs with `AdminClient#createAcls` and related operations.
- [Detecting Authorization Failures](detecting-authorization-failures.md) for how consumers and producers retry and report authorization errors.
- The Kafka documentation on [security](https://kafka.apache.org/documentation/#security) for broker-side setup and every client property.
