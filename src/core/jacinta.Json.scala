/*
    Jacinta, version [unreleased]. Copyright 2024 Jon Pretty, Propensive OÜ.

    The primary distribution site is: https://propensive.com/

    Licensed under the Apache License, Version 2.0 (the "License"); you may not use this
    file except in compliance with the License. You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software distributed under the
    License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
    either express or implied. See the License for the specific language governing permissions
    and limitations under the License.
*/

package jacinta

import language.dynamics
import language.experimental.pureFunctions

import scala.collection.Factory
import scala.compiletime.*

import anticipation.*
import contingency.*
import fulminate.*
import prepositional.*
import gossamer.*
import hieroglyph.*
import merino.*
import rudiments.*
import spectacular.*
import turbulence.*
import vacuous.*
import wisteria.*

import JsonError.Reason

trait Json2:
  given [ValueType: Encodable in Json]
      => Optional[ValueType] is Encodable in Json as optionalEncodable =
    new Encodable:
      type Self = Optional[ValueType]
      type Format = Json

      override def omit(value: Optional[ValueType]): Boolean = value.absent

      def encode(value: Optional[ValueType]): Json =
        value.let(ValueType.encode(_)).or(Json.ast(JsonAst(0L)))

  given [ValueType: Decodable in Json](using Tactic[JsonError])
      => Optional[ValueType] is Decodable in Json as optional = (json, omit) =>
    if omit then Unset else ValueType.decode(json, false)

object Json extends Json2, Dynamic:
  def ast(value: JsonAst): Json = new Json(value)

  object DecodableDerivation extends Derivable[Decodable in Json]:
    inline def join[DerivationType <: Product: ProductReflection]
            : DerivationType is Decodable in Json =
      (json, omit) =>
        summonInline[Foci[List[Text]]].give:
          summonInline[Tactic[JsonError]].give:
            val keyValues = json.root.obj
            val values = keyValues(0).zip(keyValues(1)).to(Map)

            construct: [FieldType] =>
              context =>
                val omit = !values.contains(label.s)
                val value = if omit then JsonAst(0L) else values(label.s)
                focus(label :: prior.or(Nil)):
                  context.decode(new Json(value), omit)

    inline def split[DerivationType: SumReflection]: DerivationType is Decodable in Json =
      (json, omit) =>
        summonInline[Tactic[JsonError]].give:
          summonInline[Tactic[VariantError]].give:
            val values = json.root.obj

            values(0).indexOf("_type") match
              case -1 =>
                abort(JsonError(Reason.Label(t"_type")))

              case index =>
                delegate(values(1)(index).string): [VariantType <: DerivationType] =>
                  context => context.decode(json, omit)

  object EncodableDerivation extends Derivable[Encodable in Json]:
    inline def join[DerivationType <: Product: ProductReflection]
            : DerivationType is Encodable in Json =
      value =>
        summonInline[Foci[JsonPath]].give:
          val labels = fields(value): [FieldType] =>
            field => if context.omit(field) then "" else label.s

          val values = fields(value): [FieldType] =>
            field =>
              focus(prior.or(JsonPath()) / label):
                if context.omit(field) then null else context.encode(field).root

          Json.ast(JsonAst((labels.filter(_ != ""), values.filter(_ != null))))

    inline def split[DerivationType: SumReflection]: DerivationType is Encodable in Json = value =>
      variant(value): [VariantType <: DerivationType] =>
        value => summonInline[Tactic[JsonError]].give:
          Json.ast:
            context.encode(value).root match
              case (labels: IArray[String], values: IArray[JsonAst]) =>
                JsonAst((("_type" +: labels), (label.asInstanceOf[JsonAst] +: values)))

  inline given [ValueType: Reflection] => ValueType is Decodable in Json as decodable =
    DecodableDerivation.derived

  inline given [ValueType: Reflection] => ValueType is Encodable in Json as encodable =
    EncodableDerivation.derived

  given Json is Decodable in Json as boolean = (value, omit) => value
  given (using Tactic[JsonError]) => Boolean is Decodable in Json as boolean = (value, omit) => value.root.boolean
  given (using Tactic[JsonError]) => Int is Decodable in Json as int = (value, omit) => value.root.long.toInt
  given (using Tactic[JsonError]) => Byte is Decodable in Json as byte = (value, omit) => value.root.long.toByte
  given (using Tactic[JsonError]) => Short is Decodable in Json as short = (value, omit) => value.root.long.toShort
  given (using Tactic[JsonError]) => Double is Decodable in Json as double = (value, omit) => value.root.double
  given (using Tactic[JsonError]) => Float is Decodable in Json as float = (value, omit) => value.root.double.toFloat
  given (using Tactic[JsonError]) => Long is Decodable in Json as long = (value, omit) => value.root.long
  given (using Tactic[JsonError]) => Text is Decodable in Json as text = (value, omit) => value.root.string
  given (using Tactic[JsonError]) => String is Decodable in Json as string = (value, omit) => value.root.string.s

  given [ValueType: Decodable in Json](using Tactic[JsonError])
      => Option[ValueType] is Decodable in Json as option = (json, omit) =>
    if omit then None else Some(ValueType.decode(json, false))

  given [ValueType: Encodable in Json] => Option[ValueType] is Encodable in Json as optionEncodable =
    new Encodable:
      type Self = Option[ValueType]
      type Format = Json

      override def omit(value: Option[ValueType]): Boolean = value.isEmpty

      def encode(value: Option[ValueType]): Json = value match
        case None        => Json.ast(JsonAst(0L))
        case Some(value) => ValueType.encode(value)

  given Int is Encodable in Json as intEncodable = int => Json.ast(JsonAst(int.toLong))
  given Text is Encodable in Json as textEncodable = text => Json.ast(JsonAst(text.s))
  given String is Encodable in Json as stringEncodable = string => Json.ast(JsonAst(string))
  given Double is Encodable in Json as doubleEncodable = double => Json.ast(JsonAst(double))
  given Long is Encodable in Json as longEncodable = long => Json.ast(JsonAst(long))
  given Byte is Encodable in Json as byteEncodable = byte => Json.ast(JsonAst(byte.toLong))
  given Short is Encodable in Json as shortEncodable = short => Json.ast(JsonAst(short.toLong))
  given Boolean is Encodable in Json as booleanEncodable = boolean => Json.ast(JsonAst(boolean))
  given Json is Encodable in Json as jsonEncodable = identity(_)

  given [CollectionType <: Iterable, ElementType: Encodable in Json]
      => CollectionType[ElementType] is Encodable in Json =
    values => Json.ast(JsonAst(IArray.from(values.map(ElementType.encode(_).root))))

  given [CollectionType <: Iterable, ElementType: Decodable in Json]
      (using factory:    Factory[ElementType, CollectionType[ElementType]],
             jsonAccess: Tactic[JsonError],
             foci:       Foci[JsonPath])
      => (CollectionType[ElementType] is Decodable in Json) as array =
    (value, omit) =>
      val builder = factory.newBuilder
      var index: Int = 0
      value.root.array.each: json =>
        focus(prior.or(JsonPath()) / index):
          builder += ElementType.decode(Json.ast(json), false)
          index += 1

      builder.result()

  given [ElementType: Decodable in Json](using Tactic[JsonError])
      => (Map[Text, ElementType] is Decodable in Json) as map =

    (value, omit) =>
      val (keys, values) = value.root.obj

      keys.indices.foldLeft(Map[Text, ElementType]()): (acc, index) =>
        focus(prior.or(JsonPath()) / keys(index)):
          acc.updated(keys(index).tt, ElementType.decode(Json.ast(values(index)), false))

  given Encoder[Json] as encoder = json => MinimalJsonPrinter.print(json.root)

  def parse[SourceType: Readable by Bytes](value: SourceType): Json raises JsonParseError =
    Json(JsonAst.parse(value))

  given (using JsonPrinter) => Json is Showable = json =>
    try json.root.show catch case err: JsonError => t"<${err.reason.show}>"

  given (using encoder: CharEncoder, printer: JsonPrinter)
      => ((Json is GenericHttpResponseStream)) = new:
    def mediaType: Text = t"application/json; charset=${encoder.encoding.name}"
    def content(json: Json): LazyList[Bytes] = LazyList(json.show.bytes)

  given (using Tactic[JsonParseError]) => Decoder[Json] =
    text => Json.parse(LazyList(text.bytes(using charEncoders.utf8)))

  given (using Tactic[JsonParseError]) => ((Json is GenericHttpReader)) =
    text => Json.parse(LazyList(text.bytes(using charEncoders.utf8)))

  given [SourceType: Readable by Bytes](using Tactic[JsonParseError]) => Json is Aggregable by Bytes as aggregable =
    Json.parse(_)

  def applyDynamicNamed(methodName: "of")(elements: (String, Json)*): Json =
    val keys: IArray[String] = IArray.from(elements.map(_(0)))
    val values: IArray[JsonAst] = IArray.from(elements.map(_(1).root))
    Json(JsonAst((keys, values)))

class Json(rootValue: Any) extends Dynamic derives CanEqual:
  def root: JsonAst = rootValue.asInstanceOf[JsonAst]
  def apply(index: Int): Json raises JsonError = Json(root.array(index))

  def selectDynamic(field: String)(using erased DynamicJsonEnabler): Json raises JsonError =
    apply(field.tt)

  def applyDynamic(field: String)(index: Int)(using erased DynamicJsonEnabler)
          : Json raises JsonError =
    apply(field.tt)(index)

  def apply(field: Text): Json raises JsonError =
    root.obj(0).indexWhere(_ == field.s) match
      case -1    => raise(JsonError(Reason.Label(field)), this)
      case index => Json(root.obj(1)(index))

  override def hashCode: Int =
    def recur(value: JsonAst): Int = value.asMatchable match
      case value: Long       => value.hashCode
      case value: Double     => value.hashCode
      case value: BigDecimal => value.hashCode
      case value: String     => value.hashCode
      case value: Boolean    => value.hashCode

      case value: IArray[JsonAst] @unchecked =>
        value.foldLeft(value.length.hashCode)(_*31^recur(_))

      case (keys, values) => (keys.asMatchable: @unchecked) match
        case keys: IArray[String] @unchecked => (values.asMatchable: @unchecked) match
          case values: IArray[JsonAst] @unchecked =>
            keys.zip(values).to(Map).view.mapValues(recur(_)).hashCode

      case _ =>
        0

    recur(root)

  override def equals(right: Any): Boolean = right.asMatchable match
    case right: Json =>
      def recur(left: JsonAst, right: JsonAst): Boolean = right.asMatchable match
        case right: Long     => left.asMatchable match
          case left: Long       => left == right
          case left: Double     => left == right
          case left: BigDecimal => left == BigDecimal(right)
          case _             => false

        case right: Double => left.asMatchable match
          case left: Long       => left == right
          case left: Double     => left == right
          case left: BigDecimal => left == BigDecimal(right)
          case _             => false

        case right: BigDecimal => left.asMatchable match
          case left: Long       => BigDecimal(left) == right
          case left: Double     => BigDecimal(left) == right
          case left: BigDecimal => left == right
          case _             => false

        case right: String => left.asMatchable match
          case left: String => left == right
          case _         => false

        case right: Boolean => left.asMatchable match
          case left: Boolean => left == right
          case _         => false

        case right: IArray[JsonAst] @unchecked => left.asMatchable match
          case left: IArray[JsonAst] @unchecked =>
            right.length == left.length && right.indices.all: index =>
              recur(left(index), right(index))

          case _ =>
            false

        case (rightKeys, rightValues) => (rightKeys.asMatchable: @unchecked) match
          case rightKeys: IArray[String] => (rightValues.asMatchable: @unchecked) match
            case rightValues: IArray[JsonAst] @unchecked => (left.asMatchable: @unchecked) match
              case (leftKeys, leftValues) => (leftKeys.asMatchable: @unchecked) match
                case leftKeys: IArray[String] @unchecked =>
                  (leftValues.asMatchable: @unchecked) match
                    case leftValues: IArray[JsonAst] @unchecked =>
                      val leftMap = leftKeys.zip(leftValues).to(Map)
                      val rightMap = rightKeys.zip(rightValues).to(Map)

                      leftMap.keySet == rightMap.keySet && leftMap.keySet.all: key =>
                        recur(leftMap(key), rightMap(key))

              case _ =>
                false

        case _ =>
          false

      recur(root, right.root)

    case _ =>
      false

  def as[ValueType: Decodable in Json]: ValueType raises JsonError traces List[Text] =
    ValueType.decode(this, false)
