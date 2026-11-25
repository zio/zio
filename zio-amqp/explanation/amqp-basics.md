# AMQP Basics

> ZIO AMQP speaks AMQP 0-9-1, the protocol RabbitMQ implements. This page explains the few ideas the API is built on, so the method names on [`Channel`](../reference/channel.md) read as steps of one model.

## Producers, Exchanges, Queues and Consumers

A producer never sends a message to a queue directly. It publishes to an **exchange**, and the exchange routes the message to zero or more **queues** according to **bindings**. A **consumer** reads messages from a queue.

The routing rule depends on the exchange's [`ExchangeType`](../reference/model.md#exchangetype): a direct exchange matches the routing key exactly, a fanout exchange copies the message to every bound queue, a topic exchange matches patterns and a headers exchange matches message headers. A message that no binding matches is dropped unless `mandatory` is set, and ZIO AMQP has no API to receive returned messages.

Every broker has a default exchange named `""`. It routes a message to the queue whose name equals the routing key, which is why the [Getting Started](../getting-started.md) program works without declaring an exchange.

| ZIO AMQP call                                        | AMQP step                              |
| ---------------------------------------------------- | -------------------------------------- |
| `Channel#exchangeDeclare`, `Channel#queueDeclare`    | Create the pieces of the topology.     |
| `Channel#queueBind`                                  | Connect an exchange to a queue.        |
| `Channel#publish`                                    | Send a message to an exchange.         |
| `Channel#consume`                                    | Receive messages from a queue.         |
| `Channel#ack`, `Channel#nack`                        | Tell the broker what happened to one.  |

## Connections and Channels

A **connection** is one TCP connection to the broker. A **channel** is a lightweight session multiplexed over a connection, and all AMQP operations happen on a channel. Opening a connection is expensive, opening a channel is cheap, so a program usually opens one connection and one channel per independent job, such as one for publishing and one per consumer.

The RabbitMQ Java client's channel is not thread-safe. ZIO AMQP's `Channel` guards it with a one-permit semaphore, so many fibers can share one `Channel`, but they take turns. See [`Amqp.createChannel`](../reference/amqp.md#creating-a-channel).

Both are scoped resources: `Amqp.connect` and `Amqp.createChannel` close what they open when the `Scope` ends, channel first.

## Declaring Is Idempotent

`queueDeclare` and `exchangeDeclare` create the resource if it is missing and do nothing if it already exists with the same settings. Declaring it again with different settings is an error that closes the channel. This is why both producers and consumers usually declare the queues they use at start-up. `queueDeclarePassive` only checks that a queue exists.

## Durability

A queue or exchange declared with `durable = true` survives a broker restart. A message survives only if it is published as persistent into a durable queue. [How to Publish Durable Messages](../how-to-publish-durable-messages.md) shows both halves. How consumers confirm work is the subject of [Delivery and Failure](./delivery-and-failure.md).
