package at.tugraz.ist.qs2022

import at.tugraz.ist.qs2022.simple.SimpleFunctions._
//import at.tugraz.ist.qs2022.simple.SimpleFunctionsMutant1._
//import at.tugraz.ist.qs2022.simple.SimpleFunctionsMutant2._
//import at.tugraz.ist.qs2022.simple.SimpleFunctionsMutant3._
//import at.tugraz.ist.qs2022.simple.SimpleFunctionsMutant4._
import at.tugraz.ist.qs2022.simple.SimpleJavaFunctions
import org.junit.runner.RunWith
import org.scalacheck.Prop.{forAll, propBoolean}
import org.scalacheck.{Arbitrary, Gen, Properties}

// Consult the following scalacheck documentation
// https://github.com/typelevel/scalacheck/blob/master/doc/UserGuide.md#concepts
// https://github.com/typelevel/scalacheck/blob/master/doc/UserGuide.md#generators

@RunWith(classOf[ScalaCheckJUnitPropertiesRunner])
class SimpleFunctionsTest extends Properties("SimpleFunctionsTest") {

  // Gen is some sort of function from scala check,
  // it is responsible to provide you random generated test data
  private val nonEmptyIntListGen: Gen[List[Int]] = Gen.nonEmptyListOf(Arbitrary.arbitrary[Int])

  // insertionSort Java style
  property("insertionSort Java: ordered") = forAll(nonEmptyIntListGen) { (xs: List[Int]) =>
    val sorted = SimpleJavaFunctions.insertionSort(xs.toArray)
    var correctFlag = true;
    if (xs.nonEmpty) {
      for (i <- 0 until sorted.length - 1) {
        if (sorted(i) > sorted(i + 1))
          correctFlag = false;
      }
      correctFlag // would be the return val
    }
    else
      false // returns false if xs is empty
  }

  // insertionSort the beautiful scala way
  property("insertionSort: ordered") = forAll(nonEmptyIntListGen) { (xs: List[Int]) =>
    val sorted = insertionSort(xs)
    xs.nonEmpty ==> xs.indices.tail.forall((i: Int) => sorted(i - 1) <= sorted(i))
  }
  property("insertionSort: permutation") = forAll { (xs: List[Int]) =>
    val sorted = insertionSort(xs)

    def count(a: Int, as: List[Int]) = as.count(_ == a)

    xs.forall((x: Int) => count(x, xs) == count(x, sorted))
  }


  // maximum
  // TODO: max() properties
  property("max: largest value") = forAll(nonEmptyIntListGen) { (xs: List[Int]) => 
    val max_value = max(xs)
    xs.nonEmpty ==> xs.forall((value: Int) => max_value >= value)
  }

  // minimal index
  // TODO: minIndex() properties
  property("minIndex: index of smallest value") = forAll(nonEmptyIntListGen) { (xs: List[Int]) => 
    val smallest_value_index = minIndex(xs)
    xs.nonEmpty ==> (smallest_value_index == xs.indexOf(xs.min))
  }

  // symmetric difference
  // TODO: symmetricDifference() properties
  property("symmetric difference: check for unique elements") = forAll(nonEmptyIntListGen, nonEmptyIntListGen) { (xs: List[Int], ys: List[Int]) => 
    val unique_elements = symmetricDifference(xs, ys)  
    //rs = symmetricDifference(xs, ys, rs)
    unique_elements.forall((element: Int) => (xs.contains(element) || ys.contains(element)))
  }

  // intersection
  // TODO: intersection() properties
  property("intersection: check for common elements") = forAll(nonEmptyIntListGen, nonEmptyIntListGen) { (xs: List[Int], ys: List[Int]) => 
    val common_elements = intersection(xs, ys)
    common_elements.forall((element: Int) => (xs.contains(element) && ys.contains(element)))  
  }

  // Smallest missing positive integer
  // TODO: smallestMissingPositiveInteger() properties
  property("smallest missing positive integer: return smallest positive int not contained in the set") = forAll(nonEmptyIntListGen) { (xs: List[Int]) =>
    val small_pos_int = smallestMissingPositiveInteger(xs)
    var smallest_missing_int: Int = 1
    val positive_ints_sorted = xs.filter((value: Int) => value > 0).sorted
    
    for(int <- positive_ints_sorted) {
      if(int == smallest_missing_int){
        smallest_missing_int += 1
      }
    }
    smallest_missing_int == small_pos_int && !xs.contains(smallest_missing_int)
  }
}
