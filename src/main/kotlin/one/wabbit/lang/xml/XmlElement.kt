package one.wabbit.lang.xml

import java.io.File
import java.lang.StringBuilder
import kotlinx.serialization.Serializable
import one.wabbit.lang.xml.parsing.SpannedWithSpaces
import one.wabbit.lang.xml.parsing.XmlScanner
import one.wabbit.lang.xml.parsing.parseXmlDocument
import one.wabbit.parsing.CharInput
import one.wabbit.parsing.Pos
import one.wabbit.parsing.SpanAccess
import one.wabbit.parsing.SpanLike
import one.wabbit.parsing.Spanned
import one.wabbit.parsing.TextAndPosSpan
import one.wabbit.parsing.TextOnlySpan
import one.wabbit.parsing.TextSpan

@Serializable
sealed interface XmlElement<Span> {
    val firstSpan: Span
    val lastSpan: Span

    fun spanIterator(): Iterator<Span>

    fun descendentIterator(): Iterator<XmlElement<Span>>

    fun isWhitespaceOnlyText(): Boolean {
        when (this) {
            is XmlElement.Text -> return token.text.value.isBlank()
            is XmlElement.CDATA -> return token.text.value.isBlank()
            is XmlElement.EntityRef -> return token.defaultResolvedEntity.isBlank()
            is XmlElement.Comment -> return true
            else -> return false
        }
    }

    fun printRawXML(result: StringBuilder, spanAccess: SpanAccess<Span>)

    fun rawXML(spanAccess: SpanAccess<Span>): String {
        val result = StringBuilder()
        printRawXML(result, spanAccess)
        return result.toString()
    }

    @Serializable
    data class PI<Span>(val token: XmlToken.PI<Span>) : XmlElement<Span> {
        override val firstSpan: Span
            get() = token.open.span

        override val lastSpan: Span
            get() = token.close.span

        override fun spanIterator(): Iterator<Span> = token.spanIterator()

        override fun descendentIterator(): Iterator<XmlElement<Span>> = iterator {}

        override fun printRawXML(result: StringBuilder, spanAccess: SpanAccess<Span>) {
            token.printRawXML(result, spanAccess)
        }
    }

    @Serializable
    data class Tag<Span>(
        val openTag: XmlToken.OpeningTag<Span>,
        val closeTag: XmlToken.ClosingTag<Span>?,
        val children: List<XmlElement<Span>>,
    ) : XmlElement<Span> {
        override val firstSpan: Span
            get() = openTag.open.span

        override val lastSpan: Span
            get() = closeTag?.close?.span ?: openTag.close.span

        override fun spanIterator(): Iterator<Span> = iterator {
            yieldAll(openTag.spanIterator())
            for (c in children) {
                yieldAll(c.spanIterator())
            }
            if (closeTag != null) {
                yieldAll(closeTag.spanIterator())
            }
        }

        override fun printRawXML(result: StringBuilder, spanAccess: SpanAccess<Span>) {
            openTag.printRawXML(result, spanAccess)
            for (c in children) {
                c.printRawXML(result, spanAccess)
            }
            closeTag?.printRawXML(result, spanAccess)
        }

        override fun descendentIterator(): Iterator<XmlElement<Span>> = iterator {
            for (c in children) {
                yield(c)
                yieldAll(c.descendentIterator())
            }
        }

        val name: String
            get() = openTag.name.value

        val attrs: List<XmlAttr<Span>>
            get() = openTag.attrs

        fun attr(name: String): XmlAttrValue? =
            openTag.attrs.find { it.name.value == name }?.value?.value

        fun child(name: String): XmlElement.Tag<Span>? =
            children.find { it is XmlElement.Tag && it.name == name } as? XmlElement.Tag

        fun children(name: String): List<XmlElement.Tag<Span>> =
            children.filterIsInstance<XmlElement.Tag<Span>>().filter { it.name == name }

        fun childTags(): List<XmlElement.Tag<Span>> =
            children.filterIsInstance<XmlElement.Tag<Span>>()

        fun innerRawText(spanAccess: SpanAccess<Span>): String {
            val result: StringBuilder = StringBuilder()
            for (c in children) {
                for (s in c.spanIterator()) {
                    result.append(spanAccess.raw(s))
                }
            }
            return result.toString()
        }

        fun innerText(spanAccess: SpanAccess<Span>): String {
            val result: StringBuilder = StringBuilder()
            for (c in children) {
                when (c) {
                    is XmlElement.Text -> result.append(c.token.text.value)
                    is XmlElement.CDATA -> result.append(c.token.text.value)
                    is XmlElement.EntityRef -> result.append(c.defaultResolvedEntity)
                    is XmlElement.Comment -> continue // comments are not included in inner text
                    is XmlElement.PI ->
                        continue // processing instructions are not included in inner text
                    is XmlElement.UnopenedTag -> result.append(c.rawXML(spanAccess))
                    is XmlElement.UnclosedTag -> result.append(c.rawXML(spanAccess))
                    is XmlElement.Tag -> {
                        result.append(c.openTag.rawXML(spanAccess))
                        result.append(c.innerText(spanAccess))
                        if (c.closeTag != null) {
                            result.append(c.closeTag.rawXML(spanAccess))
                        }
                    }
                }
            }
            return result.toString()
        }

        fun findChildTag(
            recursive: Boolean,
            predicate: (XmlElement.Tag<Span>) -> Boolean,
        ): XmlElement.Tag<Span>? {
            if (predicate(this)) return this
            for (c in children) {
                if (c !is XmlElement.Tag) continue

                if (predicate(c)) return c

                if (recursive) {
                    val result = c.findChildTag(recursive, predicate)
                    if (result != null) return result
                }
            }
            return null
        }
    }

    @Serializable
    sealed interface TextLike<Span> : XmlElement<Span> {
        val text: String
    }

    @Serializable
    data class Text<Span>(val token: XmlToken.Text<Span>) : TextLike<Span> {
        override val firstSpan: Span
            get() = token.text.span

        override val lastSpan: Span
            get() = token.text.span

        override fun spanIterator(): Iterator<Span> = token.spanIterator()

        override fun descendentIterator(): Iterator<XmlElement<Span>> = iterator {}

        override val text: String
            get() = token.text.value

        override fun printRawXML(result: StringBuilder, spanAccess: SpanAccess<Span>) {
            token.printRawXML(result, spanAccess)
        }
    }

    @Serializable
    data class CDATA<Span>(val token: XmlToken.CDATA<Span>) : TextLike<Span> {
        override val firstSpan: Span
            get() = token.text.span

        override val lastSpan: Span
            get() = token.text.span

        override fun spanIterator(): Iterator<Span> = token.spanIterator()

        override fun descendentIterator(): Iterator<XmlElement<Span>> = iterator {}

        override val text: String
            get() = token.text.value

        override fun printRawXML(result: StringBuilder, spanAccess: SpanAccess<Span>) {
            token.printRawXML(result, spanAccess)
        }
    }

    @Serializable
    data class EntityRef<Span>(val token: XmlToken.EntityRef<Span>) : TextLike<Span> {
        override val firstSpan: Span
            get() = token.name.span

        override val lastSpan: Span
            get() = token.name.span

        override fun spanIterator(): Iterator<Span> = token.spanIterator()

        override fun descendentIterator(): Iterator<XmlElement<Span>> = iterator {}

        val defaultResolvedEntity: String
            get() = token.defaultResolvedEntity

        override val text: String
            get() = token.defaultResolvedEntity

        override fun printRawXML(result: StringBuilder, spanAccess: SpanAccess<Span>) {
            token.printRawXML(result, spanAccess)
        }
    }

    @Serializable
    data class Comment<Span>(val token: XmlToken.Comment<Span>) : XmlElement<Span> {
        override val firstSpan: Span
            get() = token.span.span

        override val lastSpan: Span
            get() = token.span.span

        override fun spanIterator(): Iterator<Span> = token.spanIterator()

        override fun descendentIterator(): Iterator<XmlElement<Span>> = iterator {}

        override fun printRawXML(result: StringBuilder, spanAccess: SpanAccess<Span>) {
            token.printRawXML(result, spanAccess)
        }
    }

    @Serializable sealed interface InvalidTag<Span> : XmlElement<Span>

    @Serializable
    data class UnopenedTag<Span>(val token: XmlToken.ClosingTag<Span>) : InvalidTag<Span> {
        override val firstSpan: Span
            get() = token.open.span

        override val lastSpan: Span
            get() = token.close.span

        override fun spanIterator(): Iterator<Span> = token.spanIterator()

        override fun descendentIterator(): Iterator<XmlElement<Span>> = iterator {}

        override fun printRawXML(result: StringBuilder, spanAccess: SpanAccess<Span>) {
            token.printRawXML(result, spanAccess)
        }
    }

    @Serializable
    data class UnclosedTag<Span>(val token: XmlToken.OpeningTag<Span>) : InvalidTag<Span> {
        override val firstSpan: Span
            get() = token.open.span

        override val lastSpan: Span
            get() = token.close.span

        override fun spanIterator(): Iterator<Span> = token.spanIterator()

        override fun descendentIterator(): Iterator<XmlElement<Span>> = iterator {}

        override fun printRawXML(result: StringBuilder, spanAccess: SpanAccess<Span>) {
            token.printRawXML(result, spanAccess)
        }
    }

    companion object {
        private fun span(text: String): TextAndPosSpan = TextAndPosSpan(text, Pos.start, Pos.start)

        fun tag(
            name: String,
            attrs: List<Pair<String, XmlAttrValue>> = emptyList(),
            children: List<XmlElement<TextAndPosSpan>> = emptyList(),
        ): XmlElement.Tag<TextAndPosSpan> =
            XmlElement.Tag(
                openTag =
                    XmlToken.OpeningTag(
                        open = Spanned(span("<"), Unit),
                        name = SpannedWithSpaces(name, span(name), span(" ")),
                        attrs =
                            attrs.map {
                                XmlAttr(
                                    name = SpannedWithSpaces(it.first, span(it.first), span("")),
                                    eq = SpannedWithSpaces(Unit, span("="), span("")),
                                    value =
                                        SpannedWithSpaces(
                                            it.second,
                                            span(it.second.asString()),
                                            span(""),
                                        ),
                                )
                            },
                        close =
                            Spanned(
                                span(">"),
                                if (children.isEmpty()) CloseType.SlashGreater
                                else CloseType.Greater,
                            ),
                    ),
                children = children,
                closeTag =
                    if (children.isEmpty()) {
                        null
                    } else {
                        XmlToken.ClosingTag(
                            open = SpannedWithSpaces(Unit, span("</"), span("")),
                            name = SpannedWithSpaces(name, span(name), span("")),
                            close = Spanned(span(">"), Unit),
                        )
                    },
            )
    }
}

class MultipleRootTagsException(tagNames: List<String>) : Exception("Multiple root tags: $tagNames")

class NoRootTagException : Exception("No root tag")

data class XmlDocument<Span>(val children: List<XmlElement<Span>>) {
    fun printRawXML(result: StringBuilder, spanAccess: SpanAccess<Span>) {
        for (c in children) {
            c.printRawXML(result, spanAccess)
        }
    }

    fun rawXML(spanAccess: SpanAccess<Span>): String {
        val result = StringBuilder()
        printRawXML(result, spanAccess)
        return result.toString()
    }

    val root: XmlElement.Tag<Span>
        get() {
            val tags = children.filterIsInstance<XmlElement.Tag<Span>>()
            if (tags.size > 1) throw MultipleRootTagsException(tags.map { it.name })
            if (tags.isEmpty()) throw NoRootTagException()
            return tags.first()
        }

    fun findAllInvalidTags(): List<XmlElement<Span>> {
        val result = mutableListOf<XmlElement.InvalidTag<Span>>()

        fun go(tag: XmlElement.Tag<Span>) {
            for (c in tag.children) {
                if (c is XmlElement.Tag) {
                    go(c)
                } else if (c is XmlElement.UnopenedTag) {
                    result.add(c)
                } else if (c is XmlElement.UnclosedTag) {
                    result.add(c)
                }
            }
        }

        for (c in children) {
            if (c is XmlElement.Tag) {
                go(c)
            } else if (c is XmlElement.UnopenedTag) {
                result.add(c)
            } else if (c is XmlElement.UnclosedTag) {
                result.add(c)
            }
        }
        return result
    }

    fun findAllTextFragments(): List<XmlElement.Text<Span>> {
        val result = mutableListOf<XmlElement.Text<Span>>()

        fun go(tag: XmlElement.Tag<Span>) {
            for (c in tag.children) {
                if (c is XmlElement.Tag) {
                    go(c)
                } else if (c is XmlElement.Text) {
                    result.add(c)
                }
            }
        }

        for (c in children) {
            if (c is XmlElement.Tag) {
                go(c)
            } else if (c is XmlElement.Text) {
                result.add(c)
            }
        }
        return result
    }

    companion object {
        fun <Span : TextSpan> parse(
            input: CharInput<Span>,
            spanLike: SpanLike<Span>,
        ): XmlDocument<Span> {
            val scanner = XmlScanner(input)
            return parseXmlDocument(scanner, spanLike)
        }

        fun parseWithTextAndPosSpans(text: String): XmlDocument<TextAndPosSpan> {
            val input = CharInput.withTextAndPosSpans(text)
            val scanner = XmlScanner(input)
            return parseXmlDocument(scanner, TextAndPosSpan.spanLike)
        }

        fun parseWithTextOnlySpans(text: String): XmlDocument<TextOnlySpan> {
            val input = CharInput.withTextOnlySpans(text)
            val scanner = XmlScanner(input)
            return parseXmlDocument(scanner, TextOnlySpan.spanLike)
        }

        fun parseWithTextAndPosSpans(file: File): XmlDocument<TextAndPosSpan> =
            parseWithTextAndPosSpans(file.readText())

        fun parseWithTextOnlySpans(file: File): XmlDocument<TextOnlySpan> =
            parseWithTextOnlySpans(file.readText())
    }
}
