---
id: gracefully-shutdown-zio-application
title: "Tutorial: How to Gracefully Shutdown ZIO Applications?"
sidebar_label: "Gracefully Shutdown ZIO Applications"
---

## Introduction

Graceful shutdown is a critical aspect of building robust and reliable software applications. It ensures that an application terminates smoothly, allowing it to complete necessary operations, release resources, and exit in a controlled manner.

In this article, we will explore how to achieve a graceful shutdown in ZIO applications, leveraging the power of the ZIO functional effect system.

## What is Graceful Shutdown?

Graceful shutdown refers to the process of stopping an application in a controlled and orderly manner.

Graceful shutdowns are applicable in various scenarios where we need to ensure the proper and controlled termination of an application or service. Some common use cases for graceful shutdowns include:

- **Server Applications**: When stopping a server application, such as a web server, API server, or message broker, it's essential to gracefully handle incoming requests, complete ongoing operations, and release resources before shutting down. Graceful shutdowns prevent data loss, ensure clean client disconnections, and maintain service availability for existing requests.

- **Database Connections**: Graceful shutdowns are important when closing database connections to ensure that pending transactions are committed or rolled back, and resources are released. This helps maintain data integrity and prevent blocking or deadlocking issues caused by abrupt connection closures.

- **Background Processes and Workers**: Graceful shutdowns are relevant for long-running background processes, worker threads, or scheduled tasks. They provide a mechanism to stop these processes gracefully, complete their current work items, and release any acquired resources before exiting.

- **Service-oriented Architectures**: In a service-oriented architecture, where multiple services communicate with each other, graceful shutdowns enable services to notify their dependencies, complete pending requests, and gracefully disconnect from other services before shutting down.

- **Streaming and Data Processing Applications**: Graceful shutdowns are crucial for streaming applications or data processing pipelines. They provide a mechanism to complete in-flight data processing, flush buffers, and ensure data consistency before stopping the application.

In summary, graceful shutdowns are relevant in a wide range of scenarios where we need to ensure the proper completion of ongoing operations, release resources, and maintain system integrity before terminating an application or service. They play a crucial role in ensuring data consistency, preventing resource leaks, and providing a smooth user experience during application shutdown.

## Releasing Resources Before App Termination

To ensure that all resources are released before our application terminates, we need to ensure that we have managed all resources using `ZIO.acquireRelease*` operations. It ensures that resources are properly acquired when needed and released when they are no longer required. In their release action, we should perform any clean-up operation needed to make sure our termination process is graceful.

```scala
trait ZIO[-R, +E, +A] {
  def acquireRelease[R, R1, E, A](
      acquire: => ZIO[R, E, A]
    )(
      release: A => ZIO[R1, Nothing, Any]
    ): ZIO[R with R1 with Scope, E, A]
}
```

Using `acquireRelease` method, involve another data type called `Scope`. For further details, please refer to the [`Scope`](../../reference/resource/scope.md) page on the documentation project.

## Adding Finalizer Before App Termination

By using `ensuring`/`onExit` in our ZIO application, we are explicitly specifying the clean-up logic that needs to be executed regardless of the outcome (success, failure, or interruption) of the ZIO effect.

The overall process is like this:

```scala mdoc:compile-only
import zio._

object GracefulShutdownExample extends ZIOAppDefault {

  def appLogic = {
    // Your application logic goes here
    ZIO.unit
  }

  def cleanup = {
    // Perform cleanup operations here
    ZIO.unit
  }

  override def run = appLogic.ensuring(cleanup)
}
```

We can categorize these finalizers into two categories:

1. If we don't require the exit status of effect, we can use `ZIO#ensuring`

```scala
trait ZIO[-R, +E, +A] {
  final def ensuring[R1 <: R](finalizer: => URIO[R1, Any]): ZIO[R1, E, A]
}
```

2. If we need the exit status of effect, we can use `ZIO#onExit`

```scala
trait ZIO[-R, +E, +A] {
  final def onExit[R1 <: R](cleanup: Exit[E, A] => URIO[R1, Any]): ZIO[R1, E, A]
}
```

## Graceful Shutdown and Resource Management in ZIO Applications

When building a ZIO application that integrates with external libraries such as ZIO HTTP, ZIO kafka, ZIO Redis, or any other resourceful ZIO libraries, it's common to rely on those libraries' built-in resource management utilities, often implemented using ZIO's `acquireRelease` or similar mechanisms.

When we introduce an additional clean-up task using ZIO's ensuring operator within our ZIO application, we not only ensure that the clean-up task will be executed regardless of whether the entire app succeeds, fails, or is interrupted, but we also ensure that all the resources managed by those external libraries will be properly initialized and released. This ensures that our application can gracefully shut down while handling any in-flight incoming requests or ongoing operations with the integrated libraries.

This allows us to coordinate the release of resources, complete pending operations, and ensure the stability and consistency of our application during the shutdown process.


## Conclusion

The combination of ZIO's ensuring operator and the resource management utilities provided by the integrated libraries ensures that the resources acquired by those libraries will be released properly. This helps prevent resource leaks, maintain data integrity, and provide a graceful shutdown experience for our ZIO application, including handling ongoing requests and tasks.
