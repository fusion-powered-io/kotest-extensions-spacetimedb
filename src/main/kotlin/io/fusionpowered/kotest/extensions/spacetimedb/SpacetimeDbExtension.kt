package io.fusionpowered.kotest.extensions.spacetimedb

import com.clockworklabs.spacetimedb.DbConnection
import io.kotest.core.listeners.AfterProjectListener
import io.kotest.core.listeners.AfterTestListener
import io.kotest.core.listeners.BeforeTestListener
import io.kotest.core.test.TestCase
import io.kotest.engine.test.TestResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.ServerSocket
import kotlin.uuid.Uuid

/**
 * A Kotest extension that automates the lifecycle of a local SpacetimeDB instance for integration testing.
 *
 * This extension:
 * 1. Starts a standalone, in-memory SpacetimeDB server bound to a local port before the project runs.
 * 2. Builds and publishes the designated database module to that local server.
 * 3. Establishes and exposes a [DbConnection] to the published database before each test.
 * 4. Disconnects and stops the SpacetimeDB server after the tests complete.
 *
 * Register it inside your Kotest ProjectConfig using `extensions = listOf(spacetimeDbExtension)`.
 *
 * @property moduleName The name of the SpacetimeDB database module to publish and interact with.
 * @property modulePath The system path (relative or absolute) to the module directory (e.g. containing Cargo.toml).
 * @property port The local port number the SpacetimeDB server should listen on. Defaults to a randomly allocated free port.
 * @property extensionToken The token used for the extension to connect to the module
 *
 */
class SpacetimeDbExtension(
    val moduleName: String,
    private val modulePath: String,
    val port: Int = ServerSocket(0).use { it.localPort },
    private val extensionToken: String? = null,
) : BeforeTestListener, AfterTestListener, AfterProjectListener {

    /**
     * The local server connection URL.
     */
    val url = "http://localhost:$port"

    /**
     * The active [DbConnection] instance connected to the published SpacetimeDB module.
     * This is initialized in the [beforeTest] lifecycle hook.
     */
    lateinit var connection: DbConnection

    private lateinit var instanceProcess: Process

    init {
        cli("start")
        // Expose database connection details as system properties so frameworks (e.g. Spring) can pick them up
        System.setProperty("spacetime.url", url)
        System.setProperty("spacetime.module", moduleName)
        extensionToken?.let {  System.setProperty("spacetime.token", it) }
    }

    /**
     * Kotest before-test hook. Establishes the type-safe client connection before each test.
     */
    override suspend fun beforeTest(testCase: TestCase) {
        cli("publish", moduleName, "--module-path", modulePath, "-s", url, "-c", "--yes")
        connection = DbConnection.builder()
            .withUri(url)
            .withModuleName(moduleName)
            .withToken(extensionToken)
            .build()
    }

    /**
     * Kotest after-test hook. Disconnects the database client session.
     */
    override suspend fun afterTest(testCase: TestCase, result: TestResult) {
        connection.disconnect()
    }

    /**
     * Kotest after-project hook. Cleans up and forcefully terminates the local SpacetimeDB server process and descendants.
     */
    override suspend fun afterProject() {
        cli("stop")
    }

    /**
     * Executes a SpacetimeDB CLI command as a subprocess.
     *
     * Supported special commands:
     * - `"start"`: Spawns an in-memory SpacetimeDB standalone server and polls until the ping check succeeds or times out.
     * - `"stop"`: Forcefully kills the SpacetimeDB server subprocess and all its descendants.
     * - Any other arguments are passed directly to the `spacetime` executable.
     *
     * @param args The command line arguments to pass or execute.
     */
    fun cli(vararg args: String) {
        when (args.first()) {
            "start" -> {
                instanceProcess = execute(
                    "spacetime", "start", "--in-memory", "--non-interactive",
                    "--listen-addr", "0.0.0.0:$port",
                    "--data-dir", "build/tmp/spacetime/${Uuid.random()}"
                )

                var retries = 0
                val maxRetries = 50 // 50 * 200ms = 10 second timeout
                do {
                    Thread.sleep(200)
                    println("Waiting for server (attempt ${retries + 1})...")
                    retries++
                    if (retries > maxRetries) {
                        throw IllegalStateException("SpacetimeDB server failed to start within 10 seconds")
                    }
                } while (execute("spacetime", "server", "ping", url).waitFor() != 0)
                println("SpacetimeDB is up at $url")
            }

            "stop" -> {
                if (::instanceProcess.isInitialized) {
                    instanceProcess.descendants().forEach { it.destroyForcibly() }
                    instanceProcess.destroyForcibly().waitFor()
                }
            }

            else -> execute("spacetime", *args).waitFor()
        }
    }

    private fun execute(vararg args: String) =
        ProcessBuilder(*args)
            .start()

}