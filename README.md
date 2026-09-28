[![Maven Central](https://img.shields.io/maven-central/v/dev.klitsie.kameleon/kameleon-gradle-plugin)](https://central.sonatype.com/artifact/dev.klitsie.kameleon/kameleon-gradle-plugin)
[![Kotlin Library](https://img.shields.io/badge/Library-Kotlin_2.2.21-blue?logo=kotlin)](https://kotlinlang.org)
[![Platform](https://img.shields.io/badge/platform-Android%20%7C%20iOS%20%7C%20JVM%20%7C%20JS%20%7C%20WasmJS-lightgrey.svg)](#)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)

# Kameleon

**Kameleon** is a flavor and variant management toolchain for **Kotlin Multiplatform (KMP)** and **Compose Multiplatform (CMP)** projects.

It brings Android-style product flavors and multi-dimensional flavor matrices across all Kotlin Multiplatform targets (**Android, iOS, Desktop/JVM, Web Wasm/JS**), providing unified source set overlays, Compose Multiplatform resource merging, type-safe build configs, and seamless IDE integration.

---

## Key Features

- **Multi-Dimensional Flavor Matrix**: Define one or more flavor dimensions (e.g., `brand`, `environment`) that combine into predictable variants (e.g., `whoopProduction`, `flappyStaging`).
- **Unified Source Set Overlays**: Automatically overlay Kotlin source sets per flavor and per composite variant across `commonMain` and platform targets.
- **Compose Multiplatform Resources Merging**: Seamlessly merge and override Compose Multiplatform resources (`composeResources`) based on the active flavor/variant hierarchy.
- **Type-Safe BuildConfig (`KameleonConfig`)**: Generate compile-time constants (`String`, `Boolean`, `Int`, `Long`) with hierarchical value overrides (Project Default &rarr; Dimension &rarr; Flavor).
- **IntelliJ & Android Studio Companion Plugin**: Fast variant switching toolbar with automatic two-way synchronization to `gradle.properties` and Android Gradle Plugin (AGP) build variants.
- **Task & Xcode Aware**: Automatically detects flavors from Gradle task names (e.g., `assembleWhoopProductionDebug`) and Xcode build environment variables (`FLAVOR`, `SCHEME`, `CONFIGURATION`).

---

## 1. Setup & Gradle Configuration

### Apply the Plugin

Add the Kameleon plugin to your root build script or module build scripts (`build.gradle.kts`):

```kotlin
plugins {
    alias(libs.plugins.kotlinMultiplatform) // or kotlinJvm / androidApplication / androidLibrary
    alias(libs.plugins.composeMultiplatform) // optional, if using Compose Resources
    id("dev.klitsie.kameleon")
}
```

---

## 2. Configuring Flavors & Dimensions

Kameleon allows you to configure single-dimension or multi-dimensional flavor matrices.

### Single Dimension Example

For standard setups with one dimension (such as environment):

```kotlin
kameleon {
    dimension("environment") {
        flavors {
            register("staging")
            register("production")
        }
    }
}
```

*Shortcut syntax:*
```kotlin
kameleon {
    dimension("environment", "staging", "production")
}
```

---

### Multi-Dimensional Matrix Example

When your application varies across multiple axes (e.g., `brand` and `environment`):

```kotlin
kameleon {
    dimension("brand") {
        flavors {
            register("whoop")
            register("flappy")
        }
    }

    dimension("environment") {
        flavors {
            register("staging")
            register("production")
        }
    }
}
```

### Variant Matrix Calculation

When multiple dimensions are configured, Kameleon calculates the Cartesian product of the ordered dimensions using standard camelCase naming:

| `brand` | `environment` | Resulting Variant Name |
| :--- | :--- | :--- |
| `whoop` | `staging` | `whoopStaging` |
| `whoop` | `production` | `whoopProduction` |
| `flappy` | `staging` | `flappyStaging` |
| `flappy` | `production` | `flappyProduction` |

---

## 3. Type-Safe BuildConfig (`KameleonConfig`)

Kameleon generates a type-safe `KameleonConfig` class available in `commonMain` (and platform targets). Fields can be declared globally, per dimension, or per flavor, with lower levels overriding higher levels.

```kotlin
kameleon {
    // 1. Root / Base defaults
    defaultBuildConfig {
        packageName.set("dev.klitsie.kameleon.sample")
        className.set("KameleonConfig") // default: KameleonConfig
        fields.put("APP_NAME", "Kameleon App")
        fields.put("ENABLE_ANALYTICS", false)
        fields.put("TIMEOUT_SECONDS", 30)
    }

    // 2. Dimension & Flavor-specific overrides
    dimension("environment") {
        flavor("staging") {
            buildConfig {
                fields.put("API_URL", "https://staging.api.example.com")
                fields.put("ENABLE_ANALYTICS", true)
            }
        }
        flavor("production") {
            buildConfig {
                fields.put("API_URL", "https://api.example.com")
                fields.put("ENABLE_ANALYTICS", true)
            }
        }
    }
}
```

### Accessing Generated Config in Kotlin

```kotlin
import dev.klitsie.kameleon.sample.KameleonConfig

fun initializeApp() {
    println("Active Variant: ${KameleonConfig.VARIANT_NAME}")
    println("Active Flavors: ${KameleonConfig.FLAVORS.joinToString()}")
    println("API Endpoint: ${KameleonConfig.API_URL}")
}
```

---

## 4. Project Directory Structure & Merging Rules

Kameleon looks for flavor-specific source code and Compose resources under `src/flavors/<flavor-name>/`.

### Directory Layout

```
shared-module/
├── src/
│   ├── commonMain/
│   │   ├── kotlin/
│   │   │   └── dev/klitsie/kameleon/BaseService.kt
│   │   └── composeResources/
│   │       ├── drawable/logo.xml
│   │       └── values/strings.xml
│   └── flavors/
│       ├── whoop/
│       │   ├── kotlin/
│       │   └── composeResources/drawable/logo.xml         <-- Overrides base logo for whoop
│       ├── flappy/
│       │   ├── kotlin/
│       │   └── composeResources/drawable/logo.xml         <-- Overrides base logo for flappy
│       ├── staging/
│       │   └── composeResources/values/strings.xml        <-- Overrides base strings for staging
│       ├── production/
│       │   └── composeResources/values/strings.xml        <-- Overrides base strings for production
│       └── whoopProduction/                               <-- (Optional) Composite variant override
│           └── composeResources/drawable/promo.xml
```

### Resource & Source Set Priority

When building a variant such as `whoopProduction`, Kameleon applies overlays in order of increasing specificity:

1. **Base**: `src/commonMain/composeResources` & `src/commonMain/kotlin`
2. **Dimension 1 Flavor**: `src/flavors/whoop/...`
3. **Dimension 2 Flavor**: `src/flavors/production/...`
4. **Composite Variant**: `src/flavors/whoopProduction/...`

---

## 5. Selecting and Building Variants

### Via `gradle.properties` (Persistent)

Set the active variant or individual dimensions in your project's `gradle.properties`:

```properties
# Multi-dimension configuration:
kameleon.flavor.brand=whoop
kameleon.flavor.environment=production

# Or single-dimension / direct variant:
kameleon.flavor=staging
```

### Via Command-Line Properties (Ad-hoc)

Pass properties directly to Gradle commands:

```bash
# Run Desktop App with staging flavor
./gradlew :sample:desktopApp:run -Pkameleon.flavor=staging

# Run Desktop App with multi-dimensional flavor
./gradlew :sample:desktopApp:run -Pkameleon.flavor.brand=whoop -Pkameleon.flavor.environment=production
```

### Automatic Task Name Matching

When executing Android or multiplatform tasks containing flavor names, Kameleon automatically infers and resolves the matching flavor:

```bash
# Automatically sets variant to 'whoopProduction'
./gradlew :sample:androidApp:assembleWhoopProductionDebug
```

### iOS / Xcode Integration

When building through Xcode via `embedAndSignAppleFrameworkForXcode`, Kameleon automatically inspects Xcode environment variables (`FLAVOR`, `SCHEME`, or `CONFIGURATION`) and selects the corresponding flavor.

---

## 6. Companion IntelliJ & Android Studio Plugin

The `kameleon-idea-plugin` (**Kameleon Variant Switcher**) provides seamless variant switching inside your IDE.

### Features

- **Toolbar Switcher**: An interactive dropdown widget in the IDE toolbar displaying the currently active variant.
- **Multi-Dimension Submenus**: When multiple dimensions are configured, the dropdown automatically organizes flavors into clear dimensional submenus (`Dimension: Brand`, `Dimension: Environment`).
- **Two-Way AGP Synchronization**: Switching a flavor in the Kameleon toolbar automatically:
  1. Updates the `gradle.properties` file on disk.
  2. Dispatches variant changes to the **Android Gradle Plugin (AGP)** Build Variants tool window across all Android modules.
- **Instant IDE Sync**: Refreshes Compose Multiplatform resource previews and Kotlin source set bindings to match the active selection.

---

## 7. Sample Project

Inspect the `sample/` directory in this repository for a complete reference implementation:
- `:sample:shared-translations`: Demonstrates flavor-based Compose string and resource overrides.
- `:sample:shared-app`: Multiplatform shared logic consuming `KameleonConfig`.
- `:sample:androidApp`: Android application utilizing AGP flavor dimensions paired with Kameleon.
- `:sample:desktopApp`, `:sample:webApp`: Multiplatform targets consuming flavored resources and configs.
