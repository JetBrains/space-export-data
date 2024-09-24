package org.jetbrains

import com.github.ajalt.clikt.core.subcommands
import org.jetbrains.chats.ChatsCommand
import org.jetbrains.documents.DocumentsCommand

fun main(args: Array<String>) = SpaceExportCommand()
    .subcommands(ChatsCommand())
    .subcommands(DocumentsCommand())
    .main(args)