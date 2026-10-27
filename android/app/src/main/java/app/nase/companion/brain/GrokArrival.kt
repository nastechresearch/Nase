package app.nase.companion.brain

import app.nase.companion.net.CleartextPolicy

/** How a Grok Bot template link sets the brain and the relay. */
object GrokArrival {
    fun matches(client: String?): Boolean = GrokBot.isTemplateToken(client) || GrokBot.isGrok(client)

    /** Cloud unless the link itself names a different relay. */
    fun useCloudRelay(relay: String?): Boolean =
        relay.isNullOrBlank() || CleartextPolicy.isCloud(relay)
}
