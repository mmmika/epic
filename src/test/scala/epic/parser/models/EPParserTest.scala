package epic.parser
package models
/*
 Copyright 2012 David Hall

 Licensed under the Apache License, Version 2.0 (the "License")
 you may not use this file except in compliance with the License.
 You may obtain a copy of the License at

 http://www.apache.org/licenses/LICENSE-2.0

 Unless required by applicable law or agreed to in writing, software
 distributed under the License is distributed on an "AS IS" BASIS,
 WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 See the License for the specific language governing permissions and
 limitations under the License.
*/
import org.junit.runner.RunWith
import org.scalatest._
import org.scalatest.junit._
import epic.trees.AnnotatedLabel


/**
 *
 * @author dlwh
 */
@RunWith(classOf[JUnitRunner])
class EPParserTest extends FunSuite with ParserTestHarness {


  test("one parsers test") {
    val grammar = ParserTestHarness.refinedGrammar
    val product = Parser(grammar.topology, grammar.lexicon, ParserTestHarness.simpleParser.constraintsFactory, EPChartFactory(grammar))

    val simple = ParserTestHarness.simpleParser.copy(decoder = new MaxVariationalDecoder)

    val rprod = evalParser(getTestTrees(), product)
    println(rprod + " " + evalParser(getTestTrees(), simple))
    assert(rprod.f1 > 0.6, rprod)
  }

  test("two parsers test") {
    val grammar = ParserTestHarness.refinedGrammar
    val product = Parser(grammar.topology, grammar.lexicon, ParserTestHarness.simpleParser.constraintsFactory, EPChartFactory(grammar, grammar))

    val rprod = evalParser(getTestTrees(), product)
    assert(rprod.f1 > 0.6, rprod)
  }

}

