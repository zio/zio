package threadlocalbridge

import zio._

/** Title: Complete ThreadLocalBridge Example - Request Context Propagation
  * Description: A realistic end-to-end example showing how to use ThreadLocalBridge
  * to manage request context (request ID, user, correlation ID) across multiple
  * async operations, including database queries and logging.
  * Run: sbt "threadlocal-bridge/runMain threadlocalbridge.CompleteExample"
  */
object CompleteExample extends ZIOAppDefault {

  // ===== Request Context Model =====
  case class RequestContext(
    requestId: String,
    userId: String,
    correlationId: String
  )

  // ===== Java ThreadLocal for Request Context =====
  val requestContextThreadLocal: java.lang.ThreadLocal[RequestContext] =
    new java.lang.ThreadLocal[RequestContext] {
      override def initialValue(): RequestContext =
        RequestContext("unknown", "unknown", "unknown")
    }

  // ===== Simulated Services =====

  // Simulates a database query that needs request context for audit logging
  def queryDatabase(query: String): ZIO[Any, Nothing, String] = {
    val ctx = requestContextThreadLocal.get()
    ZIO.succeed {
      val result = s"Query results for: $query"
      println(
        s"  [DB] Request=${ctx.requestId} | User=${ctx.userId} | Query=$query | Result=$result"
      )
      result
    }
  }

  // Simulates an external API call that propagates correlation ID
  def callExternalApi(endpoint: String): ZIO[Any, Nothing, String] = {
    val ctx = requestContextThreadLocal.get()
    ZIO.succeed {
      Thread.sleep(50) // Simulate network delay
      val response = s"API response from $endpoint"
      println(
        s"  [API] Correlation=${ctx.correlationId} | Endpoint=$endpoint | Response=$response"
      )
      response
    }
  }

  // Simulates logging that uses context for structured logging
  def logEvent(event: String): ZIO[Any, Nothing, Unit] = {
    val ctx = requestContextThreadLocal.get()
    ZIO.succeed {
      println(
        s"  [LOG] RequestID=${ctx.requestId} | Event=$event"
      )
    }
  }

  // ===== Request Handler =====
  def handleRequest(
    contextRef: FiberRef[RequestContext],
    request: RequestContext
  ): ZIO[Any, Nothing, Unit] = {
    println(s"\n>>> Processing request: ${request.requestId} for user: ${request.userId}")

    // Use FiberRef.locally to scope the request context to this handler
    contextRef.locally(request) {
      for {
        _ <- ZIO.succeed(println(s"  [Handler] Context set: $request"))

        // Simulate request processing with multiple async operations
        _ <- logEvent("Request started")

        // Parallel operations: database queries and API calls
        _ <- ZIO.collectAllPar(
          List(
            queryDatabase("SELECT user_profile FROM users")
              .flatMap(result => logEvent(s"Profile loaded: $result")),
            callExternalApi("/api/user/permissions")
              .flatMap(result => logEvent(s"Permissions fetched: $result")),
            queryDatabase("SELECT orders FROM order_history")
          )
        ).unit

        // Sequential operations that depend on context
        _ <- for {
          _ <- logEvent("Processing order details")
          _ <- callExternalApi("/api/inventory/check")
          _ <- queryDatabase("UPDATE user_last_seen")
          _ <- logEvent("Request completed")
        } yield ()
      } yield ()
    }
  }

  // ===== Concurrent Request Simulation =====
  def runConcurrentRequests: ZIO[Scope with ThreadLocalBridge, Nothing, Unit] = {
    val requests = List(
      RequestContext(
        requestId = "req-001",
        userId = "alice",
        correlationId = "corr-a1b2c3"
      ),
      RequestContext(
        requestId = "req-002",
        userId = "bob",
        correlationId = "corr-x9y8z7"
      ),
      RequestContext(
        requestId = "req-003",
        userId = "charlie",
        correlationId = "corr-m5n6o7"
      )
    )

    println("=== Concurrent Request Handling with ThreadLocalBridge ===")
    println("Processing 3 concurrent requests with context isolation:\n")

    // Create FiberRef linked to ThreadLocal
    for {
      contextRef <- ThreadLocalBridge.makeFiberRef(
        RequestContext("default", "default", "default")
      )(ctx => requestContextThreadLocal.set(ctx))

      // Process multiple requests concurrently
      // Each request uses locally() to scope its context
      _ <- ZIO.collectAllPar(requests.map(handleRequest(contextRef, _))).unit
    } yield ()
  }

  // ===== Demo: Context Isolation =====
  def contextIsolationDemo: ZIO[Scope with ThreadLocalBridge, Nothing, Unit] = {
    println("\n=== ThreadLocal Context Isolation Demo ===\n")

    val ctx1 = RequestContext("req-iso-001", "user-x", "corr-iso-1")
    val ctx2 = RequestContext("req-iso-002", "user-y", "corr-iso-2")

    for {
      contextRef <- ThreadLocalBridge.makeFiberRef(ctx1)(
        ctx => requestContextThreadLocal.set(ctx)
      )

      _ <- ZIO.succeed {
        println(s"Main: Set context to ${ctx1.userId}")
        println(s"Main: Current context = ${requestContextThreadLocal.get()}")
      }

      // Child fiber uses locally() to scope a different context
      _ <- contextRef.locally(ctx2) {
        ZIO.succeed {
          println(s"Child: Modified context to ${ctx2.userId}")
          println(s"Child: Current context = ${requestContextThreadLocal.get()}")
        }
      }.fork.flatMap(_.join)

      // Main fiber's context is restored after child
      _ <- ZIO.succeed {
        println(s"Main: After child, context still = ${requestContextThreadLocal.get()}")
      }
    } yield ()
  }

  override def run: ZIO[Any, Any, Unit] = {
    val combined = for {
      _ <- runConcurrentRequests
      _ <- contextIsolationDemo
    } yield ()

    ZIO.scoped {
      combined
    }.provideLayer(ThreadLocalBridge.live)
  }
}
