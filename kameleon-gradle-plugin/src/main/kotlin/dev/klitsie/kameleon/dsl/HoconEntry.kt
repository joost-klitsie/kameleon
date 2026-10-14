package dev.klitsie.kameleon.dsl

import java.io.Serializable

data class HoconEntry(
    val path: String,
    val value: HoconValue,
    val environmentOverride: String? = null
) : Serializable

sealed interface HoconValue : Serializable {
    data class StringValue(val value: String) : HoconValue
    data class IntegerValue(val value: Int) : HoconValue
    data class BooleanValue(val value: Boolean) : HoconValue
    data class RawValue(val value: String) : HoconValue
}
