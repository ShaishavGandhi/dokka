package org.jetbrains.dokka.base.signatures

import javaslang.Tuple2
import kotlinx.html.Entities
import org.jetbrains.dokka.base.transformers.pages.comments.CommentsToContentConverter
import org.jetbrains.dokka.base.translators.documentables.PageContentBuilder
import org.jetbrains.dokka.links.DRI
import org.jetbrains.dokka.links.DriOfAny
import org.jetbrains.dokka.links.DriOfUnit
import org.jetbrains.dokka.links.sureClassNames
import org.jetbrains.dokka.model.*
import org.jetbrains.dokka.model.properties.ExtraProperty
import org.jetbrains.dokka.model.properties.WithExtraProperties
import org.jetbrains.dokka.pages.ContentKind
import org.jetbrains.dokka.pages.ContentNode
import org.jetbrains.dokka.pages.PlatformData
import org.jetbrains.dokka.pages.TextStyle
import org.jetbrains.dokka.utilities.DokkaLogger
import kotlin.text.Typography.nbsp

class KotlinSignatureProvider(ctcc: CommentsToContentConverter, logger: DokkaLogger) : SignatureProvider {
    private val contentBuilder = PageContentBuilder(ctcc, this, logger)

    private val ignoredVisibilities = setOf(JavaVisibility.Public, KotlinVisibility.Public)
    private val ignoredModifiers = setOf(JavaModifier.Final, KotlinModifier.Final)

    override fun signature(documentable: Documentable): ContentNode = when (documentable) {
        is DFunction -> functionSignature(documentable)
        is DProperty -> propertySignature(documentable)
        is DClasslike -> classlikeSignature(documentable)
        is DTypeParameter -> signature(documentable)
        is DEnumEntry -> signature(documentable)
        is DTypeAlias -> signature(documentable)
        else -> throw NotImplementedError(
            "Cannot generate signature for ${documentable::class.qualifiedName} ${documentable.name}"
        )
    }

    private fun signature(e: DEnumEntry) = contentBuilder.contentFor(e, ContentKind.Symbol, setOf(TextStyle.Monospace))

    private fun actualTypealiasedSignature(dri: DRI, name: String, aliasedTypes: PlatformDependent<Bound>) =
        aliasedTypes.entries.groupBy({ it.value }, { it.key }).map { (bound, platforms) ->
            contentBuilder.contentFor(dri, platforms.toSet(), ContentKind.Symbol, setOf(TextStyle.Monospace)) {
                text("actual typealias ")
                link(name, dri)
                text(" = ")
                signatureForProjection(bound)
            }
        }

    private fun <T : DClasslike> classlikeSignature(c: T) =
        (c as? WithExtraProperties<out DClasslike>)?.let {
            c.extra[ActualTypealias]?.let {
                contentBuilder.contentFor(c) {
                    +regularSignature(c, platformData = c.platformData.toSet() - it.underlyingType.keys)
                    +actualTypealiasedSignature(c.dri, c.name.orEmpty(), it.underlyingType)
                }
            } ?: regularSignature(c)
        } ?: regularSignature(c)

    private fun regularSignature(c: DClasslike, platformData: Set<PlatformData> = c.platformData.toSet()) =
        contentBuilder.contentFor(c, ContentKind.Symbol, setOf(TextStyle.Monospace), platformData = platformData) {
            group(styles = setOf(TextStyle.Block)) {
                kotlinAnnotationsBlock(c)
                platformText(
                    c.visibility,
                    platformData
                ) { it.takeIf { it !in ignoredVisibilities }?.name?.let { "$it " } ?: "" }
                if (c is DClass) {
                    platformText(c.modifier, platformData) {
                        if (c.extra[AdditionalModifiers]?.content?.contains(ExtraModifiers.DATA) == true) ""
                        else it.name + " "
                    }
                }
                when (c) {
                    is DClass -> text("${c.kotlinAdditionalModifiers()}class ")
                    is DInterface -> text("interface ")
                    is DEnum -> text("enum ")
                    is DObject -> text("object ")
                    is DAnnotation -> text("annotation class ")
                }
                link(c.name!!, c.dri)
                if (c is DClass) {
                    val pConstructor = c.constructors.singleOrNull { it.extra[PrimaryConstructorExtra] != null }
                    if (pConstructor?.annotations()?.isNotEmpty() == true) {
                        text(nbsp.toString())
                        kotlinAnnotationsInline(pConstructor)
                        text("constructor")
                    }
                    list(
                        pConstructor?.parameters.orEmpty(),
                        "(",
                        ")",
                        ",",
                        pConstructor?.platformData.orEmpty().toSet()
                    ) {
                        kotlinAnnotationsInline(it)
                        text(it.name ?: "", styles = mainStyles.plus(TextStyle.Bold))
                        text(": ")
                        signatureForProjection(it.type)
                    }
                }
                if (c is WithSupertypes) {
                    c.supertypes.filter { it.key in platformData }.map { (p, dris) ->
                        list(dris, prefix = " : ", platformData = setOf(p)) {
                            link(it.sureClassNames, it, platformData = setOf(p))
                        }
                    }
                }
            }
        }

    private fun propertySignature(p: DProperty, platformData: Set<PlatformData> = p.platformData.toSet()) =
        contentBuilder.contentFor(p, ContentKind.Symbol, setOf(TextStyle.Monospace), platformData = platformData) {
            group(styles = setOf(TextStyle.Block)) {
                kotlinAnnotationsBlock(p)
                platformText(p.visibility) { it.takeIf { it !in ignoredVisibilities }?.name?.let { "$it " } ?: "" }
                platformText(p.modifier) { it.takeIf { it !in ignoredModifiers }?.name?.let { "$it " } ?: "" }
                text(p.kotlinAdditionalModifiers())
                p.setter?.let { text("var ") } ?: text("val ")
                list(p.generics, prefix = "<", suffix = "> ") {
                    +buildSignature(it)
                }
                p.receiver?.also {
                    signatureForProjection(it.type)
                    text(".")
                }
                link(p.name, p.dri)
                text(": ")
                signatureForProjection(p.type)
            }
        }

    private fun functionSignature(f: DFunction, platformData: Set<PlatformData> = f.platformData.toSet()) =
        contentBuilder.contentFor(f, ContentKind.Symbol, setOf(TextStyle.Monospace), platformData = platformData) {
            group(styles = setOf(TextStyle.Block)) {
                kotlinAnnotationsBlock(f)
                platformText(f.visibility) { it.takeIf { it !in ignoredVisibilities }?.name?.let { "$it " } ?: "" }
                platformText(f.modifier) { it.takeIf { it !in ignoredModifiers }?.name?.let { "$it " } ?: "" }
                text(f.kotlinAdditionalModifiers())
                text("fun ")
                list(f.generics, prefix = "<", suffix = "> ") {
                    +buildSignature(it)
                }
                f.receiver?.also {
                    signatureForProjection(it.type)
                    text(".")
                }
                link(f.name, f.dri)
                list(f.parameters, "(", ")") {
                    kotlinAnnotationsInline(it)
                    text(it.kotlinAdditionalModifiers())
                    text(it.name!!)
                    text(": ")
                    signatureForProjection(it.type)
                }
                if (f.documentReturnType()) {
                    text(": ")
                    signatureForProjection(f.type)
                }
            }
        }

    private fun DFunction.documentReturnType() = when {
        this.isConstructor -> false
        this.type is TypeConstructor && (this.type as TypeConstructor).dri == DriOfUnit -> false
        this.type is Void -> false
        else -> true
    }

    private fun signature(t: DTypeAlias) =
        contentBuilder.contentFor(t) {
            t.underlyingType.entries.groupBy({ it.value }, { it.key }).map { (type, platforms) ->
                +contentBuilder.contentFor(
                    t,
                    ContentKind.Symbol,
                    setOf(TextStyle.Monospace),
                    platformData = platforms.toSet()
                ) {
                    platformText(t.visibility) { it.takeIf { it !in ignoredVisibilities }?.name?.let { "$it " } ?: "" }
                    text(t.kotlinAdditionalModifiers())
                    text("typealias ")
                    signatureForProjection(t.type)
                    text(" = ")
                    signatureForProjection(type)
                }
            }
        }

    private fun signature(t: DTypeParameter) = contentBuilder.contentFor(t) {
        link(t.name, t.dri)
        list(t.bounds, prefix = " : ") {
            signatureForProjection(it)
        }
    }

    private fun PageContentBuilder.DocumentableContentBuilder.signatureForProjection(p: Projection): Unit =
        when (p) {
            is OtherParameter -> text(p.name)

            is TypeConstructor -> if (p.function)
                +funType(this.mainDRI, this.mainPlatformData, p)
            else
                group(styles = emptySet()) {
                    link(p.dri.classNames.orEmpty(), p.dri)
                    list(p.projections, prefix = "<", suffix = ">") {
                        signatureForProjection(it)
                    }
                }

            is Variance -> group(styles = emptySet()) {
                text(p.kind.toString() + " ")
                signatureForProjection(p.inner)
            }

            is Star -> text("*")

            is Nullable -> group(styles = emptySet()) {
                signatureForProjection(p.inner)
                text("?")
            }

            is JavaObject -> link("Any", DriOfAny)
            is Void -> link("Unit", DriOfUnit)
            is PrimitiveJavaType -> signatureForProjection(p.translateToKotlin())
            is Dynamic -> text("dynamic")
        }

    private fun funType(dri: DRI, platformData: Set<PlatformData>, type: TypeConstructor) =
        contentBuilder.contentFor(dri, platformData, ContentKind.Symbol, setOf(TextStyle.Monospace)) {
            if (type.extension) {
                signatureForProjection(type.projections.first())
                text(".")
            }

            val args = if (type.extension)
                type.projections.drop(1)
            else
                type.projections

            text("(")
            args.subList(0, args.size - 1).forEachIndexed { i, arg ->
                signatureForProjection(arg)
                if (i < args.size - 2) text(", ")
            }
            text(") -> ")
            signatureForProjection(args.last())
        }

    private val strategy = OnlyOnce
    private val listBrackets = Tuple2('[', ']')
    private val classExtension = "::class"

    fun PageContentBuilder.DocumentableContentBuilder.kotlinAnnotationsBlock(d: Documentable) =
        annotationsBlock(d, emptySet(), strategy, listBrackets, classExtension)

    fun PageContentBuilder.DocumentableContentBuilder.kotlinAnnotationsInline(d: Documentable) =
        annotationsInline(d, emptySet(), strategy, listBrackets, classExtension)

    private fun <T : Documentable> WithExtraProperties<T>.kotlinAdditionalModifiers() =
        this.modifiers(ExtraModifiers.kotlinOnlyModifiers).toSignatureString()
}

private fun PrimitiveJavaType.translateToKotlin() = TypeConstructor(
    dri = DRI("kotlin", name.capitalize()),
    projections = emptyList()
)

val TypeConstructor.function
    get() = modifier == FunctionModifiers.FUNCTION || modifier == FunctionModifiers.EXTENSION

val TypeConstructor.extension
    get() = modifier == FunctionModifiers.EXTENSION
