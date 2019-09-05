---
id: advanced_error_model
title:  "Error Model"
---

## Declared Errors vs Unforeseen Defects
A ZIO value, iow a ZIO program, has a type parameter `E` which is the type of the declared errors it can fail with. E only covers those errors, which were specified at the outset. This does mean, that the same zio program can fail in ways that couldn't be foreseen. These unforeseen situations are aka _defects_ in a zio program, and they lie outside E.

Bringing abnormal app situations, from the domain of defects into that of E, enables the compiler to help us keep a tab on error conditions throughout the application, at compile time itself. This helps ensure the handling of domain errors in domain specific ways. Defects, on the other hand, can creep silently to higher levels in our application, and, if at all they get triggered, their handling might eventually be in more general ways, mostly leading to a loss in the richness of our application and a bad user experience
