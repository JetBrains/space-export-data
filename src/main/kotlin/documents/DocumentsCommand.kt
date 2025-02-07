package org.jetbrains.documents

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.requireObject
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.enum
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import mu.KotlinLogging
import org.jetbrains.ExportCommandContext
import org.jetbrains.downloadFile
import org.jetbrains.loadBatch
import space.jetbrains.api.runtime.resources.projects
import space.jetbrains.api.runtime.resources.teamDirectory
import space.jetbrains.api.runtime.types.*
import java.nio.file.Files
import java.nio.file.Path

private val logger = KotlinLogging.logger("DocumentsExport")

class DocumentsCommand : CliktCommand() {
    override fun commandHelp(context: Context): String = """
    Export all available to user documents.
    """.trimIndent()

    enum class DocumentsScope {
        All, Personal, Project
    }

    private val scope by option(
        help = "Documents scope",
        envvar = "SCOPE",
    ).enum<DocumentsScope>().default(DocumentsScope.All)

    private val projectKey: String by option(
        help = "Project key",
        envvar = "PROJECT_KEY",
    ).default("")

    private val context by requireObject<ExportCommandContext>()
    private val client get() = context.spaceApiClient

    override fun run() = runBlocking {
        val basePath = Path.of("export/documents")

        if (scope != DocumentsScope.Project) {
            logger.info { "Exporting personal documents" }
            val personalPath = basePath.resolve("personal")
            exportPersonalFolder(FolderIdentifier.Root, personalPath)
        }

        if (scope != DocumentsScope.Personal) {
            val projectKeys = getProjectKeys(projectKey)

            logger.info { "Exporting project documents from $projectKeys" }

            for (projectKey in projectKeys) {
                val projectPath = basePath.resolve("project/$projectKey")
                val project = ProjectIdentifier.Key(projectKey)
                exportProjectFolder(project, FolderIdentifier.Root, projectPath)
            }
        }
    }

    private suspend fun getProjectKeys(projectKey: String): List<String> {
        return if (projectKey.isNotEmpty()) {
            listOf(projectKey)
        } else {
            loadBatch { batch ->
                client.spaceClient.projects
                    .getAllProjectsWithRightCode(
                        right = PermissionIdentifier.ViewDocuments,
                        batchInfo = batch,
                    )
            }.map { it.key.key.lowercase() }
        }
    }

    private suspend fun exportPersonalFolder(
        folder: FolderIdentifier,
        folderPath: Path
    ) {
        logger.debug { "Exporting personal documents in folder $folder to $folderPath" }

        // Export documents
        val documents = loadBatch { batch ->
            client.spaceClient.teamDirectory.profiles.documents.folders.documents.listDocumentsInFolder(
                profile = ProfileIdentifier.Me,
                folder = folder,
                batchInfo = batch
            ) {
                id()
                title()
                bodyType()
                documentBody()
            }
        }

        if (documents.isNotEmpty()) {
            withContext(Dispatchers.IO) {
                Files.createDirectories(folderPath)
            }
        }

        for (document in documents) {
            exportDocument(
                document = document,
                folderPath = folderPath
            )
        }

        // Export subfolders
        val subfolders = loadBatch { batch ->
            client.spaceClient.teamDirectory.profiles.documents.folders.subfolders.listSubfolders(
                profile = ProfileIdentifier.Me,
                folder = folder,
                batchInfo = batch
            )
        }

        for (subfolder in subfolders) {
            exportPersonalFolder(
                folder = FolderIdentifier.Id(subfolder.id),
                folderPath = folderPath.resolve(subfolder.name.replace('/', '_'))
            )
        }
    }

    private suspend fun exportProjectFolder(
        project: ProjectIdentifier,
        folder: FolderIdentifier,
        folderPath: Path
    ) {
        logger.debug { "Exporting project $project documents in folder $folder to $folderPath" }

        // Export documents
        val documents = loadBatch { batch ->
            client.spaceClient.projects.documents.folders.documents.listDocumentsInFolder(
                project = project,
                folder = folder,
                batchInfo = batch
            ) {
                id()
                title()
                bodyType()
                documentBody()
            }
        }

        if (documents.isNotEmpty()) {
            withContext(Dispatchers.IO) {
                Files.createDirectories(folderPath)
            }
        }

        for (document in documents) {
            exportDocument(
                document = document,
                folderPath = folderPath
            )
        }

        // Export subfolders
        val subfolders = loadBatch { batch ->
            client.spaceClient.projects.documents.folders.subfolders.listSubfolders(
                project = project,
                folder = folder,
                batchInfo = batch
            )
        }

        for (subfolder in subfolders) {
            exportProjectFolder(
                project = project,
                folder = FolderIdentifier.Id(subfolder.id),
                folderPath = folderPath.resolve(subfolder.name.replace('/', '_'))
            )
        }
    }

    private suspend fun exportDocument(document: Document, folderPath: Path) {
        logger.debug { "Exporting document ${document.title} to $folderPath" }

        when (document.bodyType) {
            DocumentBodyType.FILE -> {
                val documentPath = folderPath.resolve(document.title.replace('/', '_'))
                val fileUrl = client.spaceClient.server.serverUrl + "/drive/files/" + document.id
                val accessToken = client.spaceClient.token().accessToken

                downloadFile(fileUrl, documentPath.toFile()) {
                    header("Authorization", "Bearer $accessToken")
                }
            }

            DocumentBodyType.TEXT -> {
                val documentPath = folderPath.resolve(document.title.replace('/', '_') + ".md")

                @Suppress("DEPRECATION")
                val documentBody = document.documentBody as TextDocument

                withContext(Dispatchers.IO) {
                    Files.writeString(documentPath, documentBody.text)
                }
            }

            DocumentBodyType.CHECKLIST -> {
                logger.info { "Skipping checklist document ${document.title}" }
            }

            null -> {}
        }
    }
}