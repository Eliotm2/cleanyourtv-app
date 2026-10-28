// Fork (Thomas): a name after the TV's greeting ("BONSOIR, THOMAS"), typed in Manage › Home screen.
// The launcher already reads it (greeting_name); this keeps it on the phone and adds it to every look
// sent to the TV (Look.encode), whatever path sends it: Home screen, Look, Setup, Modules.
// Until a name was saved once, looks go out unchanged; saved empty, the key is left out (greeting alone).

package tv.cleanyour.core.flows

import tv.cleanyour.core.spec.LauncherConfigDoc
import tv.cleanyour.core.store.DocumentStore

object ForkGreeting {
    const val PATH = "fork-greeting-name.txt"
    const val MAX = 40

    // Set once at start (AppGraph); null in tests that do not ask for it, so their bytes stay upstream's.
    @Volatile var store: DocumentStore? = null

    // The saved name, "" when saved empty, null when never saved.
    suspend fun load(): String? = store?.let { s -> runCatching { s.read(PATH) { it.decodeToString() } }.getOrNull() }?.let(::clean)

    suspend fun save(name: String) {
        store?.write(PATH, clean(name).encodeToByteArray())
    }

    // The doc as sent: greeting_name set to the saved name, or left out when it was saved empty.
    suspend fun apply(doc: LauncherConfigDoc): LauncherConfigDoc {
        val name = load() ?: return doc
        return doc.copy(greetingName = name.ifEmpty { null })
    }

    // One line, no control characters, at most MAX characters (the launcher uppercases it).
    fun clean(name: String): String = name.filter { !it.isISOControl() }.trim().take(MAX).trim()
}
