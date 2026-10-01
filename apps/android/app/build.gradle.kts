import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import java.net.URI
import java.util.Base64

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

fun String.asBuildConfigString(): String = "\"" +
    replace("\\", "\\\\").replace("\"", "\\\"") + "\""

val supabaseUrl = providers.gradleProperty("SUPABASE_URL")
    .orElse(providers.environmentVariable("SUPABASE_URL"))
    .getOrElse("")
val supabasePublishableKey = providers.gradleProperty("SUPABASE_PUBLISHABLE_KEY")
    .orElse(providers.environmentVariable("SUPABASE_PUBLISHABLE_KEY"))
    .getOrElse("")
val rakyzuApiBaseUrl = providers.gradleProperty("RAKYZU_API_BASE_URL")
    .orElse(providers.environmentVariable("RAKYZU_API_BASE_URL"))
    .getOrElse("")

fun String.isPrivilegedSupabaseKey(): Boolean {
    if (startsWith("sb_secret_", ignoreCase = true)) return true
    val payload = split('.').getOrNull(1) ?: return false
    return runCatching {
        String(Base64.getUrlDecoder().decode(payload.padEnd((payload.length + 3) / 4 * 4, '=')))
    }.getOrDefault("").contains(Regex("\"role\"\\s*:\\s*\"service_role\""))
}

val verifyPublicConfiguration = tasks.register("verifyPublicConfiguration") {
    group = "verification"
    description = "Rejects Android artifacts with missing or privileged public configuration."
    inputs.property("supabaseUrl", supabaseUrl)
    inputs.property("supabasePublishableKey", supabasePublishableKey)
    inputs.property("rakyzuApiBaseUrl", rakyzuApiBaseUrl)
    doLast {
        val supabaseUri = runCatching { URI(supabaseUrl) }.getOrNull()
        check(
            supabaseUri?.scheme == "https" &&
                supabaseUri.host?.endsWith(".supabase.co") == true &&
                (supabaseUri.port == -1 || supabaseUri.port == 443) &&
                (supabaseUri.path.isNullOrEmpty() || supabaseUri.path == "/") &&
                supabaseUri.userInfo == null &&
                supabaseUri.query == null &&
                supabaseUri.fragment == null,
        ) { "SUPABASE_URL must be a hosted Supabase HTTPS origin." }
        check(supabasePublishableKey.length >= 20 && !supabasePublishableKey.any(Char::isWhitespace)) {
            "SUPABASE_PUBLISHABLE_KEY is missing or malformed."
        }
        check(!supabasePublishableKey.isPrivilegedSupabaseKey()) {
            "SUPABASE_PUBLISHABLE_KEY must never contain a secret or service-role key."
        }
        check(rakyzuApiBaseUrl == "https://api.rakyzu.my.id") {
            "RAKYZU_API_BASE_URL must use the production Rakyzu API origin."
        }
    }
}

android {
    namespace = "my.id.rakyzumusic"
    compileSdk = 37

    defaultConfig {
        applicationId = "my.id.rakyzumusic"
        minSdk = 26
        targetSdk = 37
        versionCode = 106
        versionName = "0.8.8"

        buildConfigField("String", "SUPABASE_URL", supabaseUrl.asBuildConfigString())
        buildConfigField(
            "String",
            "SUPABASE_PUBLISHABLE_KEY",
            supabasePublishableKey.asBuildConfigString(),
        )
        buildConfigField(
            "String",
            "RAKYZU_API_BASE_URL",
            rakyzuApiBaseUrl.asBuildConfigString(),
        )

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

extensions.configure<ApplicationAndroidComponentsExtension> {
    onVariants(selector().all()) { variant ->
        variant.outputs.forEach { output ->
            output.outputFileName.set("Rakyzu-Music-${output.versionName.get()}-${variant.name}.apk")
        }
    }
}

tasks.named("preBuild").configure {
    dependsOn(verifyPublicConfiguration)
}

dependencies {
    implementation(project(":core:data"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:model"))
    implementation(project(":core:playback"))
    implementation(project(":feature:auth"))
    implementation(project(":feature:admin"))
    implementation(project(":feature:home"))
    implementation(project(":feature:library"))
    implementation(project(":feature:player"))
    implementation(project(":feature:playlist"))
    implementation(project(":feature:profile"))
    implementation(project(":feature:search"))

    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.core:core-ktx:1.19.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.10.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")
    implementation("androidx.navigation3:navigation3-runtime:1.1.7")
    implementation("androidx.navigation3:navigation3-ui:1.1.7")
    implementation("io.coil-kt.coil3:coil:3.6.0")
    implementation("io.coil-kt.coil3:coil-network-cache-control:3.6.0")
    implementation("io.coil-kt.coil3:coil-network-okhttp:3.6.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-core:1.10.0")

    val composeBom = platform("androidx.compose:compose-bom:2026.08.00")
    implementation(composeBom)
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material3:material3-adaptive-navigation-suite")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    testImplementation("junit:junit:4.13.2")

    androidTestImplementation(composeBom)
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
}
