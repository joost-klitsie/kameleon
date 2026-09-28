package dev.klitsie.kameleon.dsl

import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.MapProperty
import java.io.Serializable
import javax.inject.Inject

sealed interface BuildConfigValue : Serializable {
    data class StringValue(val value: String) : BuildConfigValue
    data class BooleanValue(val value: Boolean) : BuildConfigValue
    data class IntValue(val value: Int) : BuildConfigValue
    data class LongValue(val value: Long) : BuildConfigValue
}

open class BuildConfigBlock @Inject constructor(objects: ObjectFactory) {
    val fields: MapProperty<String, BuildConfigValue> =
        objects.mapProperty(String::class.java, BuildConfigValue::class.java)

    fun put(name: String, value: Any) {
        when (value) {
            is String -> putString(name, value)
            is Boolean -> putBoolean(name, value)
            is Int -> putInt(name, value)
            is Long -> putLong(name, value)
            is BuildConfigValue -> fields.put(name, value)
            else -> throw IllegalArgumentException(
                "Unsupported BuildConfig type '${value::class.qualifiedName}' for key '$name'. " +
                "Supported types: String, Boolean, Int, Long."
            )
        }
    }

    fun putString(name: String, value: String) {
        fields.put(name, BuildConfigValue.StringValue(value))
    }

    fun putBoolean(name: String, value: Boolean) {
        fields.put(name, BuildConfigValue.BooleanValue(value))
    }

    fun putInt(name: String, value: Int) {
        fields.put(name, BuildConfigValue.IntValue(value))
    }

    fun putLong(name: String, value: Long) {
        fields.put(name, BuildConfigValue.LongValue(value))
    }

    infix fun String.to(value: Any) {
        put(this, value)
    }

    operator fun set(name: String, value: Any) {
        put(name, value)
    }
}
