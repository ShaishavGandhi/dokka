#Guide to Dokka Plugin development

## Configuration

Dokka requires configured kotlin plugin and dokka-core dependency. 

```kotlin
plugins {
    kotlin("jvm") version <kotlin_version>
}

dependencies {
    implementation("org.jetbrains.dokka:dokka-core:<dokka_version>>)
}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "1.8"
}
```

## Building sample plugin

In order to load a plugin into dokka, your class must extend `DokkaPlugin` class. All instances are automatically 
loaded during dokka setup using `ServiceLoader`.

In order to extend core functionality, dokka provides a set of entry points, 
for which user can create their own implementations. They must be delegated using 
`DokkaPlugin.extending(isFallback: Boolean = false, definition: ExtendingDSL.() -> Extension<T>)` function,
that returns a delegate `ExtensionProvider` with supplied definition. 

The function receives optional `isFallback` parameter 
that determines whether the provided implementation can be overriden by other plugin.   

To create a definition, you can use one of two infix functions`with(T)` or `providing( (DokkaContext) -> T)` where `T` 
is the type of an extended endpoint. You can also use infix functions:
* `applyIf( () -> Boolean )` to add additional condition whether or not the extension should be used
* `order((OrderDsl.() -> Unit))` to determine if your extension must be created before or after another particular extension

Following sample provides custom translator object as a `DokkaCore.sourceToDocumentableTranslator`

```kotlin
package org.jetbrains.dokka.sample

import org.jetbrains.dokka.plugability.DokkaPlugin

class SamplePlugin : DokkaPlugin() {
    extension by extending {
        DokkaCore.sourceToDocumentableTranslator with CustomSourceToDocumentableTranslator
    }
}

object CustomSourceToDocumentableTranslator: SourceToDocumentableTranslator {
    override fun invoke(sourceSet: SourceSetData, context: DokkaContext): DModule
}
```

## Dokka Data Model

There a four data models that dokka uses: Documentable Model, Documentation Model, Page Model and Content Model.

### Documentable Model

Documentable model represents parsed data, returned by compilator analysis. It retains basic order structure of parsed `Psi` or `Descriptor` models.

After creation, it is a collection of trees, each with `DModel` as a root. After the Merge step, all trees are folded into one. 

The main building block of this model is `Documentable` class, that is a base class for all more specific types that represents elements of parsed Kotlin and Java classes with pretty self-explanatory names:
`DPackage`, `DFunction` and so on. `DClasslike` is a base for class-like elements, such as Classes, Enums, Interfaces and so on.

There are three non-documentable classes important for the model: `DRI`, `SourceSetDependent` and `Extra`.

* `DRI` (Dokka Resource Identifier) is an unique value that identifies specific `Documentable`. All references to other documentables different than direct ownership are described using DRIs. For example, `DFunction` with parameter of type `X` has only X's DRI, not the actual reference to X's Documentable object.
* `SourceSetDependent` is a map that handles multiplatform data, by connecting platform-specific data, declared with either `expect` or `actual` modifier, to a particular Source Set
* `ExtraProperty` is used to store any additional information that falls outside of Documentable Model. It is highly recommended to use Extras to provide any additional information when creating own Dokka plugins. To declare a new extra, you need to implement `ExtraProperty` interface:
```kotlin
interface ExtraProperty<in C : Any> {
    interface Key<in C : Any, T : Any> {
        fun mergeStrategyFor(left: T, right: T): MergeStrategy<C> = MergeStrategy.Fail {
            throw NotImplementedError("Property merging for $this is not implemented")
        }
    }

    val key: Key<C, *>
}
```
It is advised to use following pattern when declaring new extras:
```kotlin
data class CustomExtra( [any values relevant to your extra ] ): ExtraProperty<Documentable> {
    companion object : CustomExtra.Key<Documentable, CustomExtra>
    override val key: CustomExtra.Key<Documentable, *> = CustomExtra
}
```
Merge strategy for extras is invoked only if merged objects have different values for same Extra. If you don't expect it to happen, you can ommit implementing `mergeStrategyFor` function.
All extras are stored in `PropertyContainer`.

### Documentation Model

Documentation model is used along Documentable Model to store data obtained by parsing code commentaries.

There are three important classes here:

* `DocTag` describes a specific documentation syntax element, for example header, footer, list, link, raw text, paragraph, etc.
* `TagWrapper` described a whole comment description or a specific comment tag, for example @See, @Returns, @Author, and holds consisting `DocTag` elements 
* `DocumentationNode` acts as a container for `TagWrappers` for a specific `Documentable`

DocumentationNodes are references by a specific `Documentable`

### Page Model

Page Model represents the structure of future generated documentation pages and is independent of the final output format, which each node corresponding to exactly one output file. `Rendered` is processing each page separately.
Subclasses of `PageNode` represents different kinds of rendered pages for Modules, Packages, Classes etc.

The Page Model is a tree structure, with `RootPageNode` as a root. 

### Content Model

Content Model describes how the actual page content is presented. It organizes it's structure into groups, tables, links, etc. Each node is identified by unique `DCI` (Dokka Content Identifier) and all references to other nodes different than direct ownership are described using DCIs.

`DCI` aggregates `DRI`s of all `Documentables` that make up specific `ContentNode`. 

Also, all `ExtraProperty` info from consisting `Documentable`s is propagated into Content Model and available for `Renderer`.

## Extension points

### Core extension points

We will discuss all base extension points along with the steps, that `DokkaGenerator` does to build a documentation.

#### Setting up Kotlin and Java analysis process and initializing plugins

The provided Maven / CLI / Gradle configuration is read.Then, all the `DokkaPlugin` classes are loaded and the extensions are created.
 
 No entry points here.

#### Creating documentation models

The documentation models are created.

This step uses `DokkaCore.sourceToDocumentableTranslator` entry point.
All extensions registered using this entry point will be invoked.
Each of them is required to implement `SourceToDocumentableTranslator` interface:

```kotlin
interface SourceToDocumentableTranslator {
    fun invoke(sourceSet: SourceSetData, context: DokkaContext): DModule
}
```
By default, two translators are created:
* `DefaultDescriptorToDocumentableTranslator` that handles Kotlin files
* `DefaultPsiToDocumentableTranslator` that handles Java files

After this step, all data from different source sets and languages are kept separately.

#### Pre-merge documentation transform

Here you can apply any transformation to model data before different source sets are merged.

This step uses `DokkaCore.preMergeDocumentableTransformer` entry point.
All extensions registered using this entry point will be invoked.
Each of them is required to implement `PreMergeDocumentableTransformer` interface:

```kotlin
interface PreMergeDocumentableTransformer {
    operator fun invoke(modules: List<DModule>, context: DokkaContext): List<DModule>
}
```
By default, three transformers are created:
* `DocumentableVisibilityFilter` that, depending on configuration, filters out all private members from declared packages
* `ActualTypealiasAdder` that handles Kotlin typealiases
* `ModuleAndPackageDocumentationTransformer` that creates documentation content for models and packages itself 

#### Merging

All `DModule` instances are merged into one.

This step uses `DokkaCore.documentableMerger` entry point.
It is required to have exactly one extension registered for this entry point.
Having more will trigger an error, unless all except one will have parameter `isFallback` set to true, in which case the non-fallback extension will be used.

The extension is required to implement `DocumentableMerger` interface:

```kotlin
interface DocumentableMerger {
    operator fun invoke(modules: Collection<DModule>, context: DokkaContext): DModule
}
```

By default, `DefaultDocumentableMerger` is created. This extension is a fallback, so it could be replaced by a custom non-fallback one.

#### Merged data transformation

You can apply any transformation to already merged data

This step uses `DokkaCore.documentableTransformer` entry point.
All extensions registered using this entry point will be invoked.
Each of them is required to implement `DocumentableTransformer` interface:

```kotlin
interface DocumentableTransformer {
    operator fun invoke(original: DModule, context: DokkaContext): DModule
}
```

By default, `InheritorsExtractorTransformer` is created, that extracts inherited classes data across source sets and creates inheritance map.

#### Creating page models

The documentable model is translated into page format, that aggregates all tha data that will be available for different pages of documentation.

This step uses `DokkaCore.documentableToPageTranslator` entry point.
It is required to have exactly one extension registered for this entry point.
Having more will trigger an error, unless all except one will have parameter `isFallback` set to true, in which case the non-fallback extension will be used.

The extension is required to implement `DocumentableToPageTranslator` interface:

```kotlin
interface DocumentableToPageTranslator {
    operator fun invoke(module: DModule): ModulePageNode
}
```

By default, `DefaultDocumentableToPageTranslator` is created.  This extension is a fallback, so it could be replaced by a custom non-fallback one.

#### Transforming page models

You can apply any transformations to paged data.

This step uses `DokkaCore.pageTransformer` entry point.
All extensions registered using this entry point will be invoked.
Each of them is required to implement `PageTransformer` interface:

```kotlin
interface PageTransformer {
    operator fun invoke(input: RootPageNode): RootPageNode
}
```
By default, two transformers are created:
* `PageMerger` merges some pages depending on `MergeStrategy`
* `DeprecatedStrikethroughTransformer` marks all deprecated members on every page

#### Rendering

All pages are rendered to desired format.

This step uses `DokkaCore.renderer` entry point.
It is required to have exactly one extension registered for this entry point.
Having more will trigger an error, unless all except one will have parameter `isFallback` set to true, in which case the non-fallback extension will be used.
                                    
The extension is required to implement  `Renderer` interface:

```kotlin
interface Renderer {
    fun render(root: RootPageNode)
}
```

By default, only `HTMLRenderer`, that extends basic `DefaultRenderer`, is created, but it will be registered only if configuration parameter `format` is set to `html`.

### Default extensions' extension points

Default core extension points already have an implementation for providing basic dokka functionality. All of them are declared in `DokkaBase` plugin.
If you don't want this default extensions to load, all you need to do is write a custom configuration to disable loading said plugin. You will then need to implement extensions for all core extension points. 

`DokkaBase` also register several new extension points, with which you can change default behaviour of `DokkaBase` extensions. In order to use them, you need to add `dokka-base` to you dependencies:

```kotlin
    implementation("org.jetbrains.dokka:dokka-base:<dokka_version>")
```

Then, you need to obtain `DokkaBase` instance using `plugin` function:

```kotlin
class SamplePlugin : DokkaPlugin() {

    val dokkaBase = plugin<DokkaBase>()

    val extension by extending {
        dokkaBase.pageMergerStrategy with SamplePageMergerStrategy order {
           before(dokkaBase.fallbackMerger)
       }
    }
}

object SamplePageMergerStrategy: PageMergerStrategy {
    override fun tryMerge(pages: List<PageNode>, path: List<String>): List<PageNode> {
        ...
    }

}
```

#### Following extension points are available with base plugin:

| Entry point | Function | Required interface | Used by | Singular | Preregistered extensions
|---|:---|:---:|:---:|:---:|:---:|
| `pageMergerStrategy` |  determines what kind of pages should be merged | `PageMergerStrategy` | `PageMerger` | false | `FallbackPageMergerStrategy` `SameMethodNamePageMergerStrategy`   |
| `commentsToContentConverter` | transforms comment model into page content model | `CommentsToContentConverter` | `DefaultDocumentableToPageTransformer` `SignatureProvider` | true | `DocTagToContentConverter`(fallback) |
| `signatureProvider`  | provides representation of methods signatures | `SignatureProvider` | `DefaultDocumentableToPageTransformer` | true | `KotlinSignatureProvider`(fallback) |
| `locationProviderFactory` | provides `LocationProvider` instance that returns paths for requested elements | `LocationProviderFactory` | `DefaultRenderer` `HTMLRenderer` `PackageListService` | true | `DefaultLocationProviderFactory`(fallback) which returns `DefaultLocationProvider` |
| `externalLocationProviderFactory` | provides `ExternalLocationProvider` instance that returns paths for elements that are not part of generated documentation | `ExternalLocationProviderFactory` | `DefaultLocationProvider` | false | `JavadocExternalLocationProviderFactory` `DokkaExternalLocationProviderFactory` |
| `outputWriter` | writes rendered pages files | `OutputWriter` | `DefaultRenderer` `HTMLRenderer` | true | `FileWriter`(fallback)|
| `htmlPreprocessors` | transforms page content before HTML rendering | `PageTransformer`| `DefaultRenderer` `HTMLRenderer` | false | `RootCreator` `SourceLinksTransformer` `NavigationPageInstaller` `SearchPageInstaller` `ResourceInstaller` `StyleAndScriptsAppender` `PackageListCreator` |
| `samplesTransformer` | transforms content for code samples for HTML rendering | `SamplesTransformer` | `HTMLRenderer` | true | `DefaultSamplesTransformer` |


