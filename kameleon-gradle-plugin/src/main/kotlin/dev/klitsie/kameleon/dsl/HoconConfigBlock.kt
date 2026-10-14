package dev.klitsie.kameleon.dsl

import org.gradle.api.file.Directory
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderFactory
import javax.inject.Inject

open class HoconConfigBlock @Inject constructor(
    objects: ObjectFactory,
    private val providers: ProviderFactory,
    private val projectDirectory: Provider<Directory>,
    private val rootProjectDirectory: Provider<Directory>,
) {
    val fileName: Property<String> = objects.property(String::class.java).convention("kameleon.conf")

    val entries: ListProperty<HoconEntry> = objects.listProperty(HoconEntry::class.java)

    fun localProperty(key: String): Provider<String> =
        createLocalPropertyProvider(providers, projectDirectory, rootProjectDirectory, key)

    fun localProperty(key: String, defaultValue: String): Provider<String> =
        localProperty(key).orElse(defaultValue)

    fun putString(path: String, value: String, environmentOverride: String? = null) {
        entries.add(HoconEntry(path, HoconValue.StringValue(value), environmentOverride))
    }

    fun putString(path: String, value: Provider<String>, environmentOverride: String? = null) {
        entries.add(value.map { HoconEntry(path, HoconValue.StringValue(it), environmentOverride) })
    }

    fun putInteger(path: String, value: Int, environmentOverride: String? = null) {
        entries.add(HoconEntry(path, HoconValue.IntegerValue(value), environmentOverride))
    }

    fun putInteger(path: String, value: Provider<Int>, environmentOverride: String? = null) {
        entries.add(value.map { HoconEntry(path, HoconValue.IntegerValue(it), environmentOverride) })
    }

    fun putInt(path: String, value: Int, environmentOverride: String? = null) {
        putInteger(path, value, environmentOverride)
    }

    fun putInt(path: String, value: Provider<Int>, environmentOverride: String? = null) {
        putInteger(path, value, environmentOverride)
    }

    fun putBoolean(path: String, value: Boolean, environmentOverride: String? = null) {
        entries.add(HoconEntry(path, HoconValue.BooleanValue(value), environmentOverride))
    }

    fun putBoolean(path: String, value: Provider<Boolean>, environmentOverride: String? = null) {
        entries.add(value.map { HoconEntry(path, HoconValue.BooleanValue(it), environmentOverride) })
    }

    fun putRaw(path: String, rawValue: String, environmentOverride: String? = null) {
        entries.add(HoconEntry(path, HoconValue.RawValue(rawValue), environmentOverride))
    }

    fun putRaw(path: String, rawValue: Provider<String>, environmentOverride: String? = null) {
        entries.add(rawValue.map { HoconEntry(path, HoconValue.RawValue(it), environmentOverride) })
    }
}
