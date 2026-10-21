package com.atmosferast.orbita.data.remote

import java.math.BigDecimal
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.jsonPrimitive

/**
 * The Orbita API sends amounts and rates as text ("1245.80"), never as a JSON number, so no
 * platform reads them as a Double. This reads the literal text (also a number, to be safe) and
 * always writes text. Every amount and rate of a DTO must use it:
 * `@Serializable(with = BigDecimalSerializer::class)`.
 */
object BigDecimalSerializer : KSerializer<BigDecimal> {
    override val descriptor = PrimitiveSerialDescriptor("BigDecimal", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): BigDecimal {
        val json = decoder as? JsonDecoder ?: error("Only JSON is supported")
        return BigDecimal(json.decodeJsonElement().jsonPrimitive.content)
    }

    override fun serialize(encoder: Encoder, value: BigDecimal) =
        encoder.encodeString(value.toPlainString())
}
