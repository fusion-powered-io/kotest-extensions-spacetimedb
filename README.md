# SpacetimeDB Kotest Extension

[![License](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)
[![Kotlin](https://img.shields.io/badge/kotlin-2.3.21-purple.svg)](https://kotlinlang.org/)
[![Kotest](https://img.shields.io/badge/kotest-6.1.11-green.svg)](https://kotest.io/)

A premium, lightweight Kotest extension that automates the lifecycle of a local, in-memory **SpacetimeDB** instance during integration testing. 

With this extension, you can easily spin up SpacetimeDB, compile and publish your Rust or C# modules, establish type-safe client connections, and perform clean, isolated, automated test assertions—all inside your JVM test suite.

---

## Features

- ⚡ **Zero-Config Lifecycle**: Automatically boots an isolated, in-memory SpacetimeDB server before tests start and gracefully stops it afterwards.
- 📦 **Autobuild & Publish**: Automates compiling and publishing database modules directly from your source directory to the local test server.
- 🔌 **Ready-to-Use client Connection**: Provides a fully initialized, pre-connected `DbConnection` client out of the box in the test listener context.
- 🔑 **Automatic Token Generation**: Generates signed `ES256` JWT authentication tokens using the local developer private key (`~/.config/spacetime/id_ecdsa`).
- 🍃 **Framework-Friendly Integration**: Populates key system properties (`spacetime.url`, `spacetime.module`, `spacetime.token`) automatically so that dependency-injection frameworks (like Spring Boot) can seamlessly pick them up.

---

## Prerequisites

To use this extension, the local machine must have:
1. **SpacetimeDB CLI** installed. Follow the [SpacetimeDB Installation Guide](https://spacetimedb.com/docs/getting-started/installing).
2. **Cargo (Rust) or C# SDK** (depending on your module language) to compile the database module.
3. A registered developer private key at `~/.config/spacetime/id_ecdsa` (this is automatically created the first time you run `spacetime` or publish).

---

## Installation

Add the extension dependency to your `build.gradle.kts` file:

```kotlin
dependencies {
    // Kotest and SpacetimeDB dependencies
    testImplementation("io.fusionpowered:kotest-extensions-spacetimedb:0.0.1-SNAPSHOT")
    testImplementation("io.kotest:kotest-runner-junit5:6.1.11")
    testImplementation("com.clockworklabs:spacetimedb-sdk:0.1.0")
}
```

---

## Usage Example

### 1. Global Registration (Preferred)

Register the extension globally using Kotest's `AbstractProjectConfig`. Create a file at `src/test/kotlin/io/kotest/provided/ProjectConfig.kt`:

```kotlin
package io.kotest.provided

import io.fusionpowered.kotest.extensions.spacetimedb.SpacetimeDbExtension
import io.kotest.core.config.AbstractProjectConfig

object ProjectConfig : AbstractProjectConfig() {
    // Setup and register the extension globally
    val spacetimeDbExtension = SpacetimeDbExtension(
        moduleName = "my-test-module",
        modulePath = "path/to/my-module-directory", // e.g. src/test/resources/my-module
        defaultIssuer = "test-issuer"
    )

    override val extensions = listOf(spacetimeDbExtension)
}
```

### 2. Consuming in Tests

Access the globally configured extension instance inside your test classes by importing it directly:

```kotlin
package io.fusionpowered.kotest.extensions.spacetimedb

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldStartWith
import io.kotest.provided.ProjectConfig.spacetimeDbExtension

class MySpacetimeDbTest : FunSpec({

    test("should establish type-safe connections and verify integration") {
        // Given
        // The globally active and pre-configured SpacetimeDbExtension imported directly

        // When
        // The test executes (the server is active and the module is published)

        // Then
        spacetimeDbExtension.connection.shouldNotBeNull {
            // Assert on the initialized non-null connection
        }
        
        // Assert the server is healthy and online using infix matchers
        spacetimeDbExtension.url shouldStartWith "http://localhost:"
        
        // Assert system properties are correctly populated for integration frameworks
        System.getProperty("spacetime.url") shouldBe spacetimeDbExtension.url
    }
})
```

---

## Configuration API

The `SpacetimeDbExtension` constructor accepts the following parameters:

| Parameter | Type | Default Value | Description |
|---|---|---|---|
| `moduleName` | `String` | *(Required)* | The name of the SpacetimeDB database module to publish. |
| `modulePath` | `String` | *(Required)* | The path to the database module project directory containing your module definition (e.g., `Cargo.toml`). |
| `defaultIssuer` | `String` | *(Required)* | The default issuer string to sign JWT auth tokens. |
| `defaultClaims` | `Map<String, String>` | `mapOf("name" to "SpacetimeDB User", "email" to "user@spacetimedb")` | Custom JWT claims payload map. |
| `port` | `Int` | Randomly allocated free port | Local port number the SpacetimeDB server will listen on. |

---

## How It Works Under the Hood

1. **Instantiation**: The extension allocates a local TCP port and publishes system properties.
2. **Setup (`beforeTest`)**: Runs `spacetime start --in-memory` in a subprocess, pings until ready, then invokes `spacetime publish` to compile the module at `modulePath` and upload it.
3. **Connection**: Constructs and initializes a type-safe client `DbConnection` linked to your published database.
4. **Teardown (`afterProject`)**: Gracefully disconnects all connections and terminates the background server process cleanly.

---

## License

This project is licensed under the Apache License 2.0. See the `LICENSE` file for details.
