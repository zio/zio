package threadlocalbridge

import zio._
import java.util.UUID

/** Title: Request Context Propagation with ThreadLocalBridge
  * Description: A practical end-to-end example demonstrating how ThreadLocalBridge is used
  * for request context propagation in an async system. Shows setting correlation IDs that
  * are automatically propagated through all async operations, enabling distributed tracing
  * and request-scoped logging across concurrent fibers.
  * Run: sbt "threadlocal-bridge/runMain threadlocalbridge.CompleteExample"
  */
object CompleteExample extends App {
  
  // Domain model
  case class RequestContext(
    requestId: String,
    userId: String,
    startTime: Long
  )

  // ThreadLocal storage for request context
  val requestContextLocal = new ThreadLocal[RequestContext]()

  // Helper to get correlation ID for logging
  def getCorrelationId(contextRef: FiberRef[RequestContext]): ZIO[Any, Nothing, String] =
    contextRef.get.map(_.requestId)

  // Example services that need the request context
  object AuthService {
    def authenticate(
      contextRef: FiberRef[RequestContext],
      userId: String
    ): ZIO[Any, Nothing, Boolean] = for {
      correlationId <- getCorrelationId(contextRef)
      _ <- ZIO.debug(s"[$correlationId] AuthService: Authenticating user $userId")
      _ <- ZIO.sleep(50.millis)
      _ <- ZIO.debug(s"[$correlationId] AuthService: User $userId authenticated")
    } yield true
  }

  object DatabaseService {
    def fetchUserData(
      contextRef: FiberRef[RequestContext],
      userId: String
    ): ZIO[Any, Nothing, String] = for {
      correlationId <- getCorrelationId(contextRef)
      _ <- ZIO.debug(s"[$correlationId] DatabaseService: Fetching data for $userId")
      _ <- ZIO.sleep(100.millis)
      _ <- ZIO.debug(s"[$correlationId] DatabaseService: Retrieved user data")
    } yield s"UserData($userId)"
  }

  object LoggingService {
    def logRequest(
      contextRef: FiberRef[RequestContext],
      action: String
    ): ZIO[Any, Nothing, Unit] = for {
      context <- contextRef.get
      _ <- ZIO.debug(s"[${context.requestId}] LoggingService: $action")
    } yield ()
  }

  // Main request handler
  def handleRequest(
    contextRef: FiberRef[RequestContext],
    userId: String
  ): ZIO[Any, Nothing, Unit] = for {
    // Create request context with correlation ID
    requestId <- ZIO.succeed(UUID.randomUUID().toString.take(8))
    currentTime <- ZIO.succeed(java.lang.System.currentTimeMillis())
    context = RequestContext(requestId, userId, currentTime)
    
    // Set the context for this fiber
    _ <- contextRef.set(context)
    _ <- ZIO.debug(s"[${context.requestId}] === Processing request for user: $userId ===")
    
    // Execute service calls - they all see the same context
    authenticated <- AuthService.authenticate(contextRef, userId)
    
    userData <- if (authenticated) {
      DatabaseService.fetchUserData(contextRef, userId)
    } else {
      ZIO.succeed("Authentication failed")
    }
    
    _ <- LoggingService.logRequest(contextRef, s"Request completed with data: $userData")
    
    elapsed = java.lang.System.currentTimeMillis() - context.startTime
    _ <- ZIO.debug(s"[${context.requestId}] === Request completed in ${elapsed}ms ===")
  } yield ()

  val program: ZIO[ThreadLocalBridge, Nothing, Unit] = ZIO.scoped {
    for {
      // Create a FiberRef linked to ThreadLocal storage for request context
      currentTime <- ZIO.succeed(java.lang.System.currentTimeMillis())
      contextRef <- ThreadLocalBridge.makeFiberRef[RequestContext](
        RequestContext("default", "system", currentTime)
      )(
        context => requestContextLocal.set(context)
      )
      
      _ <- ZIO.debug("Starting request processing system...")
      _ <- ZIO.sleep(100.millis)
      
      // Simulate handling multiple concurrent requests
      // Each request will have its own isolated context
      
      req1 <- handleRequest(contextRef, "alice").fork
      req2 <- handleRequest(contextRef, "bob").fork
      req3 <- handleRequest(contextRef, "charlie").fork
      
      _ <- ZIO.sleep(100.millis)
      
      // Wait for all requests to complete
      _ <- req1.join
      _ <- req2.join
      _ <- req3.join
      
      _ <- ZIO.debug("All requests processed successfully")
      _ <- ZIO.debug("Notice how each request maintained its own correlation ID")
      _ <- ZIO.debug("even though operations executed asynchronously and concurrently.")
    } yield ()
  }

  def run(args: List[String]): ZIO[Any, Any, Any] = 
    program.provideLayer(ThreadLocalBridge.live)
}
