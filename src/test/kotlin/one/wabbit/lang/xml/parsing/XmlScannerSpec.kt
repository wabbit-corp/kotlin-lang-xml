package one.wabbit.lang.xml.parsing

import com.github.difflib.text.DiffRow
import com.github.difflib.text.DiffRowGenerator
import one.wabbit.lang.xml.XmlAttrValue
import one.wabbit.lang.xml.XmlElement
import one.wabbit.lang.xml.XmlToken
import one.wabbit.parsing.CharInput
import one.wabbit.parsing.TextAndPosSpan
import java.io.File
import kotlin.test.Ignore
import kotlin.test.Test


class XmlScannerSpec {
    fun scanAll(s: String): List<XmlToken<TextAndPosSpan>> {
        val input = CharInput.withTextAndPosSpans(s)
        val scanner = XmlScanner(input)
        val tokens = mutableListOf<XmlToken<TextAndPosSpan>>()
        while (scanner.current !is XmlToken.EOF) {
            tokens.add(scanner.current)
            scanner.advance()
        }
        return tokens
    }

    private val String.escaped get(): String {
        val sb = StringBuilder()
        for (c in this) {
            when (c) {
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                '\b' -> sb.append("\\b")
                '\u000C' -> sb.append("\\f")
                '\\' -> sb.append("\\\\")
                '\"' -> sb.append("\\\"")
                '\'' -> sb.append("\\'")
                else -> sb.append(c)
            }
        }

        return "\"" + sb.toString() + "\""
    }

    fun print(element: XmlElement<TextAndPosSpan>, indent: Int = 0) {
        val indentString = " ".repeat(indent)
        when (element) {
            is XmlElement.Tag -> {
                println("${indentString}OPEN    : ${element.openTag.rawXML(TextAndPosSpan.spanLike).escaped}")
                element.children.forEach { print(it, indent + 3) }
                val closeTag = element.closeTag
                if (closeTag != null)
                    println("${indentString}CLOSE   : ${closeTag.rawXML(TextAndPosSpan.spanLike).escaped}")
            }
            is XmlElement.PI -> println("${indentString}PI      : ${element.rawXML(TextAndPosSpan.spanLike).escaped}")
            is XmlElement.Text -> println("${indentString}TEXT    : ${element.rawXML(TextAndPosSpan.spanLike).escaped}")
            is XmlElement.CDATA -> println("${indentString}CDATA   : ${element.rawXML(TextAndPosSpan.spanLike).escaped}")
            is XmlElement.EntityRef -> println("${indentString}ENTITY  : ${element.rawXML(TextAndPosSpan.spanLike).escaped}")
            is XmlElement.Comment -> println("${indentString}COMMENT : ${element.rawXML(TextAndPosSpan.spanLike).escaped}")
            is XmlElement.UnopenedTag -> println("${indentString}UNOPENED: ${element.rawXML(TextAndPosSpan.spanLike).escaped}")
            is XmlElement.UnclosedTag -> println("${indentString}UNCLOSED: ${element.rawXML(TextAndPosSpan.spanLike).escaped}")
        }
    }

    private fun <A> A.shouldEqual(that: A) {
        if (this === that) return
        if (this === null || that === null)
            throw AssertionError("$this != $that")
        if (this == that) return

        if (this is CharSequence && that is CharSequence) {
            val generator = DiffRowGenerator.create()
                .showInlineDiffs(true)
                .inlineDiffByWord(true)
                .reportLinesUnchanged(false)
                .oldTag { f: Boolean? -> "~" }
                .newTag { f: Boolean? -> "**" }
                .build()
            val rows: List<DiffRow> = generator.generateDiffRows(
                this.split("\n"), that.split("\n")
            )

            println("|original|new|")
            println("|--------|---|")
            for (row in rows) {
                if (row.oldLine == row.newLine) continue
                println("|" + row.oldLine + "|" + row.newLine + "|")
            }
        }

        throw AssertionError("$this != $that")
    }

    @Test fun `test scanner`() {
        // FIXME
//        run {
//            val r = scanAll("<!----->")
//            r[0].let { it as XmlToken.Comment; it.span.raw.shouldEqual("<!----->") }
//        }

        run {
            val r = scanAll("<b attr2=\"value2\"/>")
            r[0].let {
                it as XmlToken.OpeningTag
                it.name.value.shouldEqual("b")
                it.closing.shouldEqual(true)
                it.attrs.size.shouldEqual(1)
                it.attrs[0].name.value.shouldEqual("attr2")
                it.attrs[0].value.value.shouldEqual(XmlAttrValue.String("value2"))
            }
        }

        run {
            val r = scanAll("a <b>")
            r[0].let { it as XmlToken.Text; it.text.span.raw.shouldEqual("a ") }
            r[1].let {
                it as XmlToken.OpeningTag
                it.name.value.shouldEqual("b")
                it.attrs.shouldEqual(emptyList())
            }
        }

        run {
            val r = scanAll("a <b/>")
            r[0].let { it as XmlToken.Text; it.text.span.raw.shouldEqual("a ") }
            r[1].let {
                it as XmlToken.OpeningTag
                it.name.value.shouldEqual("b")
                it.closing.shouldEqual(true)
                it.attrs.shouldEqual(emptyList())
            }
        }

        run {
            val r = scanAll("a <b attr='value'/>")
            r[0].let { it as XmlToken.Text; it.text.span.raw.shouldEqual("a ") }
            r[1].let {
                it as XmlToken.OpeningTag
                it.name.value.shouldEqual("b")
                it.closing.shouldEqual(true)
                it.attrs.size.shouldEqual(1)
                it.attrs[0].name.value.shouldEqual("attr")
                it.attrs[0].value.value.shouldEqual(XmlAttrValue.String("value"))
            }
        }



        run {
            val r = scanAll("a <b attr='value' attr2=\"value2\"/>")
            r[0].let { it as XmlToken.Text; it.text.span.raw.shouldEqual("a ") }
            r[1].let {
                it as XmlToken.OpeningTag
                it.name.value.shouldEqual("b")
                it.closing.shouldEqual(true)
                it.attrs.size.shouldEqual(2)
                it.attrs[0].name.value.shouldEqual("attr")
                it.attrs[0].value.value.shouldEqual(XmlAttrValue.String("value"))
                it.attrs[1].name.value.shouldEqual("attr2")
                it.attrs[1].value.value.shouldEqual(XmlAttrValue.String("value2"))
            }
        }

        run {
            val r = scanAll("a <b attr='value' attr2=\"value2\" attr3=9.4/>")
            r.size.shouldEqual(2)
            r[0].let { it as XmlToken.Text; it.text.span.raw.shouldEqual("a ") }
            r[1].let {
                it as XmlToken.OpeningTag
                it.name.value.shouldEqual("b")
                it.closing.shouldEqual(true)
                it.attrs.size.shouldEqual(3)
                it.attrs[0].name.value.shouldEqual("attr")
                it.attrs[0].value.value.shouldEqual(XmlAttrValue.String("value"))
                it.attrs[1].name.value.shouldEqual("attr2")
                it.attrs[1].value.value.shouldEqual(XmlAttrValue.String("value2"))
                it.attrs[2].name.value.shouldEqual("attr3")
                it.attrs[2].value.value.shouldEqual(XmlAttrValue.Real("9.4"))
            }
        }

        run {
            val r = scanAll("a <b attr='value' attr2=\"value2\" attr3=9.4 attr4=true/>")
            r.size.shouldEqual(2)
            r[0].let { it as XmlToken.Text; it.text.span.raw.shouldEqual("a ") }
            r[1].let {
                it as XmlToken.OpeningTag
                it.name.value.shouldEqual("b")
                it.closing.shouldEqual(true)
                it.attrs.size.shouldEqual(4)
                it.attrs[0].name.value.shouldEqual("attr")
                it.attrs[0].value.value.shouldEqual(XmlAttrValue.String("value"))
                it.attrs[1].name.value.shouldEqual("attr2")
                it.attrs[1].value.value.shouldEqual(XmlAttrValue.String("value2"))
                it.attrs[2].name.value.shouldEqual("attr3")
                it.attrs[2].value.value.shouldEqual(XmlAttrValue.Real("9.4"))
                it.attrs[3].name.value.shouldEqual("attr4")
                it.attrs[3].value.value.shouldEqual(XmlAttrValue.Boolean(true))
            }
        }
    }

    @Test fun `comments`() {
        run {
            val r = scanAll("<!-- -->")
            r.size.shouldEqual(1)
            r[0].let { it as XmlToken.Comment; it.span.span.raw.shouldEqual("<!-- -->") }
        }

        run {
            val r = scanAll("<!---->")
            r.size.shouldEqual(1)
            r[0].let { it as XmlToken.Comment; it.span.span.raw.shouldEqual("<!---->") }
        }
    }

    @Test fun `simple boolean attributes`() {
        val r = scanAll("<b a=0 y=true/>")
        r[0].let {
            it as XmlToken.OpeningTag
            it.name.value.shouldEqual("b")
            it.closing.shouldEqual(true)
            it.attrs.size.shouldEqual(2)
            it.attrs[0].name.value.shouldEqual("a")
            it.attrs[0].value.value.shouldEqual(XmlAttrValue.Integer("0"))
            it.attrs[1].name.value.shouldEqual("y")
            it.attrs[1].value.value.shouldEqual(XmlAttrValue.Boolean(true))
        }
    }

    @Test fun `simple real attributes`() {
        val r = scanAll("<b a=0 y=1.0/>")
        r[0].let {
            it as XmlToken.OpeningTag
            it.name.value.shouldEqual("b")
            it.closing.shouldEqual(true)
            it.attrs.size.shouldEqual(2)
            it.attrs[0].name.value.shouldEqual("a")
            it.attrs[0].value.value.shouldEqual(XmlAttrValue.Integer("0"))
            it.attrs[1].name.value.shouldEqual("y")
            it.attrs[1].value.value.shouldEqual(XmlAttrValue.Real("1.0"))
        }
    }

    @Test fun `simple integer attributes`() {
        val r = scanAll("<b a=0 y=1/>")
        r[0].let {
            it as XmlToken.OpeningTag
            it.name.value.shouldEqual("b")
            it.closing.shouldEqual(true)
            it.attrs.size.shouldEqual(2)
            it.attrs[0].name.value.shouldEqual("a")
            it.attrs[0].value.value.shouldEqual(XmlAttrValue.Integer("0"))
            it.attrs[1].name.value.shouldEqual("y")
            it.attrs[1].value.value.shouldEqual(XmlAttrValue.Integer("1"))
        }
    }

//    @Test fun `simpler boolean attribute`() {
//        val r = scanAll("<c dark_green>")
//        r[0].let {
//            it as XmlToken.StartTag
//            it.name.value.shouldEqual("c")
//            it.closing.shouldEqual(false)
//            it.attrs.size.shouldEqual(1)
//            it.attrs[0].name.value.shouldEqual("dark_green")
//            it.attrs[0].value.value.shouldEqual(XmlAttrValue.Boolean(true))
//        }
//    }

    @Test fun `newline between attributes`() {
        val text = """
                <ref id="opportunity-cost-not-simple-or-fundamental"
                    url="https://citeseerx.ist.psu.edu/viewdoc/download?doi=10.1.1.876.4712&rep=rep1&type=pdf">
            """.trimIndent()

        val r = scanAll(text.trim())
        r[0].let {
            it as XmlToken.OpeningTag
            it.name.value.shouldEqual("ref")
            it.closing.shouldEqual(false)
            it.attrs.size.shouldEqual(2)
            it.attrs[0].name.value.shouldEqual("id")
            it.attrs[0].value.value.shouldEqual(XmlAttrValue.String("opportunity-cost-not-simple-or-fundamental"))
            it.attrs[1].name.value.shouldEqual("url")
            it.attrs[1].value.value.shouldEqual(XmlAttrValue.String("https://citeseerx.ist.psu.edu/viewdoc/download?doi=10.1.1.876.4712&rep=rep1&type=pdf"))
        }
    }

//    @Test fun `two simpler boolean attributes`() {
//        val r = scanAll("<c test test2>")
//        r[0].let {
//            it as XmlToken.StartTag
//            it.name.value.shouldEqual("c")
//            it.closing.shouldEqual(false)
//            it.attrs.size.shouldEqual(2)
//            it.attrs[0].name.value.shouldEqual("test")
//            it.attrs[0].value.value.shouldEqual(XmlAttrValue.Boolean(true))
//            it.attrs[1].name.value.shouldEqual("test2")
//            it.attrs[1].value.value.shouldEqual(XmlAttrValue.Boolean(true))
//        }
//    }

    @Test fun `unclosed tag`() {
        val text = "<name>Sets and non-regular types <cite id=\"yt-sets\"></name>"
        val input = CharInput.withTextAndPosSpans(text)
        val scanner = XmlScanner(input)
        val document = parseXmlDocument(scanner, TextAndPosSpan.spanLike)
        println(document.findAllInvalidTags())
        assert(document.findAllInvalidTags().size == 1)
    }

    @Test fun `newlines between attributes`() {
        val text = """
            <wozzle
					   foo="a"                   
					   bar="b"
                       baz="2"
                       beh="L"/>
        """.trim()
        val input = CharInput.withTextAndPosSpans(text)
        val scanner = XmlScanner(input)
        val document = parseXmlDocument(scanner, TextAndPosSpan.spanLike)
        assert(document.children.size == 1)
        assert(document.children[0] is XmlElement.Tag)
    }

    @Test fun `whitespaces around =`() {
        val text = """
            <foo
               id = "4124125-1"
               title="AAAAA"
               score="24124.124"
               subtitle="abc"/>
        """.trim()
        val input = CharInput.withTextAndPosSpans(text)
        val scanner = XmlScanner(input)
        val document = parseXmlDocument(scanner, TextAndPosSpan.spanLike)
        assert(document.children.size == 1)
        assert(document.children[0] is XmlElement.Tag)
    }

    @Test fun `CDATA tag`() {
        val text = """
										<![CDATA[(OKAY if <120 mL/min/1.73m2
Otherwise, multiply by 1.21)]]>
        """.trim()
        val input = CharInput.withTextAndPosSpans(text)
        val scanner = XmlScanner(input)
        val document = parseXmlDocument(scanner, TextAndPosSpan.spanLike)
        assert(document.children.size == 1)
        val element = document.children[0]
        assert(element is XmlElement.CDATA)
    }

    @Test fun `XML start tag`() {
        val text = """
            <?xml version='1.0' encoding='UTF-8'?>
        """.trim()
        val input = CharInput.withTextAndPosSpans(text)
        val scanner = XmlScanner(input)
        val document = parseXmlDocument(scanner, TextAndPosSpan.spanLike)
        assert(document.children.size == 1)
        assert(document.children[0] is XmlElement.PI)
    }

    @Test fun `XML stylesheet`() {
        val text = """
            <?xml-stylesheet type='text/xsl' href='foo.xsl'?>
        """.trimIndent()
        val input = CharInput.withTextAndPosSpans(text)
        val scanner = XmlScanner(input)
        val document = parseXmlDocument(scanner, TextAndPosSpan.spanLike)
        assert(document.children.size == 1)
        assert(document.children[0] is XmlElement.PI)
    }

    @Test fun `nested XML inside of CDATA`() {
        val text = """
    <![CDATA[
      <note>
        <to>User</to>
        <from>Developer</from>
        <message>Welcome to XML!</message>
      </note>
    ]]>
        """.trim()
        val input = CharInput.withTextAndPosSpans(text)
        val scanner = XmlScanner(input)
        val document = parseXmlDocument(scanner, TextAndPosSpan.spanLike)
        assert(document.children.size == 1)
        val element = document.children[0]
        assert(element is XmlElement.CDATA)
    }

    @Ignore @Test fun `XML1_1 tag names #1`() {
        assert('_'.code.isNameStartChar())
        assert("🌱"[0].code.isNameChar())

        val text = """
            <_🌱>This element name starts with an underscore followed by an emoji, which is valid in XML 1.1.</_🌱>
        """.trimIndent()
        val input = CharInput.withTextAndPosSpans(text)
        val scanner = XmlScanner(input)
        val document = parseXmlDocument(scanner, TextAndPosSpan.spanLike)
        println(document)
        assert(document.children.size == 1)
        assert(document.children[0] is XmlElement.Tag)
    }

    @Ignore @Test fun `XML1_1 tag names #2`() {
        val text = """
            <൫൬൭>Elements can have numeric names in scripts other than Latin.</൫൬൭>
        """.trimIndent()
        val input = CharInput.withTextAndPosSpans(text)
        val scanner = XmlScanner(input)
        val document = parseXmlDocument(scanner, TextAndPosSpan.spanLike)
        assert(document.children.size == 1)
        assert(document.children[0] is XmlElement.Tag)
    }

    @Test fun `named entity references`() {
        val text = """
            &amp;&lt;&gt;&quot;&apos;
        """.trim()
        val input = CharInput.withTextAndPosSpans(text)
        val scanner = XmlScanner(input)
        val document = parseXmlDocument(scanner, TextAndPosSpan.spanLike)
        assert(document.children.size == 5)
        var newText = ""
        for (t in document.children) {
            t as XmlElement.EntityRef
            newText += t.token.defaultResolvedEntity
        }
        assert(newText == "&<>\"'")
    }

    @Test fun `test parser`() {
        val input = CharInput.withTextAndPosSpans("a <b> c </b> d <!-- e -->" +
                "f <g/> h <i></i> j </> <k attr=94.0/> <k a=9>" +
                "<k a=true> <k a=b> <k attr='value'/> l" +
                "<!----> <k/>")
        val scanner = XmlScanner(input)
        val document = parseXmlDocument(scanner, TextAndPosSpan.spanLike)

        document.children.forEach { print(it) }
    }

    fun findAllXMLs(path: File): List<File> {
        val result = mutableListOf<File>()
        for (fn in path.listFiles()!!) {
            if (fn.isDirectory) {
                result.addAll(findAllXMLs(fn))
            } else if (fn.extension == "xml" || fn.extension == "xsd" || fn.extension == "xsl" || fn.extension == "xslt") {
                result.add(fn)
            }
        }
        return result
    }

    @Ignore
    @Test fun `test files`() {
        val targetDirs = listOf(
            "D:\\ws\\datatron-new\\datatron\\data-xml-parsing"
        )

        val files = targetDirs.flatMap { findAllXMLs(File(it)) }

        for (fn in files) {
            val text = fn.readText()
            val input = CharInput.withTextAndPosSpans(text)
            val scanner = XmlScanner(input)
            val document = parseXmlDocument(scanner, TextAndPosSpan.spanLike)

            document.rawXML(TextAndPosSpan.spanLike).shouldEqual(text)

            val textFragments = document.findAllTextFragments()
            val badFragments = textFragments.filter { it.token.text.value.count { it == '>' || it == '<' || it == '&' } > 0 }

            val invalidTagCount = document.findAllInvalidTags().size
            val badFragmentCount = badFragments.size

            if (invalidTagCount == 0) continue

            println("File: ${fn.absolutePath}")
            println("  Invalid tags: $invalidTagCount")
            println("  Bad Text fragments: $badFragmentCount")
            for (f in badFragments) {
                println("    ${f.token.text.span.raw}")
            }
        }
    }
}
