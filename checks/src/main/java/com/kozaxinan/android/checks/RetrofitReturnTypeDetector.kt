package com.kozaxinan.android.checks

import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Detector.UastScanner
import com.android.tools.lint.detector.api.JavaContext
import com.intellij.psi.PsiSubstitutor
import com.intellij.psi.impl.source.PsiClassReferenceType
import com.kozaxinan.android.checks.RetrofitReturnTypeDetector.Visitor
import org.jetbrains.uast.UAnnotated
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UField
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.toUElement

/**
 * Parent class for finding fields of return type of and Retrofit interface method.
 *
 * Example;
 *
 * interface Api {
 *
 *  \@GET
 *   fun restSomething(): Dto
 * }
 *
 * class Dto(val a, val b)
 *
 * for this example, [Visitor.findAllFieldsOf] will return list of UField for the fields of DTO. {a, b}
 */
@Suppress("UnstableApiUsage")
internal abstract class RetrofitReturnTypeDetector : Detector(), UastScanner {

  override fun getApplicableUastTypes(): List<Class<UMethod>> = listOf(UMethod::class.java)

  abstract class Visitor(private val context: JavaContext) : UElementHandler() {

    private val listOfRetrofitAnnotations = listOf(
        "retrofit2.http.DELETE",
        "retrofit2.http.GET",
        "retrofit2.http.POST",
        "retrofit2.http.PUT",
        "DELETE",
        "GET",
        "POST",
        "PUT"
    )

    /**
     * Return all field of return type of a retrofit interface method.
     * Returned list is include recursive fields of complex classes and type information of genetic classes.
     *
     * Unit and Void return types are ignored.
     *
     * Static fields are ignored.
     *
     * @param node Method node to be check
     * @return A list of fields of return type of method.
     * Empty list if method doesn't belong to retrofit interface or method doesn't have valid return type.
     */
    fun findAllFieldsOf(node: UMethod): List<UField> {
      if (node.containingClass?.isInterface == true && hasRetrofitAnnotation(node)) {
        val returnType = node.returnType
        if (returnType is PsiClassReferenceType && returnType.isNotUnitOrVoid()) {
          return findAllInnerFields(returnType)
        }
      }

      return emptyList()
    }

    private fun PsiClassReferenceType.isNotUnitOrVoid() =
        !canonicalText.contains("Unit") && !canonicalText.contains("Void")

    private fun hasRetrofitAnnotation(method: UMethod): Boolean {
      return context
          .evaluator
          .getAllAnnotations(method as UAnnotated, true)
          .map { uAnnotation -> uAnnotation.qualifiedName }
          .intersect(listOfRetrofitAnnotations)
          .isNotEmpty()
    }

    private fun findAllInnerFields(typeRef: PsiClassReferenceType): List<UField> {
      val actualReturnType = findGenericClassType(typeRef)
      val typeClass = actualReturnType
          .resolve()
          .toUElement() as UClass

      val innerFields: List<UField> = typeClass
          .fields
          .filterNot { it.isStatic }

      return innerFields + innerFields
          .map { it.type }
          .filterIsInstance<PsiClassReferenceType>()
          .map(::findAllInnerFields)
          .flatten()
    }

    private fun findGenericClassType(returnType: PsiClassReferenceType): PsiClassReferenceType {
      val substitutor = returnType
          .resolveGenerics()
          .substitutor
      return if (substitutor == PsiSubstitutor.EMPTY) {
        returnType
      } else {
        findGenericClassType(substitutor.substitutionMap.values.first() as PsiClassReferenceType)
      }
    }
  }
}