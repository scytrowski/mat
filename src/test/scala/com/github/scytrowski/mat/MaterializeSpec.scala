package com.github.scytrowski.mat

import org.scalatest.OptionValues
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.must.Matchers

class MaterializeSpec extends AnyFlatSpec with Matchers with OptionValues {

  behavior of "constants"

  it should "materialize unit" in {
    materializeOpt[Unit].value mustBe ()
  }

  it should "materialize constant boolean" in {
    materializeOpt[true].value mustBe true
  }

  it should "materialize constant integer" in {
    materializeOpt[1337].value mustBe 1337
  }

  it should "materialize constant float" in {
    materializeOpt[123.456].value mustBe 123.456
  }

  it should "materialize constant char" in {
    materializeOpt['t'].value mustBe 't'
  }

  it should "materialize constant string" in {
    materializeOpt["abcdef"].value mustBe "abcdef"
  }

  behavior of "tuples"

  it should "materialize empty tuple" in {
    materializeOpt[EmptyTuple].value mustBe EmptyTuple
  }

  it should "materialize tuple with single element" in {
    materializeOpt[5 *: EmptyTuple].value mustBe 5 *: EmptyTuple
  }

  it should "materialize tuple with multiple elements" in {
    materializeOpt[(false, "test", 9, 'd')].value mustBe (false, "test", 9, 'd')
  }

  it should "materialize nested empty tuple" in {
    materializeOpt[("abc", EmptyTuple, 'c')].value mustBe ("abc", EmptyTuple, 'c')
  }

  it should "materialize nested tuple" in {
    materializeOpt[(15, 13.15, ('a', true, "d"), false)].value mustBe (15, 13.15, ('a', true, "d"), false)
  }

}
