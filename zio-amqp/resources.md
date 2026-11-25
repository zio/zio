# Resources

> Further reading on AMQP, RabbitMQ and ZIO AMQP.

## ZIO AMQP

- [ZIO AMQP on GitHub](https://github.com/zio/zio-amqp): source code, issues and releases.
- [Scaladoc on javadoc.io](https://javadoc.io/doc/dev.zio/zio-amqp_2.13): the API documentation generated from the source.

## RabbitMQ

- [AMQP 0-9-1 Model Explained](https://www.rabbitmq.com/tutorials/amqp-concepts): the official explanation of exchanges, queues, bindings and channels.
- [RabbitMQ Java Client Guide](https://www.rabbitmq.com/client-libraries/java-api-guide): the client library that ZIO AMQP wraps. `Connection`, `ConnectionFactory`, `Delivery` and `AMQP.BasicProperties` come from it.
- [Consumer Acknowledgements and Publisher Confirms](https://www.rabbitmq.com/docs/confirms): delivery guarantees in detail.
- [Consumer Prefetch](https://www.rabbitmq.com/docs/consumer-prefetch): how `basicQos` limits unacknowledged messages.
- [Dead Letter Exchanges](https://www.rabbitmq.com/docs/dlx) and [Time-To-Live and Expiration](https://www.rabbitmq.com/docs/ttl): the queue arguments used in [How to Dead-Letter and Expire Messages](./how-to-dead-letter-and-expire-messages.md).
- [TLS Support](https://www.rabbitmq.com/docs/ssl): configuring the broker for TLS.
