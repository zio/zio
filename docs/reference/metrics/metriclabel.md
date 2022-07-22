---
id: metriclabel
title: "MetricLabel"
---

A `MetricLabel` metadata represents a key-value pair that allows analyzing metrics at an additional level of granularity. For example, if a metric tracks the response time of a service, labels could be used to create separate versions that track response times for different clients. 

ZIO Metrics has a _label based dimensional_ data model where we have a metric name and just a list of key-value pairs attached to that metric name. So labels are the first-class citizen in ZIO Metrics. In monitoring dashboards, we can find or filter metrics using these labels.

For example, we can append following labels (dimensions) to our metric aspects:
- Endpoint (/api/users, /api/documents)
- HTTP Method (POST, GET)
- Deployment Environment (Staging, Production)
- HTTP Response Code
- Error code (404, 503)
- Datacenter Zone (us-east, eu-west)

```scala
import zio._
val counter = Metric.counter("http_requests")
  .tagged(
    MetricLabel("env", "production")
    MetricLabel("method", "GET"),
    MetricLabel("endpoint", "/api/users"),
    MetricLabel("zone", "ap-northeast"),
  )
```

By labeling metrics, we can query in a more granular way in monitoring dashboards, such as
- How many requests have been sent to each endpoint in total?
- In which zone are we about to violate SLAs?
- Which endpoint has the most latency?
