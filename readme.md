[<img alt="GitHub Workflow" src="https://img.shields.io/github/actions/workflow/status/propensive/javanais/main.yml?style=for-the-badge" height="24">](https://github.com/propensive/javanais/actions)
[<img src="https://img.shields.io/discord/633198088311537684?color=8899f7&label=DISCORD&style=for-the-badge" height="24">](https://discord.gg/7b6mpF6Qcf)
<img src="/doc/images/github.png" valign="middle">

# Javanais

_Javanais_ is a fully-featured JSON library built upon the JSON parser,
[Merino](https://github.com/propensive/merino/), and designed to make it easy
and safe to work with JSON in Scala.

## Features

- parse and represent JSON in Scala
- intuitive dynamic API for field access, without compromising typesafety
- typeclass-based conversion to and from JSON
- generic derivation of typeclass interfaces for reading and writing product and coproduct types to JSON


## Availability

Javanais has not yet been published as a binary, though work is ongoing to fix this.

## Getting Started

### Parsing

A `Json` value may be obtained from any readable value by passing it to the `Json.parse` method. This could be a
string value, for example,
```scala
import javanais.*
Json.parse(t"""{ "name": "Alfred", "age": 83 }""")
```
but could also be a file or any other data stream with an appropirate `Readable` typeclass instance in scope:
```scala
import galilei.*
val input = (dir / t"source.json").file
Json.parse(input)
```

If parsing fails, a `JsonParseError` is thrown. Otherwise, an instance of `Json` representing a JSON abstract
syntax tree is returned.

### Serialization

Many types may be serialized to JSON, i.e. converted into instances of `Json`, by calling the `.json` extension
method upon them. `42.json` will produce a `Json` value of the integer `42` represented as a JSON number type.

Other primitive types may be converted in obvious ways, for example, `t"Hello World".json`. Case class instances
may be converted into `Json` instances of objects provided the type of every parameter of the case class can be.
This applies recursively, so a case class composed of other case classes may be serialized to JSON. For example:
```scala
case class Person(firstName: Text, lastName: Text)
case class Recipient(person: Person, emailAddress: Text)

val recipient = Recipient(Person(t"Mike", t"Smith"), t"mike@example.com").json
```

Given these definitions, the `recipient` instance would serialize to the JSON,
```json
{
  "person": {
    "firstName": "Mike",
    "lastName": "Smith"
  },
  "emailAddress": "mike@example.com"
}
```

#### Coproducts

Sealed traits of two or more case class subtypes will be serialized to JSON objects, exactly as each of the
subtypes would be, but with an additional field called `_type`, whose value will be set to the unqualified type
name, e.g. `"_type": "Leaf"`.

Although this encoding of coproduct types is non-standard, it is a reasonable default, and can always be
overridden with specific typeclass instances for the sealed trait type.

#### Collections

Furthermore, all traversable standard collection types can be serialized to JSON arrays, provided the elements
of the collection can be.

### Acessing values

Instances of `Json` are dynamically-typed which means that members with arbitrary names may be accessed as if
they were methods. Taking the `recipient` example above, it would be valid to access `recipient.person`, as if
the method `person` existed on the `Json` type. It doesn't, but since `Json` instances inherit from the special
`Dynamic` trait, the code will be transformed into `recipient.selectDynamic("person")` at compiletime, which
will return a new `Json` instance representing the JSON:
```json
{
  "firstName": "Mike",
  "lastName": "Smith"
}
```

It is therefore possible to call `recipient.person.firstName` directly and get a `Json` value representing the
JSON string, `"Mike"`.

As dynamic values with little known statically about them, instances of type `Json` are not particularly useful
_directly_, and should be converted to other types like `Text`, `Int` or `Person` before being used elsewhere in
a program. This is achieved with the `Json#as` method which takes the destination type as a parameter, for
example,
```scala
val addressee: Text = recipient.person.firstName.as[Text]
```
or,
```scala
val person: Person = recipient.person.as[Person]
```

As well as accessing arbitrary fields in a JSON object, elements of an array may be accessed by simply applying
the integer index to a `Json` value representing an array, for example, `json.organisation.users(2).as[User]`.

#### Errors

Since dynamic field access is unchecked at compiletime, it's possible that a JSON object would not contain the
requested field, or a JSON array would not contain the requsted index. This will throw an exception only when
attempting to convert the value to a static type. So the expression, `recipient.user.firstName`, (noting that
`user` is not a valid field of `recipient`) would not produce an error in itself. Only when invoking,
`recipient.user.firstname.as[Text]` would an exception be thrown, of type `JsonAccessError`.

Similarly, if the expression, `recipient.person.firstName.as[Int]` were evaluated, a `JsonAccessError` would be
thrown due to the field, `firstName` being a JSON string and not a JSON number.

All methods which throw exceptions are annotated with `throws` clauses, and if `saferExceptions` is enabled,
these must be handled.

### Typeclasses

While all Java primitive types and `String`s, collection types and case class types can be serialized and
deserialized automatically, it's possible to support other types or to replace existing default implementations
by providing contextual instances of the typeclasses, `Json.Writer` and `Json.Reader`.

For example, assuming the existence of an `Email` type (which simply wraps a `Text` instance), a `Reader` and
`Writer` for `Email` could be provided in `Email`'s companion object, like so:
```scala
case class Email(value: Text)

object Email:
  given Json.Reader[Email] = json => Email(json.as[Text])
  given Json.Writer[Email] = _.value.json
```
Note that `Email` is a case class, so default instances of `Json.Reader[Email]` and `Json.Writer[Email]` would
exist already, but would be replaced by these new definitions. (If `Email` were instead a non-`case` `class`,
these would be chosen unambiguously as the only contextual instances.)

#### Functor and Cofunctor

`Json.Reader`s are functors, and the `Reader#map` method is provided to transform a reader of one type into a
reader of another. Likewise, `Json.Writer`s are cofunctors with `Writer#contramap` methods. Given these
definitions, an alternative way to write the definitions for `Email` by transforming the existing instances for
the `Text` type would be:
```scala
object Email:
  given Json.Reader[Email] = summon[Json.Reader[Text]].map(Email(_))
  given Json.Writer[Email] = summon[Json.Writer[Text]].contramap(_.value)
```




## Related Projects

The following _Scala One_ libraries are dependencies of _Javanais_:

[![Anticipation](https://github.com/propensive/anticipation/raw/main/doc/images/128x128.png)](https://github.com/propensive/anticipation/) &nbsp; [![Gossamer](https://github.com/propensive/gossamer/raw/main/doc/images/128x128.png)](https://github.com/propensive/gossamer/) &nbsp; [![Merino](https://github.com/propensive/merino/raw/main/doc/images/128x128.png)](https://github.com/propensive/merino/) &nbsp; [![Probably](https://github.com/propensive/probably/raw/main/doc/images/128x128.png)](https://github.com/propensive/probably/) &nbsp;

No other _Scala One_ libraries are dependents of _Javanais_.

## Status

Javanais is classified as __fledgling__. For reference, Scala One projects are
categorized into one of the following five stability levels:

- _embryonic_: for experimental or demonstrative purposes only, without any guarantees of longevity
- _fledgling_: of proven utility, seeking contributions, but liable to significant redesigns
- _maturescent_: major design decisions broady settled, seeking probatory adoption and refinement
- _dependable_: production-ready, subject to controlled ongoing maintenance and enhancement; tagged as version `1.0` or later
- _adamantine_: proven, reliable and production-ready, with no further breaking changes ever anticipated

Projects at any stability level, even _embryonic_ projects, are still ready to
be used, but caution should be taken if there is a mismatch between the
project's stability level and the importance of your own project.

Javanais is designed to be _small_. Its entire source code currently consists
of 413 lines of code.

## Building

Javanais can be built on Linux or Mac OS with [Fury](/propensive/fury), however
the approach to building is currently in a state of flux, and is likely to
change.

## Contributing

Contributors to Javanais are welcome and encouraged. New contributors may like to look for issues marked
<a href="https://github.com/propensive/javanais/labels/beginner">beginner</a>.

We suggest that all contributors read the [Contributing Guide](/contributing.md) to make the process of
contributing to Javanais easier.

Please __do not__ contact project maintainers privately with questions unless
there is a good reason to keep them private. While it can be tempting to
repsond to such questions, private answers cannot be shared with a wider
audience, and it can result in duplication of effort.

## Author

Javanais was designed and developed by Jon Pretty, and commercial support and training is available from
[Propensive O&Uuml;](https://propensive.com/).



## Name

_Javanais_ is a form of French slang that involves artificially adding
syllables to words, and a dialect that people can be learn to encode or decode,
like JSON. Its initial letter is also a nod towards JSON.

## License

Javanais is copyright &copy; 2019-23 Jon Pretty & Propensive O&Uuml;, and is made available under the
[Apache 2.0 License](/license.md).
