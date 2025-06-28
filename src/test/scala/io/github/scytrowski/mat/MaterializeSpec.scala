package io.github.scytrowski.mat

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

  behavior of "products"

  it should "materialize empty product" in {
    materializeOpt[EmptyProduct.type].value mustBe EmptyProduct
  }

  it should "materialize product with single element" in {
    materializeOpt[SingleElementProduct[5]].value mustBe SingleElementProduct(5)
  }

  it should "materialize product with multiple elements" in {
    materializeOpt[MultipleElementsProduct[false, 'z', "test"]].value mustBe MultipleElementsProduct(false, 'z', "test")
  }

  it should "materialize nested empty product" in {
    materializeOpt[MultipleElementsProduct[25, EmptyProduct.type, 'p']].value mustBe MultipleElementsProduct(25, EmptyProduct, 'p')
  }

  it should "materialize nested product" in {
    materializeOpt[MultipleElementsProduct["abc", MultipleElementsProduct[true, 98.32, 'p'], 19]].value mustBe MultipleElementsProduct("abc", MultipleElementsProduct(true, 98.32, 'p'), 19)
  }

  behavior of "sums"

  it should "materialize singleton sum" in {
    materializeOpt[SingletonSum].value mustBe SingletonSumVariant
  }

  private case object EmptyProduct
  private case class SingleElementProduct[A](a: A)
  private case class MultipleElementsProduct[A, B, C](a: A, b: B, c: C)

  private sealed trait SingletonSum
  private case object SingletonSumVariant extends SingletonSum

}
