# Running Queries

> There are several ways to run a `ZQuery`:

There are several ways to run a `ZQuery`:

- `runCache` runs the query using a given pre-populated cache. This can be useful for deterministically "replaying" a query without executing any new requests.
- `runLog` runs the query and returns its result along with the cache containing a complete log of all requests executed and their results. This can be useful for logging or analysis of query execution.
- `run` runs the query and returns its result.
