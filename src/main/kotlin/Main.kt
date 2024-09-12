package org.jetbrains

import com.github.ajalt.clikt.core.subcommands
import org.jetbrains.chats.ChatsCommand

fun main(args: Array<String>) = SpaceExportCommand()
    .subcommands(ChatsCommand())
    .main(args)