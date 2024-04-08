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

import turbulence.*
import contingency.*
import merino.*
import anticipation.*
import rudiments.*

object Ndjson:
  def parse
      [SourceType]
      (value: SourceType)
      (using readable:     Readable[SourceType, Line],
             jsonParse:    Errant[JsonParseError],
             textReadable: Readable[Text, Bytes])
          : Ndjson =

    Ndjson(value.stream[Line].map { line => Json.parse(line.content) })
  
case class Ndjson(stream: LazyList[Json])
