package io.kotest.provided

import io.fusionpowered.kotest.extensions.spacetimedb.SpacetimeDbExtension
import io.kotest.core.config.AbstractProjectConfig

/**
 * Global Kotest Project Configuration.
 * Registers [SpacetimeDbExtension] as a global extension active across all spec executions.
 */
object ProjectConfig : AbstractProjectConfig() {
    val spacetimeDbExtension = SpacetimeDbExtension(
        moduleName = "test-module",
        modulePath = "src/test/resources/test_module",
        defaultIssuer = "test-issuer"
    )

    override val extensions = listOf(spacetimeDbExtension)
}
