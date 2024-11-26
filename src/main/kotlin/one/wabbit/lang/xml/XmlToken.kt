package one.wabbit.lang.xml

import one.wabbit.data.iteratorOf
import one.wabbit.parsing.Pos
import one.wabbit.parsing.SpanOut
import one.wabbit.parsing.Spanned
import kotlinx.serialization.Serializable
import one.wabbit.lang.xml.parsing.SpannedWithSpaces

@Serializable
sealed class XmlAttrValue {
    fun decodeString(): kotlin.String = when (this) {
        is XmlAttrValue.String ->
            value.replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&amp;", "&")
        is XmlAttrValue.Integer -> "$value"
        is XmlAttrValue.Real -> "$value"
        is XmlAttrValue.Boolean -> "$value"
    }

    fun asString(): kotlin.String = when (this) {
        is XmlAttrValue.String -> "$value"
        is XmlAttrValue.Integer -> "$value"
        is XmlAttrValue.Real -> "$value"
        is XmlAttrValue.Boolean -> "$value"
    }

    @Serializable
    data class String(val value: kotlin.String) : XmlAttrValue()
    @Serializable
    data class Integer(val value: kotlin.String) : XmlAttrValue()
    @Serializable
    data class Real(val value: kotlin.String) : XmlAttrValue()
    @Serializable
    data class Boolean(val value: kotlin.Boolean) : XmlAttrValue()
}

@Serializable
data class XmlAttr<out Span>(
    val name: SpannedWithSpaces<Span, String>,
    val eq: SpannedWithSpaces<Span, Unit>,
    val value: SpannedWithSpaces<Span, XmlAttrValue>)

@Serializable
enum class CloseType {
    SlashGreater, Greater
}

@Serializable sealed class XmlToken<out Span> {
    abstract fun spanIterator(): Iterator<Span>

    abstract fun printRawXML(result: StringBuilder, spanOut: SpanOut<Span>)

    fun rawXML(spanOut: SpanOut<Span>): String {
        val result = StringBuilder()
        printRawXML(result, spanOut)
        return result.toString()
    }

    @Serializable data class EOF<out Span>(val pos: Pos) : XmlToken<Span>() {
        override fun spanIterator(): Iterator<Span> = iteratorOf()
        override fun printRawXML(result: StringBuilder, spanOut: SpanOut<Span>) { } // Do nothing
    }
    @Serializable data class Comment<out Span>(val span: Spanned<Span, String>) : XmlToken<Span>() {
        override fun spanIterator(): Iterator<Span> = iteratorOf(span.span)
        override fun printRawXML(result: StringBuilder, spanOut: SpanOut<Span>) {
            // FIXME: we don't need Spanned here.
            result.append(spanOut.raw(span.span))
        }
    }

    @Serializable data class Text<out Span>(val text: Spanned<Span, String>) : XmlToken<Span>() {
        override fun spanIterator(): Iterator<Span> = iteratorOf(text.span)
        override fun printRawXML(result: StringBuilder, spanOut: SpanOut<Span>) {
            result.append(spanOut.raw(text.span))
        }
    }
    @Serializable data class CDATA<out Span>(val text: Spanned<Span, String>) : XmlToken<Span>() {
        override fun spanIterator(): Iterator<Span> = iteratorOf(text.span)
        override fun printRawXML(result: StringBuilder, spanOut: SpanOut<Span>) {
            result.append(spanOut.raw(text.span))
        }
    }
    @Serializable data class EntityRef<out Span>(val name: Spanned<Span, String>) : XmlToken<Span>() {
        override fun spanIterator(): Iterator<Span> = iteratorOf(name.span)
        override fun printRawXML(result: StringBuilder, spanOut: SpanOut<Span>) {
            result.append(spanOut.raw(name.span))
        }

        val defaultResolvedEntity: String get() {
            when {
                name.value == "amp" -> return "&"
                name.value == "lt" -> return "<"
                name.value == "gt" -> return ">"
                name.value == "apos" -> return "'"
                name.value == "quot" -> return "\""
                name.value.startsWith("#") -> {
                    val id = name.value.substring(1)
                    if (id.startsWith("x")) {
                        val value = id.substring(1).toInt(16)
                        return value.toChar().toString()
                    } else {
                        val value = id.toInt()
                        return value.toChar().toString()
                    }
                }
                else -> return "&$name;"
            }
        }
    }

    @Serializable data class PI<out Span>(
        val open: Spanned<Span, Unit>,
        val name: SpannedWithSpaces<Span, String>,
        val attrs: List<XmlAttr<Span>>,
        val close: Spanned<Span, Unit>
    ) : XmlToken<Span>() {
        override fun spanIterator(): Iterator<Span> = iterator {
            yield(open.span)
            yield(name.span)
            yield(name.spaces)
            attrs.forEach {
                yield(it.name.span)
                yield(it.name.spaces)
                yield(it.eq.span)
                yield(it.eq.spaces)
                yield(it.value.span)
                yield(it.value.spaces)
            }
            yield(close.span)
        }

        override fun printRawXML(result: StringBuilder, spanOut: SpanOut<Span>) {
            result.append(spanOut.raw(open.span))
            result.append(spanOut.raw(name.span))
            result.append(spanOut.raw(name.spaces))
            for (attr in attrs) {
                result.append(spanOut.raw(attr.name.span))
                result.append(spanOut.raw(attr.name.spaces))
                result.append(spanOut.raw(attr.eq.span))
                result.append(spanOut.raw(attr.eq.spaces))
                result.append(spanOut.raw(attr.value.span))
                result.append(spanOut.raw(attr.value.spaces))
            }
            result.append(spanOut.raw(close.span))
        }
    }

    @Serializable data class OpeningTag<Span>(
        val open: Spanned<Span, Unit>,
        val name: SpannedWithSpaces<Span, String>,
        val attrs: List<XmlAttr<Span>>,
        val close: Spanned<Span, CloseType>
    ) : XmlToken<Span>() {
        val closing: Boolean get() = close.value == CloseType.SlashGreater

        override fun spanIterator(): Iterator<Span> = iterator {
            yield(open.span)
            yield(name.span)
            yield(name.spaces)
            attrs.forEach {
                yield(it.name.span)
                yield(it.name.spaces)
                yield(it.eq.span)
                yield(it.eq.spaces)
                yield(it.value.span)
                yield(it.value.spaces)
            }
            yield(close.span)
        }

        override fun printRawXML(result: StringBuilder, spanOut: SpanOut<Span>) {
            result.append(spanOut.raw(open.span))
            result.append(spanOut.raw(name.span))
            result.append(spanOut.raw(name.spaces))
            for (attr in attrs) {
                result.append(spanOut.raw(attr.name.span))
                result.append(spanOut.raw(attr.name.spaces))
                result.append(spanOut.raw(attr.eq.span))
                result.append(spanOut.raw(attr.eq.spaces))
                result.append(spanOut.raw(attr.value.span))
                result.append(spanOut.raw(attr.value.spaces))
            }
            result.append(spanOut.raw(close.span))
        }
    }

    @Serializable data class ClosingTag<Span>(
        val open: SpannedWithSpaces<Span, Unit>,
        val name: SpannedWithSpaces<Span, String>,
        val close: Spanned<Span, Unit>
    ) : XmlToken<Span>() {
        override fun spanIterator(): Iterator<Span> = iterator {
            yield(open.span)
            yield(open.spaces)
            yield(name.span)
            yield(name.spaces)
            yield(close.span)
        }

        override fun printRawXML(result: StringBuilder, spanOut: SpanOut<Span>) {
            result.append(spanOut.raw(open.span))
            result.append(spanOut.raw(open.spaces))
            result.append(spanOut.raw(name.span))
            result.append(spanOut.raw(name.spaces))
            result.append(spanOut.raw(close.span))
        }
    }
}
