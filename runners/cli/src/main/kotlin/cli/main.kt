package org.jetbrains.dokka

import kotlinx.cli.*
import org.jetbrains.dokka.DokkaConfiguration.ExternalDocumentationLink
import org.jetbrains.dokka.utilities.DokkaConsoleLogger
import java.io.File
import java.io.FileNotFoundException
import java.net.MalformedURLException
import java.net.URL
import java.nio.file.Files
import java.nio.file.Paths

open class GlobalArguments() : DokkaConfiguration {

    val parser = ArgParser("globalArguments")

    override val outputDir by parser.option(ArgType.String, description = "Output directory path").required()
    override val format by parser.option(
        ArgType.String,
        description = "Output format (text, html, gfm, jekyll, kotlin-website)"
    ).required()
    override val pluginsClasspath by parser.option(ArgTypeFile, description = "List of jars with dokka plugins")
        .vararg().also {
            Paths.get("./dokka-base.jar").toAbsolutePath().normalize().run {
                if (Files.exists(this)) (it.value as MutableList<File>).add(this.toFile())
                else throw FileNotFoundException("Dokka base plugin is not found! Make sure you placed 'dokka-base.jar' containing base plugin along the cli jar file")
            }
        }
    override val generateIndexPages by parser.option(ArgType.Boolean, description = "Generate index pages")
        .default(false)
    override val cacheRoot: String? by parser.option(
        ArgType.String,
        description = "Path to cache folder, or 'default' to use ~/.cache/dokka, if not provided caching is disabled"
    )
    override val impliedPlatforms: List<String> = emptyList()
    override val passesConfigurations by parser.option(
        ArgTypeArguments,
        description = "Single dokka pass",
        fullName = "pass"
    ).multiple()
    override val pluginsConfiguration: Map<String, String> = mutableMapOf()
}

class Arguments(val parser: DokkaArgumentsParser) : DokkaConfiguration.PassConfiguration {

    val parser = ArgParser("passConfiguration")

    override val moduleName by parser.option(
        ArgType.String,
        description = "Name of the documentation module",
        fullName = "module"
    ).default("")
    override val classpath by parser.option(ArgType.String, description = "Classpath for symbol resolution").vararg()
    override val sourceRoots by parser.option(
        ArgType.SourceRoot,
        description = "Source file or directory (allows many paths separated by the system path separator)",
        fullName = "src"
    ).vararg()
    override val samples by parser.option(Arg.String, description = "Source root for samples").vararg()
    override val includes by parser.option(
        Arg.String,
        description = "Markdown files to load (allows many paths separated by the system path separator)"
    ).vararg()
    override val includeNonPublic by parser.option(ArgType.Boolean, description = "Include non public")
    override val includeRootPackage by parser.option(ArgType.Boolean, description = "Include non public")
    override val reportUndocumented by parser.option(ArgType.Boolean, description = "Report undocumented members")
    override val skipEmptyPackages by parser.option(
        ArgType.Boolean,
        description = "Do not create index pages for empty packages"
    )
    override val skipDeprecated by parser.option(ArgType.Boolean, description = "Do not output deprecated members")
    override val jdkVersion by parser.option(
        ArgType.Int,
        description = "Version of JDK to use for linking to JDK JavaDoc"
    ).default(8)
    override val languageVersion by parser.option(
        ArgType.String,
        description = "Language Version to pass to Kotlin analysis"
    )
    override val apiVersion by parser.option(
        ArgType.String,
        description = "Kotlin Api Version to pass to Kotlin analysis"
    )
    override val noStdlibLink by parser.option(ArgType.Boolean, description = "Disable documentation link to stdlib")
    override val noJdkLink by parser.option(ArgType.Boolean, description = "Disable documentation link to JDK")
    override val suppressedFiles by parser.option(ArgType.String, description = "Paths to files to be supperessed")
        .vararg()
    override val sinceKotlin by parser.option(
        ArgType.Boolean,
        description = "Kotlin Api version to use as base version, if none specified"
    )
    override val collectInheritedExtensionsFromLibraries by parser.option(
        ArgType.Boolean,
        description = "Search for applicable extensions in libraries"
    )
    override val analysisPlatform: Platform by parser.singleOption(
        ArgTypePlatform,
        description = "Platform for analysis"
    ).default(Platform.DEFAULT)
    override val targets by parser.option(ArgType.String, description = "Generation targets").vararg()
    override val perPackageOptions by parser.option(
        ArgType.PackageOptions,
        description = "List of package passConfiguration in format \"prefix,-deprecated,-privateApi,+warnUndocumented,+suppress;...\" "
    ).vararg().default(mutableListOf())


    override val externalDocumentationLinks by parser.option(
        ArgTypeExternalDocumentationLink,
        description = "External documentation links in format url^packageListUrl^^url2..."
    ).vararg().default(mutableListOf())

    override val sourceLinks by parser.option(
        ArgTypeSourceLinkDefinition,
        description = "Mapping between a source directory and a Web site for browsing the code",
        fullName = "srcLink"
    ).vararg()
}

object MainKt {

    fun defaultLinks(config: DokkaConfiguration.PassConfiguration): MutableList<ExternalDocumentationLink> =
        mutableListOf<ExternalDocumentationLink>().apply {
            if (!config.noJdkLink)
                this += DokkaConfiguration.ExternalDocumentationLink
                    .Builder("https://docs.oracle.com/javase/${config.jdkVersion}/docs/api/")
                    .build()

            if (!config.noStdlibLink)
                this += DokkaConfiguration.ExternalDocumentationLink
                    .Builder("https://kotlinlang.org/api/latest/jvm/stdlib/")
                    .build()
        }

    fun parseLinks(links: String): List<ExternalDocumentationLink> {
        val (parsedLinks, parsedOfflineLinks) = links.split("^^")
            .map { it.split("^").map { it.trim() }.filter { it.isNotBlank() } }
            .filter { it.isNotEmpty() }
            .partition { it.size == 1 }

        return parsedLinks.map { (root) -> ExternalDocumentationLink.Builder(root).build() } +
                parsedOfflineLinks.map { (root, packageList) ->
                    val rootUrl = URL(root)
                    val packageListUrl =
                        try {
                            URL(packageList)
                        } catch (ex: MalformedURLException) {
                            File(packageList).toURI().toURL()
                        }
                    ExternalDocumentationLink.Builder(rootUrl, packageListUrl).build()
                }
    }

    @JvmStatic
    fun entry(configuration: DokkaConfiguration) {
        val generator = DokkaGenerator(configuration, DokkaConsoleLogger)
        generator.generate()
        DokkaConsoleLogger.report()
    }

    fun createConfiguration(args: Array<String>): GlobalArguments {
        val parseContext = ParseContext()
        val parser = DokkaArgumentsParser(args, parseContext)
        val configuration = GlobalArguments(parser)

        parseContext.cli.singleAction(
            listOf("-globalPackageOptions"),
            "List of package passConfiguration in format \"prefix,-deprecated,-privateApi,+warnUndocumented,+suppress;...\" "
        ) { link ->
            configuration.passesConfigurations.all {
                it.perPackageOptions.toMutableList().addAll(parsePerPackageOptions(link))
            }
        }

        parseContext.cli.singleAction(
            listOf("-globalLinks"),
            "External documentation links in format url^packageListUrl^^url2..."
        ) { link ->
            configuration.passesConfigurations.all {
                it.externalDocumentationLinks.toMutableList().addAll(parseLinks(link))
            }
        }

        parseContext.cli.repeatingAction(
            listOf("-globalSrcLink"),
            "Mapping between a source directory and a Web site for browsing the code"
        ) {
            val newSourceLinks = if (it.isNotEmpty() && it.contains("="))
                listOf(SourceLinkDefinitionImpl.parseSourceLinkDefinition(it))
            else {
                if (it.isNotEmpty()) {
                    DokkaConsoleLogger.warn("Invalid -srcLink syntax. Expected: <path>=<url>[#lineSuffix]. No source links will be generated.")
                }
                listOf()
            }

            configuration.passesConfigurations.all {
                it.sourceLinks.toMutableList().addAll(newSourceLinks)
            }
        }
        parser.parseInto(configuration)
        configuration.passesConfigurations.forEach {
            it.externalDocumentationLinks.addAll(defaultLinks(it))
        }
        return configuration
    }

    @JvmStatic
    fun main(args: Array<String>) {
        val configuration = createConfiguration(args)

        entry(configuration)
    }
}


object ArgTypeFile : ArgType<File>(true) {
    override fun convert(value: kotlin.String, name: kotlin.String): File = File(value)
}

object ArgTypeSourceRoot : ArgType<SourceRoot>(true) {
    override fun convert(value: kotlin.String, name: kotlin.String): SourceRoot = SourceRootImpl(value)
}

object ArgTypePlatform : ArgType<Platform>(true) {
    override fun convert(value: kotlin.String, name: kotlin.String): Platform = Platform.fromString(value)
}

object ArgTypePackageOptions : ArgType<PackageOptions>(true) {
    override fun convert(value: kotlin.String, name: kotlin.String): PackageOptions = parsePerPackageOptions(value)
}

object ArgTypeExternalDocumentationLink : ArgType<ExternalDocumentationLink>(true) {
    override fun convert(value: kotlin.String, name: kotlin.String): ExternalDocumentationLink = MainKt.parseLink(value)
}

object ArgTypeSourceLinkDefinition : ArgType<SourceLinkDefinition>(true) {
    override fun convert(value: kotlin.String, name: kotlin.String): SourceLinkDefinition =
        if (value.isNotEmpty() && value.contains("="))
            SourceLinkDefinitionImpl.parseSourceLinkDefinition(value)
        else {
            throw IllegalArgumentException("Warning: Invalid -srcLink syntax. Expected: <path>=<url>[#lineSuffix]. No source links will be generated.")
        }
}