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
val googleOAuthWebClientId = providers.gradleProperty("GOOGLE_OAUTH_WEB_CLIENT_ID")
    .orElse(providers.environmentVariable("GOOGLE_OAUTH_WEB_CLIENT_ID"))
    .getOrElse("201396501756-dkipfptsqtbqnln8q7q6jrp66v0l3brg.apps.googleusercontent.com")

val verifyPublicConfiguration = tasks.register("verifyPublicConfiguration") {
    group = "verification"
    description = "Rejects Android artifacts with missing or privileged public configuration."
    inputs.property("supabaseUrl", supabaseUrl)
    inputs.property("supabasePublishableKey", supabasePublishableKey)
    inputs.property("rakyzuApiBaseUrl", rakyzuApiBaseUrl)
    inputs.property("googleOAuthWebClientId", googleOAuthWebClientId)
    doLast {
        val configuredSupabaseUrl = inputs.properties.getValue("supabaseUrl") as String
        val configuredPublishableKey =
            inputs.properties.getValue("supabasePublishableKey") as String
        val configuredApiBaseUrl = inputs.properties.getValue("rakyzuApiBaseUrl") as String
        val configuredGoogleClientId =
            inputs.properties.getValue("googleOAuthWebClientId") as String
        val supabaseUri = runCatching { URI(configuredSupabaseUrl) }.getOrNull()
        check(
            supabaseUri?.scheme == "https" &&
                supabaseUri.host?.endsWith(".supabase.co") == true &&
                (supabaseUri.port == -1 || supabaseUri.port == 443) &&
                (supabaseUri.path.isNullOrEmpty() || supabaseUri.path == "/") &&
                supabaseUri.userInfo == null &&
                supabaseUri.query == null &&
                supabaseUri.fragment == null,
        ) { "SUPABASE_URL must be a hosted Supabase HTTPS origin." }
        check(
            configuredPublishableKey.length >= 20 &&
                !configuredPublishableKey.any(Char::isWhitespace),
        ) {
            "SUPABASE_PUBLISHABLE_KEY is missing or malformed."
        }
        val isPrivilegedKey = if (configuredPublishableKey.startsWith("sb_secret_", ignoreCase = true)) {
            true
        } else {
            val payload = configuredPublishableKey.split('.').getOrNull(1)
            payload != null && runCatching {
                String(
                    Base64.getUrlDecoder().decode(payload.padEnd((payload.length + 3) / 4 * 4, '=')),
                )
            }.getOrDefault("").contains(Regex("\"role\"\\s*:\\s*\"service_role\""))
        }
        check(!isPrivilegedKey) {
            "SUPABASE_PUBLISHABLE_KEY must never contain a secret or service-role key."
        }
        check(configuredApiBaseUrl == "https://api.rakyzu.my.id") {
            "RAKYZU_API_BASE_URL must use the production Rakyzu API origin."
        }
        check(
            configuredGoogleClientId.endsWith(".apps.googleusercontent.com") &&
                configuredGoogleClientId.none(Char::isWhitespace),
        ) {
            "GOOGLE_OAUTH_WEB_CLIENT_ID must be a valid public Google Web client ID."
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
        versionCode = 109
        versionName = "0.8.8.3"

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
        buildConfigField(
            "String",
            "GOOGLE_OAUTH_WEB_CLIENT_ID",
            googleOAuthWebClientId.asBuildConfigString(),
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
