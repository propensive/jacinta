/*
    Euphemism, version 0.13.0. Copyright 2019-21 Jon Pretty, Propensive OÜ.

    The primary distribution site is: https://propensive.com/

    Licensed under the Apache License, Version 2.0 (the "License"); you may not use this
    file except in compliance with the License. You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software distributed under the
    License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
    either express or implied. See the License for the specific language governing permissions
    and limitations under the License.
*/

package euphemism

import wisteria.*
import rudiments.*
import gossamer.*

import org.typelevel.jawn.{ParseException as JawnParseException, *}, ast.*

import scala.collection.mutable, collection.Factory
import scala.util.*

import scala.quoted.*
import scala.deriving.*

import language.dynamics

case class JsonParseError(line: Int, column: Int, message: Text)
extends Exception(t"euphemism: could not parse the JSON at $line:$column".s)

case class JsonAccessError(key: Int | Text)
extends Exception(key match
  case idx: Int => t"euphemism: could not access the index $idx in the JSON array".s
  case str: Text => t"euphemism: could not access the label $str in the JSON object".s
  case _        => throw Impossible("should never match")
)

case class JsonTypeError(expectedType: JsonPrimitive)
extends Exception(t"euphemism: the JSON element was not the expected type, $expectedType".s)

object JsonPrimitive:
  given Show[JsonPrimitive] = Showable(_).show

enum JsonPrimitive:
  case Array, Object, Number, Null, Boolean, String

extension [T: Json.Writer](value: T)
  def json: Json = Json(summon[Json.Writer[T]].write(value))

object Json extends Dynamic:
  given Show[Json] = json =>
    try Text(json.normalize.root.render())
    catch
      case err: JsonTypeError   => t"<type mismatch: expected ${err.expectedType}>"
      case err: JsonAccessError => err.key match
        case text: Text => t"<missing label: $text>"
        case int: Int   => t"<missing index: $int>"
        case _          => throw Impossible("all cases should have been handled")

  given clairvoyant.HttpResponse[Json] with
    def mediaType: String = "application/json"
    def content(json: Json): LazyList[IArray[Byte]] = LazyList(json.show.bytes)

  given clairvoyant.HttpReader[Json, JsonParseError] with
    def read(value: String): Json throws JsonParseError = Json.parse(Text(value))

  object Writer extends Derivation[Writer]:
    given Writer[Int] = JNum(_)
    given Writer[Text] = value => JString(value.s)
    given Writer[Double] = JNum(_)
    given Writer[Long] = JNum(_)
    given Writer[Byte] = JNum(_)
    given Writer[Short] = JNum(_)
    given Writer[Boolean] = if _ then JTrue else JFalse
    
    given (using CanThrow[JsonTypeError], CanThrow[JsonAccessError]): Writer[Json] =
      _.normalize.toOption.get.root
    
    given Writer[Nil.type] = value => JArray(Array())

    given [Coll[T1] <: Traversable[T1], T: Writer]: Writer[Coll[T]] = values =>
      JArray(values.map(summon[Writer[T]].write(_)).to(Array))

    given [T: Writer]: Writer[Map[String, T]] = values =>
      JObject(mutable.Map(values.view.mapValues(summon[Writer[T]].write(_)).to(Seq)*))

    given [T: Writer]: Writer[Option[T]] = new Writer[Option[T]]:
      override def omit(t: Option[T]): Boolean = t.isEmpty
      
      def write(value: Option[T]): JValue = value match
        case None        => JNull
        case Some(value) => summon[Writer[T]].write(value)

    def join[T](caseClass: CaseClass[Writer, T]): Writer[T] = value =>
      JObject(mutable.Map(caseClass.params.filter:
        param => !param.typeclass.omit(param.deref(value))
      .map { param => (param.label, param.typeclass.write(param.deref(value))) }*))
      
    def split[T](sealedTrait: SealedTrait[Writer, T]): Writer[T] = value =>
      sealedTrait.choose(value) { subtype =>
        val obj = subtype.typeclass.write(subtype.cast(value))
        obj match
          case JObject(vs) => vs("_type") = JString(subtype.typeInfo.short)
          case _           => ()
        
        obj
      }

  trait Writer[T]:
    def omit(t: T): Boolean = false
    def write(t: T): JValue
    def contramap[S](fn: S => T): Writer[S] = (v: S) => fn.andThen(write)(v)

  object Reader:// extends Derivation[Reader]:
    given Reader[Json] with
      type E = Nothing
      def read(value: => JValue): Json throws JsonTypeError | E = Json(value, Nil)

    given int: Reader[Int] with
      type E = Nothing
      def read(value: => JValue): Int throws JsonTypeError | E =
        value.getLong.getOrElse(throw JsonTypeError(JsonPrimitive.Number)).toInt
    
    given Reader[Byte] = long.map(_.toByte)
    given Reader[Short] = long.map(_.toShort)
    
    given float: Reader[Float] with
      type E = Nothing
      def read(value: => JValue): Float throws JsonTypeError | E =
        value.getDouble.getOrElse(throw JsonTypeError(JsonPrimitive.Number)).toFloat

    given double: Reader[Double] with
      type E = Nothing
      def read(value: => JValue): Double throws JsonTypeError | E =
        value.getDouble.getOrElse(throw JsonTypeError(JsonPrimitive.Number))

    given long: Reader[Long] with
      type E = Nothing
      def read(value: => JValue): Long throws JsonTypeError | E =
        value.getLong.getOrElse(throw JsonTypeError(JsonPrimitive.Number))

    given string: Reader[Text] with
      type E = Nothing
      def read(value: => JValue): Text throws JsonTypeError | E =
        Text(value.getString.getOrElse(throw JsonTypeError(JsonPrimitive.String)))
    
    
    given boolean: Reader[Boolean] with
      type E = Nothing
      def read(value: => JValue): Boolean throws JsonTypeError | E =
        value.getBoolean.getOrElse(throw JsonTypeError(JsonPrimitive.Number))

    given opt[T](using Reader[T]): Reader[Option[T]] with
      type E = Nothing
      def read(value: => JValue): Option[T] throws JsonTypeError | E =
        try Some(summon[Reader[T]].read(value)) catch case e: Exception => None

    given array[Coll[T1] <: Traversable[T1], T]
               (using reader: Reader[T], factory: Factory[T, Coll[T]]): Reader[Coll[T]] =
      new Reader[Coll[T]]:
        type E = reader.E
        
        def read(value: => JValue): Coll[T] throws JsonTypeError | reader.E = value match
          case JArray(vs) => val bld = factory.newBuilder
                             
                             vs.foreach:
                               v => bld += reader.read(v)

                             bld.result()
          
          case _          => throw JsonTypeError(JsonPrimitive.Array)

    given map[T](using reader: Reader[T]): Reader[Map[String, T]] = new Reader[Map[String, T]]:
      type E = reader.E
      
      def read(value: => JValue): Map[String, T] throws JsonTypeError | reader.E = value match
        case JObject(vs) => vs.toMap.foldLeft(Map[String, T]()):
                              case (acc, (k, v)) => acc.updated(k, reader.read(v))
        
        case _           => throw JsonTypeError(JsonPrimitive.Object)

    transparent inline given derived[T <: Product]: Reader[T] = ${JsonMacro.deriveReader[T]}

    // def join[T](caseClass: CaseClass[Reader, T]): Reader[T] = new Reader[T]:
    //   type E = JsonAccessError
    //   def read(value: => JValue): T throws JsonTypeError | JsonAccessError =
    //     caseClass.construct { param =>
    //       value match
    //         case JObject(vs) =>
    //           val value = vs.get(param.label).getOrElse(throw JsonAccessError(Text(param.label)))
    //           import unsafeExceptions.canThrowAny
    //           param.typeclass.read(value)
            
    //         case _ =>
    //           throw JsonTypeError(JsonPrimitive.Object)
    //     }

    // def split[T](sealedTrait: SealedTrait[Reader, T]): Reader[T] = new Reader[T]:
    //   type E = JsonAccessError
    //   def read(value: => JValue): T throws JsonTypeError | JsonAccessError =
    //     val _type = Json(value, Nil)._type.as[Text]
    //     val subtype = sealedTrait.subtypes.find { t => Text(t.typeInfo.short) == _type }
    //       .getOrElse(throw JsonTypeError(JsonPrimitive.Object)) // FIXME
        
    //     try subtype.typeclass.read(value)
    //     catch case e: Exception => throw JsonAccessError(Text(subtype.typeInfo.short))

  abstract class MapReader[T](fn: collection.mutable.Map[String, JValue] => T) extends Reader[T]:
    
    def read(json: => JValue): T throws JsonTypeError | E = json match
      case JObject(vs) => fn(vs)
      case _           => throw JsonTypeError(JsonPrimitive.Object)

  trait Reader[T]:
    private inline def self: this.type = this
    type E <: Exception
    
    def read(json: => JValue): T throws JsonTypeError | E
    def map[S](fn: T => S): Reader[S] { type E = self.E } = new Reader[S]:
      type E = self.E
      def read(json: => JValue): S throws JsonTypeError | self.E = fn(self.read(json))

  def parse(str: Text): Json throws JsonParseError = JParser.parseFromString(str.s) match
    case Success(value)                   => Json(value, Nil)
    case Failure(err: JawnParseException) => throw JsonParseError(err.line, err.col, Text(err.msg))
    case Failure(err)                     => throw err

  def applyDynamicNamed[T <: String](methodName: "of")(elements: (String, Json)*): Json =
    Json(JObject(mutable.Map(elements.map(_ -> _.root)*)), Nil)

case class Json(root: JValue, path: List[Int | Text] = Nil)
extends Dynamic, Shown[Json] derives CanEqual:
  def apply(idx: Int): Json = Json(root, idx :: path)
  def apply(field: Text): Json = Json(root, field :: path)
  def selectDynamic(field: String): Json = this(Text(field))
  def applyDynamic(field: String)(idx: Int): Json = this(Text(field))(idx)

  def normalize: Json throws JsonAccessError | JsonTypeError =
    def deref(value: JValue, path: List[Int | Text])
             : JValue throws JsonTypeError | JsonAccessError = path match
      case Nil =>
        value
      case (idx: Int) :: tail => value match
        case JArray(vs) =>
          deref((vs.unsafeImmutable.lift(idx) match
            case None        => throw JsonAccessError(idx)
            case Some(value) => value
          ), tail)
        case _ =>
          throw JsonTypeError(JsonPrimitive.Array)
      case (field: Text) :: tail => value match
        case JObject(vs) =>
          deref((vs.get(field.s) match
            case None        => throw JsonAccessError(field)
            case Some(value) => value
          ), tail)
        case _ =>
          throw JsonTypeError(JsonPrimitive.Object)
      
      case _ => throw Impossible("should never match")
      
    Json(deref(root, path.reverse), Nil)

  inline def as[T](using reader: Json.Reader[T])
                  : T throws JsonTypeError | JsonAccessError | reader.E =
    reader.read(normalize.root)
  