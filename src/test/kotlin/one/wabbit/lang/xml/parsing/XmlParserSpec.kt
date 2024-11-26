package one.wabbit.lang.xml.parsing

import one.wabbit.lang.xml.XmlElement
import one.wabbit.parsing.CharInput
import one.wabbit.parsing.TextAndPosSpan
import one.wabbit.parsing.TextOnlySpan
import kotlin.test.Test
import kotlin.test.assertTrue

class XmlParserSpec {
    @Test fun test() {
        val xml = """
            <f id='join-flag'/> <c color='grey'>{{player}}</c> joined the server for the first time!
        """.trimIndent()

        val result = parseXmlDocument(XmlScanner(CharInput.withTextAndPosSpans(xml)), TextAndPosSpan.spanLike)
        assertTrue(result.children.size == 4)
        assertTrue(result.children[0] is XmlElement.Tag)
        val f = result.children[0] as XmlElement.Tag
        assertTrue(f.name == "f")
        assertTrue(f.attrs.size == 1)
        assertTrue(result.children[1] is XmlElement.Text)
        assertTrue(result.children[2] is XmlElement.Tag)
        val c = result.children[2] as XmlElement.Tag
        assertTrue(c.name == "c")
        assertTrue(result.children[3] is XmlElement.Text)
    }

    @Test fun testParsingUnopenedTagsCase1() {
        val xml = "</a></root>"
        val result = parseXmlDocument(XmlScanner(CharInput.withTextOnlySpans(xml)), TextOnlySpan.spanLike)
        assertTrue(result.children.size == 2)
        val a = result.children[0]
        assertTrue(a is XmlElement.UnopenedTag)
        assertTrue(a.token.name.value == "a")
        val root = result.children[1]
        assertTrue(root is XmlElement.UnopenedTag)
        assertTrue(root.token.name.value == "root")
    }

    @Test fun testParsingUnopenedTagsCase3() {
        val xml = "<root><a>X</b>Y</a></root>"
        val result = parseXmlDocument(XmlScanner(CharInput.withTextOnlySpans(xml)), TextOnlySpan.spanLike)
        assertTrue(result.children.size == 1)
        val root = result.children[0]
        assertTrue(root is XmlElement.Tag)
        assertTrue(root.name == "root")
        assertTrue(root.children.size == 1)
        val a = root.children[0]
        assertTrue(a is XmlElement.Tag)
        assertTrue(a.name == "a")
        assertTrue(a.children.size == 3)
        assertTrue(a.children[0] is XmlElement.Text)
        assertTrue(a.children[1] is XmlElement.UnopenedTag)
        assertTrue(a.children[2] is XmlElement.Text)
        val b = a.children[1] as XmlElement.UnopenedTag
        assertTrue(b.token.name.value == "b")
    }

    @Test fun testParsingUnclosedTagsCase4() {
        val xml = "<root><a>X<b>Y</a></root>"
        val result = parseXmlDocument(XmlScanner(CharInput.withTextOnlySpans(xml)), TextOnlySpan.spanLike)
        assertTrue(result.children.size == 1)
        val root = result.children[0]
        assertTrue(root is XmlElement.Tag)
        assertTrue(root.name == "root")
        assertTrue(root.children.size == 1)
        val a = root.children[0]
        assertTrue(a is XmlElement.Tag)
        assertTrue(a.name == "a")
        assertTrue(a.children.size == 3)
        assertTrue(a.children[0] is XmlElement.Text)
        assertTrue(a.children[1] is XmlElement.UnclosedTag)
        assertTrue(a.children[2] is XmlElement.Text)
        val b = a.children[1] as XmlElement.UnclosedTag
        assertTrue(b.token.name.value == "b")
    }

    @Test fun testParsingUnclosedTagsCase4_1() {
        val xml = "<root><a><a>X<b>Y</a></a></root>"
        val result = parseXmlDocument(XmlScanner(CharInput.withTextOnlySpans(xml)), TextOnlySpan.spanLike)
        assertTrue(result.children.size == 1)
        val root = result.children[0]
        assertTrue(root is XmlElement.Tag)
        assertTrue(root.name == "root")
        assertTrue(root.children.size == 1)
        val a1 = root.children[0]
        assertTrue(a1 is XmlElement.Tag)
        assertTrue(a1.name == "a")
        assertTrue(a1.children.size == 1)

        val a2 = a1.children[0]
        assertTrue(a2 is XmlElement.Tag)
        assertTrue(a2.name == "a")
        assertTrue(a2.children.size == 3)
        assertTrue(a2.children[0] is XmlElement.Text)
        assertTrue(a2.children[1] is XmlElement.UnclosedTag)
        assertTrue(a2.children[2] is XmlElement.Text)
        val b = a2.children[1] as XmlElement.UnclosedTag
        assertTrue(b.token.name.value == "b")
    }
}
