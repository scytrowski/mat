package me.cytrowski.mat

import org.scalatest.OptionValues
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.must.Matchers
import scala.annotation.nowarn
import scala.compiletime.testing.typeCheckErrors

class MaterializeSpec extends AnyFlatSpec with Matchers with OptionValues {

  private inline def diagnostic(inline code: String): String =
    typeCheckErrors(code)
      .map(_.message)
      .find(_.contains("cannot be materialized"))
      .value

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

  behavior of "intersection types"

  it should "materialize an intersection with a literal type on the left" in {
    materializeOpt[5 & Int].value mustBe 5
  }

  it should "materialize an intersection with a literal type on the right" in {
    materializeOpt[Int & 5].value mustBe 5
  }

  it should "preserve the precise type of an intersection result" in {
    val left: 5 = materialize[5 & Int]
    val right: 5 = materialize[Int & 5]

    left mustBe 5
    right mustBe 5
  }

  it should "materialize an intersection with more than two components" in {
    val threeComponents: 5 = materialize[5 & Int & AnyVal]
    val fourComponents: 5 = materialize[5 & Int & AnyVal & Matchable]

    threeComponents mustBe 5
    fourComponents mustBe 5
  }

  it should "materialize an intersection of the same literal type" in {
    materializeOpt[5 & 5].value mustBe 5
    materializeOpt[5 & 5 & 5].value mustBe 5
  }

  it should "materialize an intersection containing a tuple" in {
    val left: (5, "abc", 'd') =
      materialize[(5, "abc", 'd') & Tuple]
    val right: (5, "abc", 'd') =
      materialize[Tuple & (5, "abc", 'd')]

    left mustBe (5, "abc", 'd')
    right mustBe (5, "abc", 'd')
  }

  it should "reject an intersection of incompatible literal types" in {
    typeCheckErrors("materialize[5 & \"abc\"]") must not be empty
  }

  it should "not materialize an unsupported intersection" in {
    materializeOpt[String & Int] mustBe empty
  }

  it should "not materialize an unsupported intersection with more than two components" in {
    materializeOpt[String & Int & AnyVal] mustBe empty
  }

  behavior of "union types"

  it should "materialize a union with one supported variant" in {
    val left: 5 = materialize[5 | String]
    val right: 5 = materialize[String | 5]

    left mustBe 5
    right mustBe 5
  }

  it should "materialize a nested union with one supported variant" in {
    val value: 5 = materialize[(5 | String) | Boolean]

    value mustBe 5
  }

  it should "materialize a flat union with more than two variants" in {
    val value: 5 = materialize[5 | String | Boolean]

    value mustBe 5
  }

  it should "materialize a union containing a tuple" in {
    val left: (5, "abc", 'd') =
      materialize[(5, "abc", 'd') | String]
    val right: (5, "abc", 'd') =
      materialize[String | (5, "abc", 'd')]

    left mustBe (5, "abc", 'd')
    right mustBe (5, "abc", 'd')
  }

  it should "materialize a union containing a tuple supertype" in {
    val left: (5, "abc", 'd') =
      materialize[(5, "abc", 'd') | Tuple]
    val right: (5, "abc", 'd') =
      materialize[Tuple | (5, "abc", 'd')]

    left mustBe (5, "abc", 'd')
    right mustBe (5, "abc", 'd')
  }

  it should "deduplicate repeated union variants" in {
    materializeOpt[5 | 5].value mustBe 5
  }

  it should "not materialize an ambiguous union" in {
    materializeOpt[5 | "abc"] mustBe empty
  }

  it should "not materialize an ambiguous flat union with more than two variants" in {
    materializeOpt[5 | "abc" | true] mustBe empty
  }

  it should "not materialize a union without a supported variant" in {
    materializeOpt[String | Int] mustBe empty
  }

  behavior of "mixed union and intersection types"

  it should "materialize a union containing an intersection" in {
    val left: 5 = materialize[(5 & Int) | String]
    val right: 5 = materialize[String | (5 & Int)]

    left mustBe 5
    right mustBe 5
  }

  it should "materialize an intersection containing a union" in {
    val left: 5 = materialize[(5 | String) & Int]
    val right: 5 = materialize[Int & (5 | String)]

    left mustBe 5
    right mustBe 5
  }

  it should "materialize a tuple containing a union and an intersection" in {
    val value: (5, "abc", 'd') =
      materialize[(5 | String, "abc" & String, 'd')]

    value mustBe (5, "abc", 'd')
  }

  it should "materialize a deeply nested union and intersection" in {
    val left: 5 = materialize[(5 & Int & AnyVal) | String]
    val right: 5 = materialize[(5 | String | Boolean) & AnyVal]

    left mustBe 5
    right mustBe 5
  }

  it should "reject an ambiguous union of intersections" in {
    materializeOpt[(5 & Int) | ("abc" & String)] mustBe empty
  }

  it should "reject a union and intersection without a materializable branch" in {
    materializeOpt[(String & Int) | Boolean] mustBe empty
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
    materializeOpt[(a: false, b: 'v', c: "ghi")].value mustBe ((
      a = false,
      b = 'v',
      c = "ghi"
    ))
  }

  it should "materialize named tuple with mixed union and intersection elements" in {
    val value: (a: 5, b: "abc", c: 'd') =
      materialize[(a: 5 | String, b: "abc" & String, c: 'd')]

    value mustBe ((a = 5, b = "abc", c = 'd'))
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

  it should "materialize product with mixed union and intersection fields" in {
    val value: MultipleElementsProduct[5, "abc", 'd'] =
      materialize[MultipleElementsProduct[5 | String, "abc" & String, 'd']]

    value mustBe MultipleElementsProduct(5, "abc", 'd')
  }

  it should "materialize a local product without generated symbol references" in {
    case class LocalProduct[A](value: A)

    materializeOpt[LocalProduct[5]].value mustBe LocalProduct(5)
  }

  it should "not materialize product with non materializable element" in {
    materializeOpt[MultipleElementsProduct["def", false, Int]] mustBe empty
  }

  behavior of "sums"

  it should "materialize singleton sum" in {
    materializeOpt[SingletonSum].value mustBe SingletonSumVariant
  }

  it should "materialize a nested singleton sum" in {
    materializeOpt[NestedSingletonRoot].value mustBe NestedSingletonLeaf
  }

  it should "materialize a deeply nested singleton sum" in {
    val value: DeepSingletonLeaf.type = materialize[DeepSingletonRoot]

    value mustBe DeepSingletonLeaf
  }

  it should "materialize a nested singleton sum containing a product" in {
    materializeOpt[NestedProductRoot].value mustBe NestedProductLeaf(5)
  }

  it should "not materialize a nested sum with multiple variants" in {
    materializeOpt[NestedMultipleRoot] mustBe empty
  }

  it should "not materialize a root with a singleton and nested branch" in {
    materializeOpt[MixedNestedRoot] mustBe empty
  }

  it should "materialize the only candidate among an empty nested branch" in {
    materializeOpt[MixedNestedWithEmptyBranchRoot].value mustBe
      MixedNestedWithEmptyBranchSingleton
  }

  it should "not materialize a sum with only empty branches" in {
    materializeOpt[OnlyEmptyBranchesRoot] mustBe empty
  }

  it should "reject multiple candidates even with an empty branch" in {
    materializeOpt[MixedMultipleRoot] mustBe empty
  }

  it should "reject ambiguous parameterized ADTs" in {
    materializeOpt[ParameterizedResult[5]] mustBe None
    materializeOpt[ParameterizedResult[String]] mustBe None
  }

  it should "filter GADT variants by the requested result type" in {
    materializeOpt[TypedExpr[Int]] mustBe Some(TypedInt)
    materializeOpt[TypedExpr[Boolean]] mustBe Some(TypedBoolean)
    materializeOpt[TypedExpr[String]] mustBe None
  }

  it should "instantiate compatible generic product variants" in {
    materializeOpt[InvariantResult[5]].value mustBe InvariantValue(5)
    materializeOpt[InvariantResult[String]] mustBe None
  }

  it should "reject ambiguous and bottom-typed Option variants" in {
    materializeOpt[Option[5]] mustBe None
    @nowarn("msg=Match type reduction failed")
    val nothingResult: Any = materializeOpt[Option[Nothing]]
    nothingResult mustBe None
  }

  it should "not materialize sum with multiple variants" in {
    materializeOpt[SumWithMultipleVariants] mustBe empty
  }

  behavior of "enums"

  it should "materialize enum with single variant" in {
    materializeOpt[SingleVariantEnum].value mustBe SingleVariantEnum.Variant
  }

  it should "materialize enum variant" in {
    materializeOpt[
      SingleVariantEnum.Variant.type
    ].value mustBe SingleVariantEnum.Variant
  }

  it should "not materialize enum with multiple variants" in {
    materializeOpt[MultipleVariantsEnum] mustBe empty
  }

  behavior of "custom materialization"

  it should "materialize type using custom implementation" in {
    materializeOpt[
      ClassWithCustomMaterialization
    ].value mustBe ClassWithCustomMaterialization.instance
  }

  it should "prefer custom materialization over built-in implementation" in {
    var customMaterializationUsed = false

    given CustomMaterialize[Unit]:
      override type Out = Unit
      override def apply(): Unit =
        customMaterializationUsed = true

    materializeOpt[Unit].value mustBe ()
    customMaterializationUsed mustBe true
  }

  behavior of "compile-time contract"

  it should "preserve precise result types" in {
    val constant: 1337 = materialize[1337]
    val tuple: (true, "test") = materialize[(true, "test")]
    val product: SingleElementProduct[5] = materialize[SingleElementProduct[5]]
    val singleton: SingletonSumVariant.type = materialize[SingletonSum]

    constant mustBe 1337
    tuple mustBe (true, "test")
    product mustBe SingleElementProduct(5)
    singleton mustBe SingletonSumVariant
  }

  it should "support Materialize as a context bound" in {
    def requiresMaterialize[A: Materialize]: A = materialize[A]

    val value: SingleElementProduct[5] =
      requiresMaterialize[SingleElementProduct[5]]

    value mustBe SingleElementProduct(5)
  }

  it should "prefer explicit Materialize evidence over derivation" in {
    case class ExplicitMaterialize(value: String)

    given Materialize[ExplicitMaterialize] =
      Materialize.fromValue[ExplicitMaterialize, ExplicitMaterialize](
        ExplicitMaterialize("explicit")
      )

    materialize[ExplicitMaterialize] mustBe ExplicitMaterialize("explicit")
  }

  it should "prefer explicit Materialize evidence over union derivation" in {
    given Materialize.Aux[5 | String, String] =
      Materialize.fromValue[5 | String, String]("explicit")

    val value: String = materialize[5 | String]

    value mustBe "explicit"
  }

  it should "use explicit Materialize evidence through a context bound" in {
    case class ContextOnlyMaterialize(value: String)

    given Materialize[ContextOnlyMaterialize] =
      Materialize.fromValue[ContextOnlyMaterialize, ContextOnlyMaterialize](
        ContextOnlyMaterialize("context")
      )

    def requiresMaterialize[A: Materialize]: A = materialize[A]

    requiresMaterialize[ContextOnlyMaterialize] mustBe
      ContextOnlyMaterialize("context")
  }

  it should "evaluate Materialize.fromValue on every application" in {
    var applications = 0
    val evidence: Materialize.Aux[Int, Int] =
      Materialize.fromValue[Int, Int] {
        applications += 1
        applications
      }

    evidence() mustBe 1
    evidence() mustBe 2
    applications mustBe 2
  }

  it should "preserve the precise output type in Materialize evidence" in {
    val evidence: Materialize.Aux[SingletonSum, SingletonSumVariant.type] =
      summon[Materialize[SingletonSum]]

    evidence() mustBe SingletonSumVariant
  }

  it should "expose None as an Option for unsupported types" in {
    val result: Option[String] = materializeOpt[String]

    result mustBe empty
  }

  it should "expose None for nested unsupported types" in {
    val result: Option[MultipleElementsProduct[
      "ok",
      SingleElementProduct[String],
      false
    ]] = materializeOpt[
      MultipleElementsProduct["ok", SingleElementProduct[String], false]
    ]

    result mustBe empty
  }

  it should "reject unsupported types with materialize" in {
    typeCheckErrors("materialize[String]") must not be empty
  }

  it should "explain why an unsupported type cannot be materialized" in {
    diagnostic("materialize[String]") mustBe
      "Type java.lang.String cannot be materialized. Supported forms are literal types, tuples, products, single-variant sums, or CustomMaterialize."
  }

  it should "explain why unsupported Materialize evidence cannot be derived" in {
    diagnostic("Materialize.derived[String]") mustBe
      "Type java.lang.String cannot be materialized. Supported forms are literal types, tuples, products, single-variant sums, or CustomMaterialize."
  }

  it should "identify an unsupported tuple element" in {
    diagnostic("materialize[(5, String, true)]") mustBe
      "Element 1 of tuple scala.Tuple3[5, scala.Predef.String, true] (java.lang.String) cannot be materialized: Type java.lang.String cannot be materialized. Supported forms are literal types, tuples, products, single-variant sums, or CustomMaterialize."
  }

  it should "identify an unsupported intersection" in {
    diagnostic("materialize[String & Int]") mustBe
      "Intersection type scala.Predef.String & scala.Int cannot be materialized from either component (scala.Predef.String or scala.Int)."
  }

  it should "identify an ambiguous union" in {
    val message = typeCheckErrors("materialize[5 | \"abc\"]")
      .map(_.message)
      .find(_.contains("Union type"))
      .value
    message mustBe
      "Union type 5 | \"abc\" is ambiguous because multiple variants can be materialized: 5, \"abc\"."
  }

  it should "identify a union without a supported variant" in {
    diagnostic("materialize[String | Int]") mustBe
      "Union type scala.Predef.String | scala.Int cannot be materialized because none of its variants can be materialized."
  }

  it should "preserve context for nested mixed union and intersection errors" in {
    diagnostic(
      "materialize[MultipleElementsProduct[\"ok\", 5 | \"abc\", false]]"
    ) mustBe
      "Field 'b' of MaterializeSpec.this.MultipleElementsProduct[\"ok\", 5 | \"abc\", false] (5 | \"abc\") cannot be materialized: Union type 5 | \"abc\" is ambiguous because multiple variants can be materialized: 5, \"abc\"."
  }

  it should "identify an unsupported product field" in {
    diagnostic(
      "materialize[MultipleElementsProduct[\"ok\", false, String]]"
    ) mustBe
      "Field 'c' of MaterializeSpec.this.MultipleElementsProduct[\"ok\", false, scala.Predef.String] (java.lang.String) cannot be materialized: Type java.lang.String cannot be materialized. Supported forms are literal types, tuples, products, single-variant sums, or CustomMaterialize."
  }

  it should "preserve context for nested tuple errors" in {
    diagnostic("materialize[(5, (true, String))]") mustBe
      "Element 1 of tuple scala.Tuple2[5, scala.Tuple2[true, scala.Predef.String]] (scala.Tuple2[true, scala.Predef.String]) cannot be materialized: Element 1 of tuple scala.Tuple2[true, scala.Predef.String] (java.lang.String) cannot be materialized: Type java.lang.String cannot be materialized. Supported forms are literal types, tuples, products, single-variant sums, or CustomMaterialize."
  }

  it should "preserve context for nested product errors" in {
    diagnostic(
      "materialize[MultipleElementsProduct[\"ok\", SingleElementProduct[String], false]]"
    ) mustBe
      "Field 'b' of MaterializeSpec.this.MultipleElementsProduct[\"ok\", MaterializeSpec.this.SingleElementProduct[scala.Predef.String], false] (MaterializeSpec.this.SingleElementProduct[scala.Predef.String]) cannot be materialized: Field 'a' of MaterializeSpec.this.SingleElementProduct[scala.Predef.String] (java.lang.String) cannot be materialized: Type java.lang.String cannot be materialized. Supported forms are literal types, tuples, products, single-variant sums, or CustomMaterialize."
  }

  it should "preserve context for named tuple errors" in {
    diagnostic("materialize[(a: 5, b: String)]") mustBe
      "Element 1 of tuple scala.NamedTuple.NamedTuple[scala.Tuple2[\"a\", \"b\"], scala.Tuple2[5, scala.Predef.String]] (java.lang.String) cannot be materialized: Type java.lang.String cannot be materialized. Supported forms are literal types, tuples, products, single-variant sums, or CustomMaterialize."
  }

  it should "identify unsupported sums" in {
    val errors = typeCheckErrors("materialize[SumWithMultipleVariants]")

    errors.map(_.message).find(_.contains("variants")).value mustBe
      "Sum type MaterializeSpec.this.SumWithMultipleVariants has 3 materializable variants; only sums with exactly one materializable variant can be materialized."
  }

  it should "identify nested sum ambiguity" in {
    val errors = typeCheckErrors("materialize[MixedNestedRoot]")

    errors
      .map(_.message)
      .find(_.contains("materializable variants"))
      .value mustBe
      "Sum type MaterializeSpec.this.MixedNestedRoot has 2 materializable variants; only sums with exactly one materializable variant can be materialized."
  }

  it should "identify Option[5] ambiguity" in {
    val errors = typeCheckErrors("materialize[Option[5]]")

    errors.map(_.message).find(_.contains("variants")).value mustBe
      "Sum type scala.Option[5] has 2 materializable variants; only sums with exactly one materializable variant can be materialized."
  }

  it should "identify Option[Nothing] ambiguity" in {
    val errors = typeCheckErrors("materialize[Option[Nothing]]")

    errors.map(_.message).find(_.contains("variants")).value mustBe
      "Sum type scala.Option[scala.Nothing] has 1 materializable variants; only sums with exactly one materializable variant can be materialized."
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

  private sealed trait NestedSingletonRoot
  private sealed trait NestedSingletonBranch extends NestedSingletonRoot
  private case object NestedSingletonLeaf extends NestedSingletonBranch

  private sealed trait DeepSingletonRoot
  private sealed trait DeepSingletonBranch extends DeepSingletonRoot
  private sealed trait DeepSingletonInner extends DeepSingletonBranch
  private case object DeepSingletonLeaf extends DeepSingletonInner

  private sealed trait NestedProductRoot
  private sealed trait NestedProductBranch extends NestedProductRoot
  private case class NestedProductLeaf(value: 5) extends NestedProductBranch

  private sealed trait NestedMultipleRoot
  private sealed trait NestedMultipleBranch extends NestedMultipleRoot
  private case object NestedFirstLeaf extends NestedMultipleBranch
  private case object NestedSecondLeaf extends NestedMultipleBranch

  private sealed trait MixedNestedRoot
  private case object MixedNestedSingleton extends MixedNestedRoot
  private sealed trait MixedNestedBranch extends MixedNestedRoot
  private case object MixedNestedLeaf extends MixedNestedBranch

  private sealed trait MixedNestedWithEmptyBranchRoot
  private case object MixedNestedWithEmptyBranchSingleton
      extends MixedNestedWithEmptyBranchRoot
  private sealed trait MixedNestedWithEmptyBranch
      extends MixedNestedWithEmptyBranchRoot

  private sealed trait OnlyEmptyBranchesRoot
  private sealed trait OnlyEmptyBranchesLeft extends OnlyEmptyBranchesRoot
  private sealed trait OnlyEmptyBranchesRight extends OnlyEmptyBranchesRoot

  private sealed trait MixedMultipleRoot
  private case object MixedMultipleSingleton extends MixedMultipleRoot
  private sealed trait MixedMultipleEmptyBranch extends MixedMultipleRoot
  private case object MixedMultipleLeaf extends MixedMultipleRoot

  private sealed trait ParameterizedResult[+A]
  private case object ParameterizedEmpty extends ParameterizedResult[Nothing]
  private case class ParameterizedValue[A](value: A)
      extends ParameterizedResult[A]

  private sealed trait TypedExpr[A]
  private case object TypedInt extends TypedExpr[Int]
  private case object TypedBoolean extends TypedExpr[Boolean]

  private sealed trait InvariantResult[A]
  private case object InvariantEmpty extends InvariantResult[Nothing]
  private case class InvariantValue[A](value: A) extends InvariantResult[A]

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

  private sealed abstract class ClassWithCustomMaterialization

  private object ClassWithCustomMaterialization:
    val instance: ClassWithCustomMaterialization =
      new ClassWithCustomMaterialization {}

  private given CustomMaterialize[ClassWithCustomMaterialization]:
    override type Out = ClassWithCustomMaterialization
    override def apply(): ClassWithCustomMaterialization =
      ClassWithCustomMaterialization.instance

  private type TypeLambda[C <: Boolean] <: Option[String] =
    C match
      case true  => Some["test"]
      case false => None.type

}
