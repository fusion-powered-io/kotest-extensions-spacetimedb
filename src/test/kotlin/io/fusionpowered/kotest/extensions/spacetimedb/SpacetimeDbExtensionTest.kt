package io.fusionpowered.kotest.extensions.spacetimedb

import com.clockworklabs.spacetimedb.bsatn.BsatnReader
import com.clockworklabs.spacetimedb.bsatn.BsatnWriter
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotBeEmpty
import io.kotest.matchers.string.shouldStartWith
import io.kotest.provided.ProjectConfig.spacetimeDbExtension

class SpacetimeDbExtensionTest : StringSpec({

    "should start SpacetimeDB server, publish module, and establish a DbConnection" {
        // Given
        // A globally registered SpacetimeDbExtension active for this project

        // When
        // The test suite executes (the extension has automatically started the server and published the module)

        // Then
        spacetimeDbExtension.connection.shouldNotBeNull()
        spacetimeDbExtension.url shouldStartWith "http://localhost:"

        System.getProperty("spacetime.url") shouldBe spacetimeDbExtension.url
        System.getProperty("spacetime.module") shouldBe "test-module"
        System.getProperty("spacetime.token").shouldNotBeNull().shouldNotBeEmpty()
    }

    "should generate valid signed token with default claims" {
        // Given
        // The globally active SpacetimeDbExtension

        // When
        val token = spacetimeDbExtension.createToken()

        // Then
        token.split(".").size shouldBe 3
    }

    "should generate valid signed token with custom claims" {
        // Given
        val customClaims = mapOf(
            "name" to "Alice Administrator",
            "email" to "alice@example.com",
            "role" to "admin"
        )

        // When
        val token = spacetimeDbExtension.createToken(
            issuer = "custom-issuer",
            claims = customClaims
        )

        // Then
        token.split(".").size shouldBe 3
    }

    "should allow custom port configuration and compute correct URLs" {
        // Given
        val customPort = 45678

        // When
        val customExt = SpacetimeDbExtension(
            moduleName = "another-module",
            modulePath = "src/test/resources/test_module",
            defaultIssuer = "custom-issuer",
            port = customPort
        )

        // Then
        customExt.port shouldBe customPort
        customExt.url shouldBe "http://localhost:45678"
    }


    "should successfully call reducer methods" {
        // Given
        // A globally registered SpacetimeDbExtension active for this project
        val addPersonArgs = BsatnWriter().apply {
            writeString("Bob")
            writeI32(30)
        }
        // When
        spacetimeDbExtension.connection.callReducer("add_person", addPersonArgs.toByteArray())

        // Then
        spacetimeDbExtension.connection.oneOffQuery("select * from person where name = 'Bob'").rows.shouldNotBeNull {
            val personTableData = tables.first { it.table.value == "person" }.rows.rowsData
            BsatnReader(personTableData).run {
                readString() shouldBe "Bob"
                readI32() shouldBe 30
            }
        }
    }

})
