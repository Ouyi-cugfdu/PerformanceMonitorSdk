// 项目根目录的 build.gradle.kts
plugins {
    id("com.android.application") version "7.4.2" apply false
    id("com.android.library") version "7.4.2" apply false
    id("org.jetbrains.kotlin.android") version "1.8.0" apply false
}

tasks.register("clean", Delete::class) {
    delete(rootProject.buildDir)
}