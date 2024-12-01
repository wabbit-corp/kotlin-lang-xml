package one.wabbit.lang.xml

import one.wabbit.parsing.*
import java.io.File
import java.lang.StringBuilder
import kotlinx.serialization.Serializable
import one.wabbit.lang.xml.parsing.SpannedWithSpaces
import one.wabbit.lang.xml.parsing.XmlScanner
import one.wabbit.lang.xml.parsing.parseXmlDocument

@Serializable sealed interface XmlElement<Span> {
    fun spanIterator(): Iterator<Span>
    fun descendentIterator(): Iterator<XmlElement<Span>>

    fun printRawXML(result: StringBuilder, spanOut: SpanOut<Span>)

    fun rawXML(spanOut: SpanOut<Span>): String {
        val result = StringBuilder()
        printRawXML(result, spanOut)
        return result.toString()
    }

    @Serializable data class PI<Span>(val token: XmlToken.PI<Span>) : XmlElement<Span> {
        override fun spanIterator(): Iterator<Span> = token.spanIterator()
        override fun descendentIterator(): Iterator<XmlElement<Span>> = iterator {  }
        override fun printRawXML(result: StringBuilder, spanOut: SpanOut<Span>) {
            token.printRawXML(result, spanOut)
        }
    }

    @Serializable data class Tag<Span>(
        val openTag: XmlToken.OpeningTag<Span>,
        val closeTag: XmlToken.ClosingTag<Span>?,
        val children: List<XmlElement<Span>>
    ) : XmlElement<Span>
    {
        override fun spanIterator(): Iterator<Span> = iterator {
            yieldAll(openTag.spanIterator())
            for (c in children) {
                yieldAll(c.spanIterator())
            }
            if (closeTag != null) {
                yieldAll(closeTag.spanIterator())
            }
        }

        override fun printRawXML(result: StringBuilder, spanOut: SpanOut<Span>) {
            openTag.printRawXML(result, spanOut)
            for (c in children) {
                c.printRawXML(result, spanOut)
            }
            closeTag?.printRawXML(result, spanOut)
        }

        override fun descendentIterator(): Iterator<XmlElement<Span>> = iterator {
            for (c in children) {
                yield(c)
                yieldAll(c.descendentIterator())
            }
        }

        val name: String get() = openTag.name.value
        val attrs: List<XmlAttr<Span>>
            get() = openTag.attrs
        fun attr(name: String): XmlAttrValue? =
            openTag.attrs.find { it.name.value == name }?.value?.value
        fun child(name: String): XmlElement.Tag<Span>? =
            children.find { it is XmlElement.Tag && it.name == name } as? XmlElement.Tag
        fun children(name: String): List<XmlElement.Tag<Span>> =
            children.filterIsInstance<XmlElement.Tag<Span>>()
                .filter { it.name == name }

        fun childTags(): List<XmlElement.Tag<Span>> =
            children.filterIsInstance<XmlElement.Tag<Span>>()

        fun innerRawText(spanOut: SpanOut<Span>): String {
            val result: StringBuilder = StringBuilder()
            for (c in children) {
                for (s in c.spanIterator()) {
                    result.append(spanOut.raw(s))
                }
            }
            return result.toString()
        }

        fun findChildTag (recursive: Boolean, predicate: (XmlElement.Tag<Span>) -> Boolean): XmlElement.Tag<Span>? {
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

    @Serializable sealed interface TextLike<Span> : XmlElement<Span> {
        val text: String
    }

    @Serializable data class Text<Span>(val token: XmlToken.Text<Span>) : TextLike<Span> {
        override fun spanIterator(): Iterator<Span> = token.spanIterator()
        override fun descendentIterator(): Iterator<XmlElement<Span>> = iterator {  }

        override val text: String
            get() = token.text.value

        override fun printRawXML(result: StringBuilder, spanOut: SpanOut<Span>) {
            token.printRawXML(result, spanOut)
        }
    }
    @Serializable data class CDATA<Span>(val token: XmlToken.CDATA<Span>) : TextLike<Span> {
        override fun spanIterator(): Iterator<Span> = token.spanIterator()
        override fun descendentIterator(): Iterator<XmlElement<Span>> = iterator {  }

        override val text: String
            get() = token.text.value

        override fun printRawXML(result: StringBuilder, spanOut: SpanOut<Span>) {
            token.printRawXML(result, spanOut)
        }
    }
    @Serializable data class EntityRef<Span>(val token: XmlToken.EntityRef<Span>) : TextLike<Span> {
        override fun spanIterator(): Iterator<Span> = token.spanIterator()
        override fun descendentIterator(): Iterator<XmlElement<Span>> = iterator {  }

        val defaultResolvedEntity: String get() = token.defaultResolvedEntity

        override val text: String
            get() = token.defaultResolvedEntity

        override fun printRawXML(result: StringBuilder, spanOut: SpanOut<Span>) {
            token.printRawXML(result, spanOut)
        }
    }
    @Serializable data class Comment<Span>(val token: XmlToken.Comment<Span>) : XmlElement<Span> {
        override fun spanIterator(): Iterator<Span> = token.spanIterator()
        override fun descendentIterator(): Iterator<XmlElement<Span>> = iterator {  }

        override fun printRawXML(result: StringBuilder, spanOut: SpanOut<Span>) {
            token.printRawXML(result, spanOut)
        }
    }

    @Serializable sealed interface InvalidTag<Span> : XmlElement<Span>
    @Serializable data class UnopenedTag<Span>(val token: XmlToken.ClosingTag<Span>) : InvalidTag<Span> {
        override fun spanIterator(): Iterator<Span> = token.spanIterator()
        override fun descendentIterator(): Iterator<XmlElement<Span>> = iterator {  }

        override fun printRawXML(result: StringBuilder, spanOut: SpanOut<Span>) {
            token.printRawXML(result, spanOut)
        }
    }
    @Serializable data class UnclosedTag<Span>(val token: XmlToken.OpeningTag<Span>) : InvalidTag<Span> {
        override fun spanIterator(): Iterator<Span> = token.spanIterator()
        override fun descendentIterator(): Iterator<XmlElement<Span>> = iterator {  }

        override fun printRawXML(result: StringBuilder, spanOut: SpanOut<Span>) {
            token.printRawXML(result, spanOut)
        }
    }

    companion object {
        private fun span(text: String): TextAndPosSpan = TextAndPosSpan(text, Pos.start, Pos.start)

        fun tag(name: String, attrs: List<Pair<String, XmlAttrValue>> = emptyList(), children: List<XmlElement<TextAndPosSpan>> = emptyList()): XmlElement.Tag<TextAndPosSpan> {
            return XmlElement.Tag(
                openTag = XmlToken.OpeningTag(
                    open = Spanned(span("<"), Unit),
                    name = SpannedWithSpaces(name, span(name), span(" ")),
                    attrs = attrs.map {
                        XmlAttr(
                            name = SpannedWithSpaces(it.first, span(it.first), span("")),
                            eq = SpannedWithSpaces(Unit, span("="), span("")),
                            value = SpannedWithSpaces(it.second, span(it.second.asString()), span(""))
                        )
                    },
                    close = Spanned(span(">"), if (children.isEmpty()) CloseType.SlashGreater else CloseType.Greater),
                ),
                children = children,
                closeTag = if (children.isEmpty()) null else XmlToken.ClosingTag(
                    open = SpannedWithSpaces(Unit, span("</"), span("")),
                    name = SpannedWithSpaces(name, span(name), span("")),
                    close = Spanned(span(">"), Unit),
                )
            )
        }
    }
}

class MultipleRootTagsException(tagNames: List<String>) : Exception("Multiple root tags: $tagNames")
class NoRootTagException : Exception("No root tag")

data class XmlDocument<Span>(val children: List<XmlElement<Span>>) {
    fun printRawXML(result: StringBuilder, spanOut: SpanOut<Span>) {
        for (c in children) {
            c.printRawXML(result, spanOut)
        }
    }

    fun rawXML(spanOut: SpanOut<Span>): String {
        val result = StringBuilder()
        printRawXML(result, spanOut)
        return result.toString()
    }

    val root: XmlElement.Tag<Span> get() {
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
        fun <Span : TextSpan> parse(input: CharInput<Span>, spanLike: SpanLike<Span>): XmlDocument<Span> {
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

