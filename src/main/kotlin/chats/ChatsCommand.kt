package org.jetbrains.chats

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.requireObject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import mu.KotlinLogging
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jetbrains.ExportCommandContext
import org.jetbrains.ExportFormat
import space.jetbrains.api.runtime.BatchInfo
import space.jetbrains.api.runtime.resources.chats
import space.jetbrains.api.runtime.resources.uploads
import space.jetbrains.api.runtime.types.AllChannelsListEntry
import space.jetbrains.api.runtime.types.AttachmentInfo
import space.jetbrains.api.runtime.types.ChannelIdentifier
import space.jetbrains.api.runtime.types.ChannelItemRecord
import space.jetbrains.api.runtime.types.ChatContactDetails
import space.jetbrains.api.runtime.types.ChatMessageIdentifier
import space.jetbrains.api.runtime.types.FileAttachment
import space.jetbrains.api.runtime.types.ImageAttachment
import space.jetbrains.api.runtime.types.MessagesSorting
import space.jetbrains.api.runtime.types.UnfurlAttachment
import space.jetbrains.api.runtime.types.VideoAttachment
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

private val logger = KotlinLogging.logger("ChatExport")

class ChatsCommand : CliktCommand() {
    override fun commandHelp(context: Context): String = """
    Export all available to user chats in the following format.
    """.trimIndent()

    private val context by requireObject<ExportCommandContext>()
    private val client get() = context.spaceApiClient

    override fun run() = runBlocking {
        val exporter = when (context.format) {
            ExportFormat.Json -> JsonExporter("export/json")
        }
        suspend fun dumpMessages(channel: ExportedChannel) {
            with(exporter) {
                dump(channel, fetchMessages(channel))
            }
        }
        fetchGroupNamedChannels().forEach { dumpMessages(it) }
        fetchDMs().forEach { dumpMessages(it) }
    }

    suspend fun fetchGroupNamedChannels(): List<ExportedChannel> {
        val channels = mutableListOf<AllChannelsListEntry>()
        var offset = ""
        var hasNext = true
        while (hasNext) {
            val response = client.spaceClient.chats.channels.listAllChannels("", batchInfo = BatchInfo(offset, 50))
            channels.addAll(response.data)
            offset = response.next
            hasNext = (response.totalCount ?: 0) > channels.size
        }
        return channels.map { ExportedChannel(it.channelId, it.name, ExportedChannel.Type.GroupChannel) }
    }

    suspend fun fetchDMs(): List<ExportedChannel> {
        val channels = mutableListOf<ExportedChannel>()
        var offset = ""
        var hasNext = true
        while (hasNext) {
            val response = client.spaceClient.chats.channels.listDirectMessageChannelsAndConversations(
                BatchInfo(
                    offset,
                    50
                )
            ) {
                details {
                    user()
                    users()
                    subject()
                }
                id()
                key()
                channelType()
            }
            channels.addAll(response.data.mapNotNull {
                ExportedChannel(
                    it.id,
                    when (val details = it.details) {
                        is ChatContactDetails.Profile -> details.user.username.takeIf { it.isNotBlank() } ?: "user-${details.user.id}"
                        is ChatContactDetails.Conversation -> (details.subject
                            ?: details.users.joinToString("_") { it.username }).takeIf { it.isNotEmpty() } ?: "conversation-${it.id}"

                        else -> {
                            logger.error("${it.details::class.simpleName} invariant key=${it.key} channelType=${it.channelType}")
                            return@mapNotNull null
                        }
                    },
                    ExportedChannel.Type.DM)
            })
            offset = response.next
            hasNext = (response.totalCount ?: 0) > channels.size
        }
        return channels

    }

    suspend fun fetchMessages(channel: ExportedChannel): List<ExportedMessage> {

        var nextStartFromDate: Instant? = kotlinx.datetime.Clock.System.now()
        val messages = mutableListOf<ExportedMessage>()
        while (nextStartFromDate != null) {
            val response = client.spaceClient.chats.messages.getChannelMessages(
                ChannelIdentifier.Id(channel.id),
                MessagesSorting.FromNewestToOldest,
                startFromDate = nextStartFromDate,
                batchSize = 50
            ) {
                nextStartFromDate()
                orgLimitReached()
                messages {
                    id()
                    author()
                    time()
                    text()
                    attachments()
                    reactions {
                        emojiReactions()
                    }
                    thread()
                }
            }
            if (nextStartFromDate == response.nextStartFromDate) {
                nextStartFromDate = null
            } else {
                nextStartFromDate = response.nextStartFromDate
            }
            messages.addAll(response.messages.map { message ->
                ExportedMessage(
                    message.id,
                    message.author.name,
                    message.time,
                    message.text,
                    unfurls = message.attachments?.mapNotNull {
                        (it.details as? UnfurlAttachment)?.unfurl?.let {
                            ExportedUnfurl(
                                it.text,
                                it.link,
                                it.image
                            )
                        }
                    } ?: emptyList(),
                    attachments = message.attachments?.mapNotNull { info ->
                        when (val details = info.details) {
                            is ImageAttachment -> ExportedAttachment(attachmentUrl(channel, message, details.id), details.name ?: details.id)
                            is VideoAttachment -> ExportedAttachment(attachmentUrl(channel, message, details.id), details.name ?: details.id)
                            is FileAttachment -> ExportedAttachment(attachmentUrl(channel, message, details.id), details.filename)
                            else -> null
                        }
                    } ?: emptyList(),
                    reactions = (message.reactions?.emojiReactions?.map { ExportedReaction(it.emoji, it.count) })
                        ?: emptyList(),
                    thread = message.thread?.let {
                        fetchMessages(
                            ExportedChannel(
                                it.id,
                                "thread",
                                ExportedChannel.Type.Thread
                            )
                        )
                    } ?: emptyList()
                )
            })
            if (response.orgLimitReached) {
                error("Org Limit reached")
            }
        }
        return messages
    }

    private suspend fun attachmentUrl(channel: ExportedChannel, message: ChannelItemRecord, id: String): String {
        return client.spaceClient.uploads.chat.publicUrl.getPublicUrl(
            ChannelIdentifier.Id(channel.id),
            ChatMessageIdentifier.InternalId(message.id),
            id
        )
    }
}

fun ensureDirectoryExists(path: String) {
    val directory = File(path)
    if (!directory.exists()) {
        if (!directory.mkdirs()) {
            logger.error("Failed to create directory $path.")
        }
    }
}

interface ChatExporter {
    suspend fun CoroutineScope.dump(channel: ExportedChannel, messages: List<ExportedMessage>)

    fun downloadFile(url: String, outputFileName: String) {
        val client = OkHttpClient()
        val request = Request.Builder().url(url).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Failed to download file: $response")

            response.body?.byteStream()?.use { input ->
                FileOutputStream(File(outputFileName)).use { output ->
                    input.copyTo(output)
                }
            }
        }
    }
}

class JsonExporter(val basePath: String) : ChatExporter {
    override suspend fun CoroutineScope.dump(channel: ExportedChannel, messages: List<ExportedMessage>) {
        require(channel.name.isNotEmpty()) { "Channel name cannot be empty" }
        val jsonString = Json.encodeToString(messages)
        val subdirectory = when (channel.type) {
            ExportedChannel.Type.DM -> "dm"
            ExportedChannel.Type.GroupChannel -> "group"
            else -> error(channel.type.toString() + " is not supported")
        }
        val path = "$basePath/$subdirectory/${channel.name}"
        ensureDirectoryExists(path)
        File(path, "history.json").writeText(jsonString)
        logger.info("Exported ${messages.size} messages to $path/history.json")
        messages.map { it.attachments }.flatten().map {
            async(Dispatchers.IO) {
                logger.info("Downloading ${it.name}")
                downloadFile(it.url, "$path/${it.name}")
            }
        }.awaitAll()
    }
}

@Serializable
data class ExportedChannel(
    val id: String,
    val name: String,
    val type: Type
) {
    enum class Type {
        DM,
        GroupChannel,
        Thread
    }
}

@Serializable
data class ExportedMessage(
    val id: String,
    val author: String,
    val time: Long,
    val text: String,
    val unfurls: List<ExportedUnfurl> = emptyList(),
    val attachments: List<ExportedAttachment> = emptyList(),
    val reactions: List<ExportedReaction> = emptyList(),
    val thread: List<ExportedMessage> = emptyList()
)

@Serializable
data class ExportedAttachment(val url: String, val name: String)

@Serializable
data class ExportedReaction(val emoji: String, val count: Int)

@Serializable
data class ExportedUnfurl(val text: String, val link: String, val image: String? = null)


