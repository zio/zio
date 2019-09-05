---
id: advanced_error_model
title:  "Error Model"
---

## Declared Errors vs Unforeseen Defects
A ZIO value has a type parameter `E` which is the type of declared errors it can fail with. `E` only covers the errors which were specified at the outset. The same ZIO value could still throw exceptions in ways that couldn't be foreseen. These unforeseen situations are called _defects_ in a ZIO program, and they lie outside E.

Bringing abnormal situations from the domain of defects into that of `E` enables the compiler to help us keep a tab on error conditions throughout the application, at compile time. This helps ensure the handling of domain errors in domain specific ways. Defects, on the other hand, can creep silently to higher levels in our application, and, if at all they get triggered, their handling might eventually be in more general ways.
