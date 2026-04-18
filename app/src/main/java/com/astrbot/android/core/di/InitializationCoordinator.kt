package com.astrbot.android.core.di

import android.content.Context

class InitializationCoordinator(
    private val initializers: List<AppInitializer>,
) {
    fun initializeAll(context: Context) {
        for (initializer in sortedInitializers()) {
            try {
                initializer.initialize(context)
            } catch (throwable: Throwable) {
                throw InitializationException(initializer.key, throwable)
            }
        }
    }

    internal fun sortedInitializers(): List<AppInitializer> {
        val byKey = mutableMapOf<String, AppInitializer>()
        for (init in initializers) {
            require(init.key !in byKey) {
                "Duplicate initializer key: ${init.key}"
            }
            byKey[init.key] = init
        }

        for (init in initializers) {
            for (dep in init.dependencies) {
                require(dep in byKey) {
                    "Initializer '${init.key}' depends on unknown key: $dep"
                }
            }
        }

        val sorted = mutableListOf<AppInitializer>()
        val visited = mutableSetOf<String>()
        val visiting = mutableSetOf<String>()

        fun visit(key: String) {
            if (key in visited) return
            if (key in visiting) {
                throw IllegalStateException("Initialization cycle detected involving: $key")
            }
            visiting += key
            val init = byKey.getValue(key)
            for (dep in init.dependencies.sorted()) {
                visit(dep)
            }
            visiting -= key
            visited += key
            sorted += init
        }

        for (key in byKey.keys.sorted()) {
            visit(key)
        }
        return sorted
    }
}

class InitializationException(
    val initializerKey: String,
    cause: Throwable,
) : RuntimeException("Initializer failed: $initializerKey", cause)
