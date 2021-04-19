---
id: zmanaged
title: "ZManaged"
---

A `ZManaged[R, E, A]` is a managed resource, that requires an `R`, and may fail with an `E` value, or succeed with an `A`.

 `ZManaged` is a data structure that encapsulates the acquisition and the release of a resource, which may be used by invoking the `use` method of the resource. The resource will be automatically acquired before the resource is used, and automatically released after the resource is used.

Resources do not survive the scope of `use`, meaning that if you attempt to capture the resource, leak it from `use`, and then use it after the resource has been consumed, the resource will not be valid anymore and may fail with some checked error, as per the type of the functions provided by the resource.

