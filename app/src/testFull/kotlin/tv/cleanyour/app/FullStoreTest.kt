package tv.cleanyour.app

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import java.io.ByteArrayInputStream
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import tv.cleanyour.core.flows.StoreRules
import tv.cleanyour.core.model.EnabledState
import tv.cleanyour.core.model.PackageName
import tv.cleanyour.core.model.PackageState
import tv.cleanyour.core.planner.PlanRequest
import tv.cleanyour.core.planner.PlanResult
import tv.cleanyour.core.registry.HttpFetcher
import tv.cleanyour.core.registry.HttpResponse
import tv.cleanyour.core.model.Op
import tv.cleanyour.core.trust.DownloadHosts
import tv.cleanyour.testing.fixtures.Samples
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

// The GitHub and F-Droid build (STORES-PLAN 1): the update check, the add-ons, the GitHub hosts
// and the "updates" choice, exactly as before the store flavours.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class FullStoreTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val graphs = mutableListOf<AppGraph>()
    private fun graph(): AppGraph = AppGraph(context).also { graphs += it }

    @After fun closeGraphs() { graphs.forEach { it.close() } }

    private class OneAnswer(val code: Int, val body: String) : HttpFetcher {
        val urls = mutableListOf<String>()
        override suspend fun get(url: String, etag: String?, rangeFrom: Long?): HttpResponse {
            urls += url
            return HttpResponse(code, null, ByteArrayInputStream(body.encodeToByteArray()), body.length.toLong())
        }
    }

    @Test fun thisIsTheFullBuild() {
        assertEquals("full", Store.flavour)
        assertEquals(StoreRules.FULL, Store.rules)
        assertEquals(DownloadHosts.ALL, Store.hosts)
        assertTrue(Store.updatesMayGoOff)
        val g = graph()
        assertSame(Store, g.store)
        assertIs<InstallableOnlyFetcher>(g.seams.fetcher)
        assertEquals(StoreRules.FULL, g.manageDeps.store)
        assertEquals(StoreRules.FULL, g.setupDeps.store)
        for (h in DownloadHosts.ALL) assertTrue(DownloadHosts.isAllowed("https://$h/x", Store.hosts), h)
    }

    // Fork (Thomas): the check reads the fork's own releases, signed with the fork's key.
    @Test fun theUpdateCheckAsksTheForksLatestRelease() = runBlocking {
        val newer = OneAnswer(200, """{"tag_name": "v0.4.0-thomas.2", "name": "x"}""")
        assertEquals("0.4.0-thomas.2", Store.updateChecker(newer).latestNewerThan("0.4.0-thomas.1"))
        assertEquals(listOf("https://api.github.com/repos/Eliotm2/cleanyourtv-app/releases/latest"), newer.urls)
        assertEquals("", Store.updateChecker(OneAnswer(200, """{"tag_name":"v0.4.0-thomas.1"}""")).latestNewerThan("0.4.0-thomas.1"))
        assertEquals(null, Store.updateChecker(OneAnswer(404, "")).latestNewerThan("0.4.0-thomas.1"))
        assertEquals("https://github.com/Eliotm2/cleanyourtv-app/releases/latest", Store.releasePage)
    }

    @Test fun theUpstreamVersionComesFirstThenTheForksNumber() {
        assertTrue(Store.forkNewer("3.2-thomas.1", "0.4.6.1-thomas.1"))   // numbered after the launcher from 3.2-thomas.1
        assertTrue(Store.forkNewer("3.2-thomas.2", "3.2-thomas.1"))
        assertTrue(Store.forkNewer("0.4.1-thomas.1", "0.4.0-thomas.9"))
        assertTrue(Store.forkNewer("0.4.6.1-thomas.1", "0.4.6-thomas.9"))
        assertTrue(!Store.forkNewer("0.4.6-thomas.9", "0.4.6.1-thomas.1"))
        assertTrue(Store.forkNewer("0.4.0-thomas.10", "0.4.0-thomas.9"))
        assertTrue(Store.forkNewer("0.4.0-thomas.1", "0.3.7-thomas.9"))
        assertTrue(!Store.forkNewer("0.3.7-thomas.9", "0.4.0-thomas.1"))
        assertTrue(!Store.forkNewer("0.4.0", "0.4.0-thomas.1"))
    }

    // A four-part hotfix (0.4.6.1): 0.4.6 is offered it; the 0.4.7-beta1 test build, already past it, is not.
    // 0.4.7 (issue #818): the API's 60 requests an hour per address run out behind a shared address (CGNAT, a VPN),
    // and it answers 403. The releases feed on github.com has no such limit. When neither answers, the diagnostics
    // log says why for each. Fork (Thomas): the fork's feed, whose newest "-thomas.N" tag is the latest release.
    private class ByUrl(val answers: Map<String, Any>) : HttpFetcher {
        val urls = mutableListOf<String>()
        override suspend fun get(url: String, etag: String?, rangeFrom: Long?): HttpResponse {
            urls += url
            return when (val a = answers[url]) {
                is Exception -> throw a
                is Pair<*, *> -> (a.second as String).let { HttpResponse(a.first as Int, null, ByteArrayInputStream(it.encodeToByteArray()), it.length.toLong()) }
                else -> HttpResponse(404, null, null, null)
            }
        }
    }
    private val feed = """<feed><entry><link rel="alternate" type="text/html" href="https://github.com/Eliotm2/cleanyourtv-app/releases/tag/3.2-thomas.2"/></entry>
        <entry><link rel="alternate" type="text/html" href="https://github.com/Eliotm2/cleanyourtv-app/releases/tag/3.2-thomas.1"/></entry>
        <entry><link rel="alternate" type="text/html" href="https://github.com/Eliotm2/cleanyourtv-app/releases/tag/0.4.6.1-thomas.1"/></entry></feed>"""

    @Test fun aRateLimitedApiFallsBackToTheReleasesFeed() = runBlocking {
        val lines = mutableListOf<String>()
        val http = ByUrl(mapOf(Store.RELEASES_URL to (403 to """{"message":"API rate limit exceeded"}"""), Store.FEED_URL to (200 to feed)))
        assertEquals("3.2-thomas.2", Store.updateChecker(http) { lines += it }.latestNewerThan("3.2-thomas.1"))
        assertEquals(listOf(Store.RELEASES_URL, Store.FEED_URL), http.urls)
        assertEquals(listOf("settings: update check used the releases feed (api HTTP 403)"), lines)
        assertEquals("", Store.updateChecker(http) {}.latestNewerThan("3.2-thomas.2"))
        assertEquals("3.2-thomas.2", Store.updateChecker(http) {}.latestNewerThan("0.4.6.1-thomas.1"))
        assertEquals(null, Store.latestInFeed("<feed></feed>"))
        assertEquals(null, Store.latestInFeed("""<feed><entry><link href="https://github.com/x/y/releases/tag/v0.4.7"/></entry></feed>"""), "not a fork tag")
    }

    @Test fun whenNeitherAnswersTheLogSaysWhy() = runBlocking {
        val lines = mutableListOf<String>()
        val tls = tv.cleanyour.core.registry.FetchException(tv.cleanyour.core.registry.FetchFailure.HTTP_ERROR, null, "TLS failed: SSLHandshakeException: chain refused")
        val http = ByUrl(mapOf(Store.RELEASES_URL to tls, Store.FEED_URL to (429 to "")))
        assertEquals(null, Store.updateChecker(http) { lines += it }.latestNewerThan("3.2-thomas.1"))
        assertEquals(listOf("settings: update check failed: api http_error (TLS failed: SSLHandshakeException: chain refused), feed HTTP 429"), lines)
        // The API's answer is used first when it has a tag: one request.
        val ok = ByUrl(mapOf(Store.RELEASES_URL to (200 to """{"tag_name":"3.2-thomas.2"}""")))
        assertEquals("3.2-thomas.2", Store.updateChecker(ok) {}.latestNewerThan("3.2-thomas.1"))
        assertEquals(listOf(Store.RELEASES_URL), ok.urls)
    }

    @Test fun aFourPartHotfixIsOfferedToTheReleaseBeforeItOnly() = runBlocking {
        fun latest(tag: String, current: String) = runBlocking { Store.updateChecker(OneAnswer(200, "{\"tag_name\":\"v$tag\"}")).latestNewerThan(current) }
        assertTrue(Store.offered("0.4.6.1", "0.4.6"))
        assertFalse(Store.offered("0.4.6.1", "0.4.7-beta1"))
        assertFalse(Store.offered("0.4.6.1", "0.4.6.1"))
        assertTrue(Store.offered("0.4.7", "0.4.7-beta1"))
        // Fork (Thomas): the checker reads the fork's releases; four-part upstream versions order first.
        assertEquals("0.4.6.1-thomas.1", latest("0.4.6.1-thomas.1", "0.4.6-thomas.3"))
        assertEquals("", latest("0.4.6-thomas.3", "0.4.6.1-thomas.1"))
    }

    @Test fun softwareUpdatesStayTheOwnersChoice() {
        val g = graph()
        val ota = PackageName("com.mitv.ota")
        val on = PackageState(EnabledState.ENABLED, true, 1, null, false, false, null, false)
        val snap = Samples.snapshot(packages = Samples.miTvPackages(emptySet()) + (ota to on))
        val r = g.planner.plan(PlanRequest.SwitchOff(setOf(ota)), snap, g.facts, emptyList())
        assertIs<PlanResult.Ok>(r)
        assertEquals(listOf<Op>(Op.Disable(ota)), r.plan.steps.map { it.op })
    }
}
