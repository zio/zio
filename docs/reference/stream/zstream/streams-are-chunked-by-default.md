---
id: streams-are-chunked-by-default
title: "Streams Are Chunked by Default"
sidebar_label: "Chunked Streams"
---

Every time we are working with streams, we are always working with chunks. There are no streams with individual elements, these streams have always chunks in their underlying implementation. So every time we evaluate a stream, when we pull an element out of a stream, we are actually pulling out a chunk of elements.

So why streams are designed in this way? This is because of the **efficiency and performance** issues. Every I/O operation in the programming world works with batches. We never work with a single element. 

For example, whenever we are reading or writing from/to a file descriptor, or a socket we are reading or writing multiple elements at a time. This is also true when we are working with an HTTP server or even JDBC drivers. We always read and write multiple bytes to be more performant.

So let's talk a bit about Chunk. Chunk is a ZIOs immutable array-backed collection. It is initially written for ZIO stream, but later it has been evolved into a very attractive general collection type which is also useful for other purposes. 

The `Chunk` data type is an immutable array-backed collection. Most importantly it tries to keep primitives unboxed. This is super important for the efficient processing of files and sockets. They are also very useful and efficient for encoding and decoding and writing transducers. 

To learn more about this data type, we have introduced that at the [Chunk](../chunk.md) section.
