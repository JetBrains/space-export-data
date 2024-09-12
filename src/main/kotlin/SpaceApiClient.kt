package org.jetbrains

import space.jetbrains.api.runtime.SpaceClient

class SpaceApiClient(token: String, org: String) {
    private val organizationUrl = if (org.startsWith("http"))
        org
    else if (org.endsWith("jetbrains.space"))
        "https://$org"
    else "https://$org.jetbrains.space"

    val spaceClient = SpaceClient(
        organizationUrl,
        token
    )
}