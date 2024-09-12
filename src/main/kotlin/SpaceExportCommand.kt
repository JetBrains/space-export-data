package org.jetbrains

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.enum

class SpaceExportCommand : CliktCommand() {

    private val token by option(help = "API token to use for requests", envvar = "SPACE_TOKEN").required()
    private val org by option(
        help = "Server endpoint (e.g: organization.jetbrains.space)",
        envvar = "SPACE_ORG"
    ).required()

    private val format by option(
        help = "Export file format",
        envvar = "FORMAT",
    ).enum<ExportFormat>().default(ExportFormat.Json)

    override fun run() {
        currentContext.obj = ExportCommandContext(SpaceApiClient(token, org), format)
    }
}

class ExportCommandContext(val spaceApiClient: SpaceApiClient, val format: ExportFormat)