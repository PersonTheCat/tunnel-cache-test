# Tunnel Cache Test
This repository contains a couple of performance tests comparing cached
vs manual sphere generation for repeated use cases. The results appear 
to indicate a minimum 65% performance increase over manual generation
when used at a high volume in a single thread.

# Rationale
`SphereData` is based on the following principles:
* No memory allocations whatsoever (after initial creation)
* No garbage collection of said memory (until the parent is disposed)
* Preferring single array access vs multi dimensional array access
* Avoiding data accessors where possible

We can see based on these results that violating a series of core Java 
principles is exactly what enables our code to perform better. 
`SphereData` is of itself still an abstraction which encapsulates a
series of raw data. It would almost certainly perform better to access
the data directly, but an exception was made here for the sake of
readability. I believe this to be well worth it even in a high performance
context.

# Considerations

It is worth noting that these changes are only significant when given 
the number of times we have to generate a single sphere: once to check
for water, once to produce the shape of the sphere, and a final time to
decorate the sphere with various features.

There may be other optimizations that we miss out on by requiring that
the entire sphere be used in each of these scenarios.
