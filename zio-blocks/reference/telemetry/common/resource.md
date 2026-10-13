# Resource

> Describes the entity producing telemetry (service, container, host). An immutable wrapper around Attributes shared by TracerProvider, LoggerProvider, and MeterProvider.

`Resource` is an immutable wrapper around `Attributes` that describes the entity producing telemetry — the service, container, host, or runtime. It is stamped on every span and every log record so that a backend can group signals by their origin. All three provider types (`TracerProvider`, `LoggerProvider`, `MeterProvider`) accept a `Resource` at configuration time; `Tracer` and `Logger` propagate it onto the signals they emit, while metric instruments do not carry it — collected `MetricData` holds only the per-measurement `Attributes`.

```scala
final case class Resource(attributes: Attributes)

object Resource {
  val empty:   Resource                       // no attributes
  val default: Resource                       // service.name + telemetry.sdk.* attributes
  def create(attrs: Attributes): Resource

  // merge comes from an implicit class on the companion
  implicit class ResourceOps(val self: Resource) {
    def merge(other: Resource): Resource      // other's attributes take precedence on duplicate keys
  }
}
```

## Predefined Instances

| Instance           | Description                                                                                                                                                          |
|--------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `Resource.empty`   | A `Resource` with no attributes. Use as a neutral base.                                                                                                              |
| `Resource.default` | Pre-populated with `service.name = "unknown_service"`, `telemetry.sdk.name = "zio-blocks"`, `telemetry.sdk.language = "scala"`, and the SDK version from build info. |
