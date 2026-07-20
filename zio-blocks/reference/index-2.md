# Schema Evolution

> Schema evolution is the process of changing data structures over time while keeping existing data readable and systems interoperable. ZIO Blocks provides two type classes for this: `Into` for one-way conversions and `As` for bidirectional round-trip conversions.

Schema evolution is the process of changing data structures over time while keeping existing data readable and systems interoperable. ZIO Blocks provides two type classes for this: `Into` for one-way conversions and `As` for bidirectional round-trip conversions.

```
         Into[A, B]                    As[A, B]
  ─────────────────────        ──────────────────────────
  A ──── into(a) ──── B        A ──── into(a) ────► B
                               A ◄─── from(b) ──── B
  One-way, asymmetric           Bidirectional, round-trip
  Allows defaults, drops        Requires fields to match
  extra fields freely           or be Option; no defaults
                                on asymmetric fields
```

## `Into[A, B]` — One-Way Conversion

[`Into[A, B]`](./into.md) converts a value of type `A` to `Either[SchemaError, B]`. It is the right choice whenever the migration is asymmetric — for example, when adding a field with a default value, removing a field, or transforming data in a way that cannot be reversed.

Typical use cases:

- Migrating records from an old schema version to a new one
- Translating an external DTO into a validated domain model
- Converting API responses to internal representations

## `As[A, B]` — Bidirectional Round-Trip

[`As[A, B]`](./as.md) extends `Into[A, B]` with a `from(b: B): Either[SchemaError, A]` reverse direction. It guarantees that `A → B → A` restores the original value (within the constraints of numeric precision and optional fields). Use `As` when both sides of the conversion must remain in sync.

Typical use cases:

- Synchronising a local model with a remote representation
- Persisting to a data format that must be readable back into the same type
- Bridging two live systems that both produce and consume the same data

## Choosing Between `Into` and `As`

| | `Into[A, B]` | `As[A, B]` |
|---|---|---|
| Reverse conversion | ✗ | ✅ `from(b)` |
| Default values on extra fields | ✅ allowed | ✗ not allowed |
| Optional asymmetric fields | ✅ | ✅ (`Option` only) |
| Numeric coercion | ✅ (widening + narrowing) | ✅ (must be invertible) |
| Use for one-way migrations | ✅ | possible but overly strict |
| Use for bidirectional sync | manual | ✅ |

When in doubt, start with `Into`. Upgrade to `As` only when you need the reverse direction and can satisfy its stricter derivation requirements.
