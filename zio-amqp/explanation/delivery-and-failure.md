# Delivery and Failure

> This page explains what ZIO AMQP and RabbitMQ guarantee when messages are consumed, and what happens when something goes wrong. The task-oriented versions are [How to Acknowledge Messages](../how-to-acknowledge-messages.md) and [How to Recover from a Closed Channel](../how-to-recover-from-a-closed-channel.md).

## Acknowledgements Give At-Least-Once Delivery

When a consumer receives a delivery, the broker keeps the message as *unacknowledged* until the consumer settles it:

- `Channel#ack` says the message was processed. The broker forgets it.
- `Channel#nack` says it was not. With `requeue = true` the broker puts it back on the queue, with `requeue = false` it discards it or dead-letters it.

If a channel closes while messages are unacknowledged, the broker redelivers them to another consumer. So a message can be processed more than once, for example if a worker finishes the work and crashes before `ack`. Consumers that must not repeat an effect have to be idempotent.

With `autoAck = true` the broker treats a message as settled as soon as it sends it. That is at-most-once delivery: a crash loses whatever the consumer was holding.

## Back-Pressure

A consumer is a `ZStream`, and ZIO AMQP pushes each delivery into the stream as the broker sends it. The stream does not slow the broker down by itself. The bound on in-flight work is the prefetch count set with `Channel#basicQos`: the broker sends no more unacknowledged messages than that. See [How to Limit Prefetch](../how-to-limit-prefetch.md).

## Publishing Has No Confirmation

`Channel#publish` completes when the client has written the message to the connection. It does not wait for the broker to confirm that it accepted the message, because ZIO AMQP does not expose publisher confirms. A published message can still be lost if the broker or the network fails at that moment.

## Failures Close Channels

Most broker-side errors close the channel they occurred on, for example publishing to a non-existent exchange, declaring a queue with conflicting settings, or acknowledging an unknown delivery tag. After that, every call on that `Channel` fails and the consumer stream fails with the RabbitMQ client's `ShutdownSignalException`. The connection stays usable, so the remedy is to open a new channel on it.

The failure types are the Java client's: ZIO AMQP surfaces them in the `Throwable` error channel of each method without translating them.

## Interruption

When a consumer stream ends for any reason, ZIO AMQP cancels the consumer on the broker. Messages that were delivered and not acknowledged stay unacknowledged until the channel closes, after which the broker redelivers them. See [How to Stop a Consumer](../how-to-stop-a-consumer.md).
