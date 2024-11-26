package one.wabbit.lang.xml.parsing

import one.wabbit.lang.xml.XmlDocument
import one.wabbit.lang.xml.XmlElement
import one.wabbit.lang.xml.XmlToken
import one.wabbit.parsing.*

private class StackElement<Span>(val openToken: XmlToken.OpeningTag<Span>?) {
    val children = mutableListOf<XmlElement<Span>>()

    fun add(element: XmlElement<Span>, spanLike: SpanLike<Span>) {
        if (children.isNotEmpty()) {
            val last = children.last()
            if (element is XmlElement.Text && last is XmlElement.Text) {
                children.removeAt(children.lastIndex)
                children.add(
                    XmlElement.Text(
                        XmlToken.Text(
                            Spanned(
                                spanLike.combine(last.token.text.span, element.token.text.span),
                                last.token.text.value + element.token.text.value
                            )
                        )
                    )
                )
                return
            }
        }
        children.add(element)
    }

    fun addAll(elements: List<XmlElement<Span>>, spanLike: SpanLike<Span>) {
        elements.forEach { add(it, spanLike) }
    }

    fun print(spanLike: SpanLike<Span>): String {
        val children = children.joinToString("") { it.rawXML(spanLike) }
        return "<${openToken?.name?.value}>$children..."
    }

    override fun toString(): String {
        return "StackElement(openToken=$openToken, children=$children)"
    }
}

internal fun <Span : TextSpan> parseXmlDocument(scanner: XmlScanner<Span>, spanLike: SpanLike<Span>): XmlDocument<Span> {
    val stack = mutableListOf<StackElement<Span>>()
    stack.add(StackElement(null))

    while (true) {
        val token = scanner.current
        when (token) {
            is XmlToken.PI<Span> -> {
                stack.last().add(XmlElement.PI(token), spanLike)
                scanner.advance()
            }
            is XmlToken.Text<Span> -> {
                stack.last().add(XmlElement.Text(token), spanLike)
                scanner.advance()
            }
            is XmlToken.CDATA<Span> -> {
                stack.last().add(XmlElement.CDATA(token), spanLike)
                scanner.advance()
            }
            is XmlToken.EntityRef<Span> -> {
                stack.last().add(XmlElement.EntityRef(token), spanLike)
                scanner.advance()
            }
            is XmlToken.Comment<Span> -> {
                stack.last().add(XmlElement.Comment(token), spanLike)
                scanner.advance()
            }

            is XmlToken.OpeningTag<Span> -> {
                if (token.closing) {
                    stack.last().add(
                        XmlElement.Tag(token, null, emptyList()),
                        spanLike
                    )
                    scanner.advance()
                } else {
                    stack.add(StackElement(token))
                    scanner.advance()
                }
            }

            is XmlToken.ClosingTag<Span> -> {
                val closeToken = token
                val lastTag = stack.last()

                // Case #1: A tag hasn't been properly closed at the top level.
                // <a></a> ... </b>
                if (lastTag.openToken == null) {
                    lastTag.add(XmlElement.UnopenedTag(closeToken), spanLike)
                    scanner.advance()
                    continue
                }

                // Case #2: A tag is properly closed.
                // <a> ... </a>
                if (lastTag.openToken.name.value == token.name.value) {
                    stack.removeAt(stack.lastIndex)
                    stack.last().add(
                        XmlElement.Tag(lastTag.openToken, closeToken, lastTag.children),
                        spanLike
                    )
                    scanner.advance()
                    continue
                }

                // Difficult case #3: the last tag is not closed.
                // <a> ... </b>
                // What if there is a tag above the last one that is not closed and has the same name?
                // <root> ... <a> ... <b> </a>

                // Find the last tag with the same name.
                val index = stack.indexOfLast {
                    it.openToken?.name?.value == closeToken.name.value
                }

                // If there is no such tag, just close the last tag.
                if (index == -1) {
                    lastTag.add(XmlElement.UnopenedTag(closeToken), spanLike)
                    scanner.advance()
                } else {
                    // Close all tags above the last one.
                    while (stack.size - 1 > index) {
                        val tag = stack.last()
                        stack.removeLast()
                        stack.last().add(
                            XmlElement.UnclosedTag(tag.openToken!!),
                            spanLike
                        )
                        stack.last().addAll(tag.children, spanLike)
                    }

                    // Close the last tag.
                    val tag = stack.last()
                    stack.removeLast()
                    stack.last().add(
                        XmlElement.Tag(tag.openToken!!, closeToken, tag.children),
                        spanLike
                    )
                    scanner.advance()
                }
            }

            is XmlToken.EOF<Span> -> {
                while (stack.size > 1) {
                    val lastTag = stack.last()
                    stack.removeAt(stack.lastIndex)
                    stack.last().add(
                        XmlElement.UnclosedTag(lastTag.openToken!!),
                        spanLike
                    )
                    stack.last().addAll(lastTag.children, spanLike)
                }
                return XmlDocument(stack[0].children)
            }
        }
    }
}

internal fun parseXmlDocumentWithTextAndPosSpans(text: String): XmlDocument<TextAndPosSpan> {
    val input = CharInput.withTextAndPosSpans(text)
    val scanner = XmlScanner(input)
    return parseXmlDocument(scanner, TextAndPosSpan.spanLike)
}

internal fun parseXmlDocumentWithTextOnlySpans(text: String): XmlDocument<TextOnlySpan> {
    val input = CharInput.withTextOnlySpans(text)
    val scanner = XmlScanner(input)
    return parseXmlDocument(scanner, TextOnlySpan.spanLike)
}
