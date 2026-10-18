package cn.solarmoon.spark_core.pack.diagnostic

import net.minecraft.network.chat.Component
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class SparkPackIssuesTest {

    @BeforeEach
    fun setUp() {
        SparkPackIssues.reset()
    }

    @AfterEach
    fun tearDown() {
        SparkPackIssues.reset()
    }

    private fun issue(
        location: String,
        severity: PackSeverity = PackSeverity.ERROR,
        context: PackLoadContext = PackLoadContext.CLIENT_LOCAL,
        moduleId: String = "models",
        packId: String? = "test",
    ) = PackIssue(moduleId, packId, location, severity, Component.literal("bad $location"), null, context)

    @Test
    fun `contexts are isolated`() {
        SparkPackIssues.begin(PackLoadContext.CLIENT_LOCAL)
        SparkPackIssues.report(issue("ns:models/local.json"))

        SparkPackIssues.begin(PackLoadContext.CLIENT_REMOTE)
        SparkPackIssues.report(issue("ns:models/remote-a.json", context = PackLoadContext.CLIENT_REMOTE))
        SparkPackIssues.report(issue("ns:models/remote-b.json", context = PackLoadContext.CLIENT_REMOTE))

        assertEquals(1, SparkPackIssues.snapshot(PackLoadContext.CLIENT_LOCAL).size)
        assertEquals(2, SparkPackIssues.snapshot(PackLoadContext.CLIENT_REMOTE).size)

        // 只清空本地上下文，远端上下文保留
        SparkPackIssues.begin(PackLoadContext.CLIENT_LOCAL)
        assertEquals(0, SparkPackIssues.snapshot(PackLoadContext.CLIENT_LOCAL).size)
        assertEquals(2, SparkPackIssues.snapshot(PackLoadContext.CLIENT_REMOTE).size)
    }

    @Test
    fun `records are capped per context`() {
        SparkPackIssues.begin(PackLoadContext.CLIENT_LOCAL)
        repeat(SparkPackIssues.CAP_PER_CONTEXT + 5) {
            SparkPackIssues.report(issue("ns:models/$it.json"))
        }

        assertEquals(SparkPackIssues.CAP_PER_CONTEXT, SparkPackIssues.snapshot(PackLoadContext.CLIENT_LOCAL).size)
        assertEquals(5, SparkPackIssues.summary(PackSide.CLIENT).dropped)
    }

    @Test
    fun `records are not deduplicated`() {
        SparkPackIssues.begin(PackLoadContext.CLIENT_LOCAL)
        val duplicated = issue("ns:models/a.json")
        SparkPackIssues.report(duplicated)
        SparkPackIssues.report(duplicated)

        assertEquals(2, SparkPackIssues.snapshot(PackLoadContext.CLIENT_LOCAL).size)
    }

    @Test
    fun `summary groups by module`() {
        SparkPackIssues.begin(PackLoadContext.CLIENT_LOCAL)
        SparkPackIssues.report(issue("ns:models/a.json"))
        SparkPackIssues.report(issue("ns:models/b.json", severity = PackSeverity.WARN))
        SparkPackIssues.report(issue("ns:parts/a.json", moduleId = "parts"))
        SparkPackIssues.begin(PackLoadContext.CLIENT_REMOTE)
        SparkPackIssues.report(issue("ns:models/c.json", context = PackLoadContext.CLIENT_REMOTE))

        val summary = SparkPackIssues.summary(PackSide.CLIENT)
        assertEquals(listOf("models", "parts"), summary.modules.map { it.moduleId })
        assertEquals(3, summary.errors)
        assertEquals(1, summary.warnings)
        assertEquals(3, summary.modules.first().issues.size)
    }

    @Test
    fun `summary groups by module and sorts deterministically`() {
        SparkPackIssues.begin(PackLoadContext.CLIENT_LOCAL)
        SparkPackIssues.report(issue("ns:models/b.json", packId = "p2"))
        SparkPackIssues.report(issue("ns:models/a.json", packId = "p1"))
        SparkPackIssues.report(issue("ns:models/c.json", packId = null, severity = PackSeverity.WARN))
        SparkPackIssues.report(issue("ns:models/c.json", packId = null, severity = PackSeverity.ERROR))
        SparkPackIssues.report(issue("ns:parts/a.json", moduleId = "parts"))

        val sorted = SparkPackIssues.snapshot(PackLoadContext.CLIENT_LOCAL)
        assertEquals(
            listOf(
                "ns:models/c.json" to PackSeverity.ERROR,
                "ns:models/c.json" to PackSeverity.WARN,
                "ns:models/a.json" to PackSeverity.ERROR,
                "ns:models/b.json" to PackSeverity.ERROR,
                "ns:parts/a.json" to PackSeverity.ERROR,
            ),
            sorted.map { it.location to it.severity },
        )
    }

    @Test
    fun `session ids advance per context and per side`() {
        SparkPackIssues.begin(PackLoadContext.CLIENT_LOCAL)
        SparkPackIssues.begin(PackLoadContext.CLIENT_LOCAL)
        SparkPackIssues.begin(PackLoadContext.CLIENT_REMOTE)

        assertEquals(2, SparkPackIssues.sessionId(PackLoadContext.CLIENT_LOCAL))
        assertEquals(1, SparkPackIssues.sessionId(PackLoadContext.CLIENT_REMOTE))
        assertEquals(3, SparkPackIssues.sideSessionId(PackSide.CLIENT))
        assertEquals(0, SparkPackIssues.sideSessionId(PackSide.SERVER))
    }

    @Test
    fun `warn without frame does not throw`() {
        assertDoesNotThrow { SparkPackIssues.warn(Component.literal("outside a frame")) }
    }

    @Test
    fun `concurrent reports respect the cap`() {
        SparkPackIssues.begin(PackLoadContext.CLIENT_LOCAL)
        val threads = 8
        val perThread = 50
        val pool = Executors.newFixedThreadPool(threads)
        val start = CountDownLatch(1)
        val done = CountDownLatch(threads)
        try {
            repeat(threads) { thread ->
                pool.execute {
                    start.await()
                    repeat(perThread) { index ->
                        SparkPackIssues.report(issue("ns:models/$thread-$index.json"))
                    }
                    done.countDown()
                }
            }
            start.countDown()
            assertTrue(done.await(30, TimeUnit.SECONDS), "concurrent reports timed out")
        } finally {
            pool.shutdownNow()
        }

        assertEquals(SparkPackIssues.CAP_PER_CONTEXT, SparkPackIssues.snapshot(PackLoadContext.CLIENT_LOCAL).size)
        assertEquals(threads * perThread - SparkPackIssues.CAP_PER_CONTEXT, SparkPackIssues.summary(PackSide.CLIENT).dropped)
    }
}
