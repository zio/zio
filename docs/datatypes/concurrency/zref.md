---
id: zref
title: "ZRef"
---

A `ZRef[EA, EB, A, B]` is a polymorphic, purely functional description of a mutable reference. The fundamental operations of a `ZRef` are `set` and `get`. 

- **`set`** takes a value of type `A` and sets the reference to a new value, potentially failing with an error of type `EA`.
- **`get`** gets the current value of the reference and returns a value of type `B`, potentially
failing with an error of type `EB`.

When the error and value types of the `ZRef` are unified, that is, it is a `ZRef[E, E, A, A]`, the `ZRef` also supports atomic `modify` and `update` operations. All operations are guaranteed to be safe for concurrent access.


> _**Note:**_
>
>While `ZRef` provides the functional equivalent of a mutable reference, the value inside the `ZRef` should be immutable. For performance reasons `ZRef` is implemented in terms of compare and swap operations rather than synchronization. **These operations are not safe for mutable values that do not support concurrent access**.
