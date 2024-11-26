package one.wabbit.lang.xml.parsing

import one.wabbit.lang.xml.CloseType
import one.wabbit.lang.xml.XmlAttr
import one.wabbit.lang.xml.XmlAttrValue
import one.wabbit.lang.xml.XmlToken
import one.wabbit.parsing.CharInput
import one.wabbit.parsing.Spanned
import one.wabbit.parsing.TextSpan
import kotlinx.serialization.Serializable

internal fun <Span, Value> Spanned<Span, Value>.withSpaces(spaces: Span): SpannedWithSpaces<Span, Value> =
    SpannedWithSpaces(value, span, spaces)

@Serializable
data class SpannedWithSpaces<out Span, out Value>(
    val value: Value,
    val span: Span,
    val spaces: Span)


internal fun Char.isWhitespace(): Boolean =
    this == ' ' || this == '\t' || this == '\r' || this == '\n'

internal fun Char.isHexDigit(): Boolean =
    this in '0'..'9' || this in 'a'..'f' || this in 'A'..'F'

// NameStartChar ::= ":" | [A-Z] | "_" | [a-z] | [#xC0-#xD6] | [#xD8-#xF6] | [#xF8-#x2FF] | [#x370-#x37D]
//                 | [#x37F-#x1FFF] | [#x200C-#x200D] | [#x2070-#x218F] | [#x2C00-#x2FEF] | [#x3001-#xD7FF]
//                 | [#xF900-#xFDCF] | [#xFDF0-#xFFFD] | [#x10000-#xEFFFF]
internal fun Int.isNameStartChar(): Boolean {
    return when {
        this == ':'.code -> true
        this in 'A'.code..'Z'.code -> true
        this == '_'.code -> true
        this in 'a'.code..'z'.code -> true
        this in 0xC0..0xD6 -> true
        this in 0xD8..0xF6 -> true
        this in 0xF8..0x2FF -> true
        this in 0x370..0x37D -> true
        this in 0x37F..0x1FFF -> true
        this in 0x200C..0x200D -> true
        this in 0x2070..0x218F -> true
        this in 0x2C00..0x2FEF -> true
        this in 0x3001..0xD7FF -> true
        this in 0xF900..0xFDCF -> true
        this in 0xFDF0..0xFFFD -> true
        this in 0x10000..0xEFFFF -> true
        else -> false
    }
}

// NameChar ::= NameStartChar | "-" | "." | [0-9] | #xB7 | [#x0300-#x036F] | [#x203F-#x2040]
internal fun Int.isNameChar(): Boolean {
    return when {
        this.isNameStartChar() -> true
        this == '-'.code -> true
        this == '.'.code -> true
        this in '0'.code..'9'.code -> true
        this == 0xB7 -> true
        this in 0x0300..0x036F -> true
        this in 0x203F..0x2040 -> true
        else -> false
    }
}


internal fun <Span> CharInput<Span>.fail(message: String): Nothing {
    throw Exception("Error at ${this}: $message")
}

internal fun <Span> CharInput<Span>.expect(c: Char) {
    val ch = this.current
    if (ch != c) {
        this.fail("Expected '$c', got '$ch'")
    }
    this.advance()
}

internal fun <Span : TextSpan> CharInput<Span>.expect(s: String) {
    // Check the whole string at once, so that we don't advance the input
    // if the string doesn't match.
    val start = this.mark()
    for (c in s) {
        val ch = this.current
        if (ch != c) {
            val text = this.capture().raw
            this.reset(start)
            this.fail("Expected '$s', got '$text'")
        }
        this.advance()
    }
}

internal fun <Span : TextSpan> CharInput<Span>.captureRaw(start: CharInput.Mark): Spanned<Span, String> {
    val span = capture(start)
    return Spanned(span, span.raw)
}

internal fun <Span> CharInput<Span>.readIntegerAsString(): String {
    // This is a violation of the contract of readInteger:
    check(current.isDigit()) {
        "Expected a digit, got '$current'"
    }

    val buffer = StringBuilder()
    while (true) {
        val char = current
        when {
            char == CharInput.EOB ->
                return buffer.toString()
            char.isDigit() -> {
                buffer.append(char)
                advance()
            }
            else -> return buffer.toString()
        }
    }
}

internal fun <Span> CharInput<Span>.readHexInteger(): String {
    // This is a violation of the contract of readHexInteger:
    check(current.isHexDigit()) {
        "Expected a hex digit, got '$current'"
    }

    val buffer = StringBuilder()
    while (true) {
        when {
            current == CharInput.EOB ->
                return buffer.toString()
            current.isHexDigit() -> {
                buffer.append(current)
                advance()
            }
            else -> return buffer.toString()
        }
    }
}

internal fun <Span : TextSpan> CharInput<Span>.readIntegerOrReal(): Spanned<Span, XmlAttrValue>? {
    val start = mark()

    while (true) {
        val char = current
        when {
            char.isDigit() -> {
                advance()
            }
            char == '.' -> {
                advance()
                return readReal(start)?.map { XmlAttrValue.Real(it) }
            }

            else -> {
                // INCLUDES char == CharInput.EOB
                return this.captureRaw(start)
                    .map { XmlAttrValue.Integer(it) }
            }
        }
    }
}

internal fun <Span : TextSpan> CharInput<Span>.readReal(startMark: CharInput.Mark): Spanned<Span, String>? {
    // Supports 1.0, 1.0e10, 1.0e-10, 1.0e+10

    while (true) {
        val char = current
        // println("readReal: ${input}")
        when {
            char == CharInput.EOB || char == '>' || char.isWhitespace() || char == '/' -> {
                val span = capture(startMark)
                return Spanned(span, span.raw)
            }

            char.isDigit() -> {
                advance()
            }

            char == 'e' || char == 'E' -> {
                advance()
                return readExponent(startMark)
            }

            else -> return null
        }
    }
}

internal fun <Span : TextSpan> CharInput<Span>.readExponent(startMark: CharInput.Mark): Spanned<Span, String>? {
    // Supports 1.0e10, 1.0e-10, 1.0e+10
    val char = current

    if (char == '+' || char == '-') {
        advance()
    }

    if (!current.isDigit())
        return null

    while (true) {
        when {
            current.isDigit() -> {
                advance()
            }

            else -> {
                // INCLUDES input.current == CharInput.EOB
                val span = capture(startMark)
                return Spanned(span, span.raw)
            }
        }
    }
}

internal fun <Span> CharInput<Span>.readSpaces(): Span {
    val start = mark()
    while (true) {
        val char = current
        when {
            char == CharInput.EOB -> return capture(start)
            char.isWhitespace() -> advance()
            else -> return capture(start)
        }
    }
}

internal fun <Span> CharInput<Span>.readLiteral(c: Char): Spanned<Span, Unit>? {
    val start = mark()
    val ch = current
    if (ch != c) {
        return null
    }
    advance()
    return Spanned(capture(start), Unit)
}

internal fun <Span> CharInput<Span>.readLiteral(s: String): Spanned<Span, Unit>? {
    val start = mark()
    for (c in s) {
        val ch = current
        if (ch != c) {
            return null
        }
        advance()
    }
    return Spanned(capture(start), Unit)
}

internal fun <Span> CharInput<Span>.readIdentifier(): Spanned<Span, String>? {
    val start = mark()
    val buffer = StringBuilder()

    // FIXME: incorrect unicode handling
    if (!current.code.isNameStartChar()) return null
    buffer.append(current)
    advance()

    while (true) {
        when {
            current == CharInput.EOB ->
                return Spanned(capture(start), buffer.toString())

            current.code.isNameChar() -> {
                buffer.append(current)
                advance()
            }

            else -> return Spanned(capture(start), buffer.toString())
        }
    }
}

private fun <Span : TextSpan> CharInput<Span>.nextToken(): XmlToken<Span> {
    val tokenStart = this.mark()

    when (current) {
        CharInput.EOB -> return XmlToken.EOF(this.pos())
        '<' -> {
            advance()
            val char = current
            return when {
                char == '/' -> {
                    advance()
                    return scanEndTag(tokenStart)
                }
                char == '!' -> {
                    advance()
                    if (current == '-') {
                        return scanComment(tokenStart)
                    } else if (current == '[') {
                        return scanCDATA(tokenStart)
                    } else {
                        // FIXME: <!DOCTYPE ...>
                        return scanText(tokenStart)
                    }
                }
                char == '?' -> {
                    advance()
                    if (current.code.isNameStartChar()) {
                        return scanTag(tokenStart, special = true)
                    } else {
                        return scanText(tokenStart)
                    }
                }
                char.code.isNameStartChar() -> return scanTag(tokenStart, special = false)
                else -> return scanText(tokenStart)
            }
        }
        '&' -> {
            return scanEntityRef(tokenStart)
        }
        else -> return scanText(tokenStart)
    }
}

// Assumes that the current character is '&'.
private fun <Span : TextSpan> CharInput<Span>.scanEntityRef(tokenStart: CharInput.Mark): XmlToken<Span> {
    assert(current == '&')
    advance()

    if (current == '#') {
        advance()
        if (current == 'x') {
            advance()
            if (!current.isHexDigit()) return scanText(tokenStart)
            val id = readHexInteger()
            if (current != ';') return scanText(tokenStart)
            advance()
            return XmlToken.EntityRef(Spanned(capture(tokenStart), "#x$id"))
        } else if (current.isDigit()) {
            val id = readIntegerAsString()
            if (current != ';') return scanText(tokenStart)
            advance()
            return XmlToken.EntityRef(Spanned(capture(tokenStart), "#$id"))
        } else {
            return scanText(tokenStart)
        }
    } else {
        val name = readIdentifier() ?: return scanText(tokenStart)
        if (current != ';') return scanText(tokenStart)
        advance()
        return XmlToken.EntityRef(Spanned(capture(tokenStart), name.value))
    }
}

// Assumes that there is already a char in the span.
private fun <Span : TextSpan> CharInput<Span>.scanText(tokenStart: CharInput.Mark): XmlToken.Text<Span> {
    while (current != '<' && current != '&' && current != CharInput.EOB) {
        advance()
    }
    val span = capture(tokenStart)
    return XmlToken.Text(Spanned(span, span.raw))
}

// Assumes that the span starts with '<![ and the current character is '['.
private fun <Span : TextSpan> CharInput<Span>.scanCDATA(tokenStart: CharInput.Mark): XmlToken<Span> {
    assert(current == '[')

    advance()
    if (current != 'C') return scanText(tokenStart)
    advance()
    if (current != 'D') return scanText(tokenStart)
    advance()
    if (current != 'A') return scanText(tokenStart)
    advance()
    if (current != 'T') return scanText(tokenStart)
    advance()
    if (current != 'A') return scanText(tokenStart)
    advance()
    if (current != '[') return scanText(tokenStart)
    advance()

    val sb = StringBuilder()

    while (true) {
        when (current) {
            CharInput.EOB -> return scanText(tokenStart)
            ']' -> {
                advance()
                if (current == ']') {
                    advance()
                    if (current == '>') {
                        advance()
                        val span = capture(tokenStart)
                        return XmlToken.CDATA(Spanned(span, sb.toString()))
                    } else {
                        sb.append(']')
                        advance()
                    }
                } else {
                    sb.append(']')
                    advance()
                }
            }

            else -> {
                sb.append(current)
                advance()
            }
        }
    }

    error("Should not reach here")
}

// Assumes that the span starts with '<!-' and the current character is '-'.
private fun <Span : TextSpan> CharInput<Span>.scanComment(tokenStart: CharInput.Mark): XmlToken<Span> {
    assert(current == '-')

    advance()
    if (current != '-') return scanText(tokenStart)
    advance()

    val sb = StringBuilder()

    while (true) {
        when (current) {
            CharInput.EOB -> return scanText(tokenStart)
            '-' -> {
                advance()
                if (current == '-') {
                    advance()
                    if (current == '>') {
                        advance()
                        val span = capture(tokenStart)
                        return XmlToken.Comment(Spanned(span, sb.toString()))
                    }
                }
            }

            else -> {
                sb.append(current)
                advance()
            }
        }
    }
}

// Assumes that the span starts with '</' and the current character is '/'.
private fun <Span : TextSpan> CharInput<Span>.scanEndTag(tokenStart: CharInput.Mark): XmlToken<Span> {
    val open = capture(tokenStart)
    assert(open.raw == "</")
    val openTail = readSpaces()

    val name = readIdentifier() ?: return scanText(tokenStart)
    val nameTail = readSpaces()

    val close = readLiteral('>') ?: return scanText(tokenStart)
    assert(close.span.raw == ">")

    return XmlToken.ClosingTag(
        SpannedWithSpaces(Unit, open, openTail),
        name.withSpaces(nameTail),
        close
    )
}

// Assumes that the span starts with '<X' or <?X and the current character is 'X' (any start symbol).
private fun <Span : TextSpan> CharInput<Span>.scanTag(tokenStart: CharInput.Mark, special: Boolean): XmlToken<Span> {
    val open = capture(tokenStart)
    if (special) assert(open.raw == "<?")
    else assert(open.raw == "<")

    assert(current.code.isNameStartChar())

    val name = readIdentifier() ?: return scanText(tokenStart)
    val nameTail = readSpaces()

    var attrs: List<XmlAttr<Span>> = emptyList()

    while (true) {
        when {
            current == CharInput.EOB ->
                return scanText(tokenStart)

            current == '?' && special -> {
                val close = readLiteral("?>") ?: return scanText(tokenStart)
                return XmlToken.PI(
                    Spanned(open, Unit),
                    SpannedWithSpaces(name.value, name.span, nameTail),
                    attrs,
                    close
                )
            }

            current == '>' -> {
                val close = readLiteral('>') ?: return scanText(tokenStart)
                return XmlToken.OpeningTag(
                    Spanned(open, Unit),
                    SpannedWithSpaces(name.value, name.span, nameTail),
                    attrs, close.replace(CloseType.Greater)
                )
            }

            current == '/' -> {
                val close = readLiteral("/>") ?: return scanText(tokenStart)
                return XmlToken.OpeningTag(
                    Spanned(open, Unit),
                    SpannedWithSpaces(name.value, name.span, nameTail),
                    attrs, close.replace(CloseType.SlashGreater)
                )
            }

            current.code.isNameStartChar() -> {
                attrs = readAttributes() ?: return scanText(tokenStart)
                assert(!current.isWhitespace() && !current.code.isNameStartChar())
            }

            else -> return scanText(tokenStart)
        }
    }
}

private fun <Span : TextSpan> CharInput<Span>.readAttributes(): List<XmlAttr<Span>>? {
    assert(current.code.isNameStartChar())
    val attrs = mutableListOf<XmlAttr<Span>>()
    while (true) {
        if (current.code.isNameStartChar()) {
            val attr = readAttribute() ?: return null
            attrs.add(attr)
        } else break
    }
    return attrs
}

private fun <Span : TextSpan> CharInput<Span>.readAttribute(): XmlAttr<Span>? {
    val name = readIdentifier() ?: return null
    val nameTail = readSpaces()

    val eq = readLiteral('=') ?: return null

    assert(eq.span.raw == "=")
    val eqTail = readSpaces()

    val value = readAttrValue() ?: return null
    val valueTail = readSpaces()

    return XmlAttr(
        name.withSpaces(nameTail),
        eq.withSpaces(eqTail),
        value.withSpaces(valueTail)
    )
}

private fun <Span : TextSpan> CharInput<Span>.readAttrValue(): Spanned<Span, XmlAttrValue>? {
    val start = mark()
    val char = current
    when {
        char == '"'  -> return readQuotedAttrValue(start, '"')?.map { XmlAttrValue.String(it) }
        char == '\'' -> return readQuotedAttrValue(start, '\'')?.map { XmlAttrValue.String(it) }

        char == '>' -> return null
        char == '/' -> return null
        char == CharInput.EOB -> return null

        char.code.isNameStartChar() -> {
            val name = readIdentifier() ?: return null
            if (name.value == "true") return name.replace(XmlAttrValue.Boolean(true))
            else if (name.value == "false") return name.replace(XmlAttrValue.Boolean(false))
            else return name.map { XmlAttrValue.String(it) }
        }

        char.isDigit() -> return readIntegerOrReal()
        else -> return null
    }
}

private fun <Span> CharInput<Span>.readQuotedAttrValue(start: CharInput.Mark, quote: Char): Spanned<Span, String>? {
    assert(current == quote)
    advance()

    val buffer = StringBuilder()
    while (true) {
        val char = current
        when {
            char == CharInput.EOB -> return null
            char == quote -> {
                advance()
                return Spanned(capture(start), buffer.toString())
            }

            char == '\\' -> {
                advance()
                val escaped = current
                when (escaped) {
                    CharInput.EOB -> return null
                    'n' -> buffer.append('\n')
                    'r' -> buffer.append('\r')
                    't' -> buffer.append('\t')
                    else -> buffer.append(escaped)
                }
                advance()
            }

            else -> {
                buffer.append(char)
                advance()
            }
        }
    }
}

class XmlScanner<Span : TextSpan>(val input: CharInput<Span>) {
    var current: XmlToken<Span> = input.nextToken()
    fun advance() {
        current = input.nextToken()
    }
}
