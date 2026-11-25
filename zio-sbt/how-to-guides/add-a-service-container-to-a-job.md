# Add a Service Container to a Job

> Jobs that need a database can attach a service. `options` carries the raw `docker create` arguments, which is where the health check goes — without one the job races the container and steps can start before it accepts connections:

```scala
import zio.Chunk
import zio.sbt.githubactions.{ImageRef, Service, ServicePort}

Job(
  id = "test",
  name = "Test",
  services = Seq(
    Service(
      name = "postgres",
      image = ImageRef("postgres:16"),
      env = Map("POSTGRES_USER" -> "postgres", "POSTGRES_PASSWORD" -> "postgres"),
      ports = Chunk(ServicePort(5432, 5432)),
      options = Some("--health-cmd pg_isready --health-interval 10s --health-timeout 5s --health-retries 5")
    )
  ),
  steps = ...
)
```

`ServicePort(inner, outer)` renders as `inner:outer`, which GitHub reads as `<host>:<container>` — so `inner` is the port a step connects to on `localhost`.
