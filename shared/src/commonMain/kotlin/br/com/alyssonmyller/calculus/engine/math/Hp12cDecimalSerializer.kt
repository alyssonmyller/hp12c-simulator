package br.com.alyssonmyller.calculus.engine.math

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * Serializa [Hp12cDecimal] como uma string decimal exata — ex: `"1234.56"`, `"0.3333333333"`.
 *
 * **Por que String e não Double?**
 * `Hp12cDecimal.toString()` produz a representação decimal canônica de 10 dígitos. Um
 * round-trip `Hp12cDecimal.of(value.toString())` é exato, sem nenhum ruído de ponto
 * flutuante binário. Serializar como Double truncaria para 15-16 dígitos significativos
 * e introduziria erros de representação (ex: `0.1` em IEEE-754 não é exatamente `0.1`).
 *
 * Registrado via [PersistenceSerializersModule] e ativado com `@Contextual` nos campos
 * das classes de estado. Ver `arquitetura/persistence.md` §4.2.
 */
object Hp12cDecimalSerializer : KSerializer<Hp12cDecimal> {

    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("Hp12cDecimal", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: Hp12cDecimal) =
        encoder.encodeString(value.toString())

    override fun deserialize(decoder: Decoder): Hp12cDecimal =
        Hp12cDecimal.of(decoder.decodeString())
}
