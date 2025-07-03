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
    materializeOpt[("abc", EmptyTuple, 'c')].value mustBe (
      "abc",
      EmptyTuple,
      'c'
    )
  }

  it should "materialize nested tuple" in {
    materializeOpt[(15, 13.15, ('a', true, "d"), false)].value mustBe (
      15,
      13.15,
      ('a', true, "d"),
      false
    )
  }

  it should "not materialize tuple with non materializable element" in {
    materializeOpt[(23, String, 'a')] mustBe empty
  }

  behavior of "named tuples"

  it should "materialize named tuple with single element" in {
    materializeOpt[(a: 5)].value mustBe ((a = 5))
  }

  it should "materialize named tuple with multiple elements" in {
    materializeOpt[(a: false, b: 'v', c: "ghi")].value mustBe ((a = false, b = 'v', c = "ghi"))
  }

  it should "materialize nested named tuple" in {
    materializeOpt[(a: 'u', b: 37, c: (d: 98.76, e: false))].value mustBe ((
      a = 'u',
      b = 37,
      c = (d = 98.76, e = false)
    ))
  }

  it should "not materialize named tuple with non materializable element" in {
    materializeOpt[(a: 3, b: "aaa", c: Int)] mustBe empty
  }

  behavior of "products"

  it should "materialize empty product" in {
    materializeOpt[EmptyProduct.type].value mustBe EmptyProduct
  }

  it should "materialize product with single element" in {
    materializeOpt[SingleElementProduct[5]].value mustBe SingleElementProduct(5)
  }

  it should "materialize product with multiple elements" in {
    materializeOpt[
      MultipleElementsProduct[false, 'z', "test"]
    ].value mustBe MultipleElementsProduct(false, 'z', "test")
  }

  it should "materialize nested empty product" in {
    materializeOpt[
      MultipleElementsProduct[25, EmptyProduct.type, 'p']
    ].value mustBe MultipleElementsProduct(25, EmptyProduct, 'p')
  }

  it should "materialize nested product" in {
    materializeOpt[MultipleElementsProduct[
      "abc",
      MultipleElementsProduct[true, 98.32, 'p'],
      19
    ]].value mustBe MultipleElementsProduct(
      "abc",
      MultipleElementsProduct(true, 98.32, 'p'),
      19
    )
  }

  it should "not materialize product with non materializable element" in {
    materializeOpt[MultipleElementsProduct["def", false, Int]] mustBe empty
  }

  behavior of "sums"

  it should "materialize singleton sum" in {
    materializeOpt[SingletonSum].value mustBe SingletonSumVariant
  }

  it should "not materialize sum with multiple variants" in {
    materializeOpt[SumWithMultipleVariants] mustBe empty
  }

  behavior of "enums"

  it should "materialize enum with single variant" in {
    materializeOpt[SingleVariantEnum].value mustBe SingleVariantEnum.Variant
  }

  it should "materialize enum variant" in {
    materializeOpt[SingleVariantEnum.Variant.type].value mustBe SingleVariantEnum.Variant
  }

  it should "not materialize enum with multiple variants" in {
    materializeOpt[MultipleVariantsEnum] mustBe empty
  }

  behavior of "other types"

  it should "materialize type lambda resulting in constant type" in {
    materializeOpt[TypeLambda[true]].value mustBe Some("test")
    materializeOpt[TypeLambda[false]].value mustBe None
  }

  it should "not materialize abstract type" in {
    materializeOpt[String] mustBe empty
  }

  it should "not materialize abstract generic type with abstract type parameter" in {
    materializeOpt[Option[Int]] mustBe empty
  }

  it should "not materialize abstract generic type with constant type parameter" in {
    materializeOpt[List[5]] mustBe empty
  }

  private case object EmptyProduct
  private case class SingleElementProduct[A](a: A)
  private case class MultipleElementsProduct[A, B, C](a: A, b: B, c: C)

  private sealed trait SingletonSum
  private case object SingletonSumVariant extends SingletonSum

  private sealed trait SumWithMultipleVariants
  private case object FirstVariant extends SumWithMultipleVariants
  private case object SecondVariant extends SumWithMultipleVariants
  private case object ThirdVariant extends SumWithMultipleVariants

  private enum SingleVariantEnum {
    case Variant
  }

  private enum MultipleVariantsEnum {
    case FirstVariant
    case SecondVariant
    case ThirdVariant
  }

  private type TypeLambda[C <: Boolean] <: Option[String] =
    C match
      case true  => Some["test"]
      case false => None.type

}
