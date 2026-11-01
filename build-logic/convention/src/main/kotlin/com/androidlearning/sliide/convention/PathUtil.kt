package com.androidlearning.sliide.convention

import org.gradle.api.Project

private const val BASE_PACKAGE = "com.androidlearning.sliide"

/** `:users:data` → `com.androidlearning.sliide.users.data`. */
internal fun Project.pathToPackageName(): String = BASE_PACKAGE + path.replace(':', '.').lowercase()
