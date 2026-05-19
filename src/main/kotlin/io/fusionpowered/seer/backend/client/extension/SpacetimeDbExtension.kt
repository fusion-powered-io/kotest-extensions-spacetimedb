package io.fusionpowered.seer.backend.client.extension

import com.clockworklabs.spacetimedb.DbConnection
import io.fusionpowered.seer.backend.client.extension.model.UserTokenData
import io.jsonwebtoken.Jwts
import io.kotest.core.listeners.AfterProjectListener
import io.kotest.core.listeners.AfterTestListener
import io.kotest.core.listeners.BeforeTestListener
import io.kotest.core.test.TestCase
import io.kotest.engine.test.TestResult
import java.io.File
import java.net.ServerSocket
import java.security.KeyFactory
import java.security.spec.PKCS8EncodedKeySpec
import kotlin.io.encoding.Base64
import kotlin.random.Random
import kotlin.uuid.Uuid

class SpacetimeDbExtension(
    val moduleName: String = "backend-server-test",
    private val port: Int = ServerSocket(0).use { it.localPort },
    private val modulePath: String = "../backendServer"
) : BeforeTestListener, AfterTestListener, AfterProjectListener {

    val url = "http://localhost:$port"

    lateinit var connection: DbConnection

    private lateinit var process: Process

    init {
        //To be picked up by spring
        System.setProperty("spacetime.url", url)
        System.setProperty("spacetime.module", moduleName)
        System.setProperty("spacetime.token", createTokenFor(UserTokenData("Backend User", "user@backend")))
    }

    fun createTokenFor(userTokenData: UserTokenData): String {
        val privateKey = File("${System.getProperty("user.home")}/.config/spacetime/id_ecdsa")
            .readText()
            .replace("-----BEGIN PRIVATE KEY-----", "")
            .replace("-----END PRIVATE KEY-----", "")
            .replace("\\s".toRegex(), "")
            .let { Base64.decode(it) }
            .let { PKCS8EncodedKeySpec(it) }
            .let { KeyFactory.getInstance("EC").generatePrivate(it) }
        return with(userTokenData) {
            Jwts.builder()
                .issuer("https://accounts.google.com")
                .issuedAt(java.util.Date())
                .expiration(java.util.Date(System.currentTimeMillis() + 3_600_000))
                .subject(Random.nextBytes(ByteArray(64)).toHexString())
                .claim("name", name)
                .claim("email", email)
                .claim("picture", picture)
                .signWith(privateKey, Jwts.SIG.ES256)
                .compact()
        }
    }

    override suspend fun beforeTest(testCase: TestCase) {
        connection = DbConnection.builder()
            .withUri(url)
            .withModuleName(moduleName)
            .withToken(createTokenFor(UserTokenData()))
            .also {
                cli("start")
                cli("publish", moduleName, "--module-path", modulePath, "-s", url, "-c", "--yes")
            }
            .build()
    }

    override suspend fun afterTest(testCase: TestCase, result: TestResult) {
        connection.disconnect()
    }

    override suspend fun afterProject() {
        cli("stop")
    }

    fun cli(vararg args: String) {
        when (args.first()) {
            "start" -> {
                process = execute(
                    "spacetime", "start", "--in-memory", "--non-interactive",
                    "--listen-addr", "0.0.0.0:$port",
                    "--data-dir", "build/tmp/spacetime/${Uuid.random()}"
                )
                do {
                    Thread.sleep(200)
                    println("Waiting for server...")
                } while (execute("spacetime", "server", "ping", url).waitFor() != 0)
            }

            "stop" -> process.destroyForcibly().waitFor()

            else -> execute("spacetime", *args).waitFor()
        }
    }

    private fun execute(vararg args: String) =
        ProcessBuilder(*args)
            .inheritIO()
            .start()

}