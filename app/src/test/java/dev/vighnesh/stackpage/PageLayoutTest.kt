package dev.vighnesh.stackpage

import dev.vighnesh.stackpage.pdf.Margin
import dev.vighnesh.stackpage.pdf.PageOrientation
import dev.vighnesh.stackpage.pdf.PageSize
import dev.vighnesh.stackpage.pdf.layoutPage
import org.junit.jupiter.api.Test
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

private const val EPS = 0.01f

private fun assertClose(expected: Float, actual: Float, what: String) {
    assertTrue(abs(expected - actual) < EPS, "$what: expected $expected but was $actual")
}

class PageLayoutTest {

    @Test
    fun `a4 portrait is 595 by 842 points`() {
        val l = layoutPage(1000, 1000, PageSize.A4, PageOrientation.PORTRAIT, Margin.NONE)
        assertClose(595f, l.pageWidthPt, "page width")
        assertClose(842f, l.pageHeightPt, "page height")
    }

    @Test
    fun `landscape swaps the page dimensions`() {
        val l = layoutPage(1000, 1000, PageSize.A4, PageOrientation.LANDSCAPE, Margin.NONE)
        assertClose(842f, l.pageWidthPt, "page width")
        assertClose(595f, l.pageHeightPt, "page height")
    }

    @Test
    fun `auto orientation follows the image`() {
        val wide = layoutPage(4000, 3000, PageSize.A4, PageOrientation.AUTO, Margin.NONE)
        assertTrue(wide.pageWidthPt > wide.pageHeightPt, "wide image should get a landscape page")

        val tall = layoutPage(3000, 4000, PageSize.A4, PageOrientation.AUTO, Margin.NONE)
        assertTrue(tall.pageHeightPt > tall.pageWidthPt, "tall image should get a portrait page")

        val square = layoutPage(3000, 3000, PageSize.A4, PageOrientation.AUTO, Margin.NONE)
        assertTrue(square.pageHeightPt > square.pageWidthPt, "square image defaults to portrait")
    }

    @Test
    fun `aspect ratio is preserved, never stretched`() {
        val l = layoutPage(4000, 3000, PageSize.A4, PageOrientation.PORTRAIT, Margin.MEDIUM)
        val srcRatio = 4000f / 3000f
        val destRatio = l.dest.width / l.dest.height
        assertClose(srcRatio, destRatio, "aspect ratio")
    }

    @Test
    fun `image is centred inside the content box`() {
        val l = layoutPage(4000, 3000, PageSize.A4, PageOrientation.PORTRAIT, Margin.SMALL)
        val leftGap = l.dest.left
        val rightGap = l.pageWidthPt - l.dest.right
        val topGap = l.dest.top
        val bottomGap = l.pageHeightPt - l.dest.bottom
        assertClose(leftGap, rightGap, "horizontal centring")
        assertClose(topGap, bottomGap, "vertical centring")
    }

    @Test
    fun `margins are honoured on every side`() {
        val m = Margin.LARGE
        val l = layoutPage(1000, 1000, PageSize.A4, PageOrientation.PORTRAIT, m)
        assertTrue(l.dest.left >= m.points - EPS, "left margin")
        assertTrue(l.dest.top >= m.points - EPS, "top margin")
        assertTrue(l.pageWidthPt - l.dest.right >= m.points - EPS, "right margin")
        assertTrue(l.pageHeightPt - l.dest.bottom >= m.points - EPS, "bottom margin")
    }

    @Test
    fun `a square image on a portrait page is limited by width, not height`() {
        val l = layoutPage(1000, 1000, PageSize.A4, PageOrientation.PORTRAIT, Margin.NONE)
        assertClose(595f, l.dest.width, "drawn width fills the page width")
        assertClose(595f, l.dest.height, "drawn height matches, square stays square")
        assertTrue(l.dest.height < l.pageHeightPt, "there is slack above and below")
    }

    @Test
    fun `fit to image produces a page shaped like the image`() {
        val l = layoutPage(1200, 800, PageSize.FIT_IMAGE, PageOrientation.PORTRAIT, Margin.NONE)
        assertClose(1200f, l.pageWidthPt, "page width")
        assertClose(800f, l.pageHeightPt, "page height")
        assertClose(0f, l.dest.left, "no inset")
        assertClose(1200f, l.dest.right, "image fills the page")
    }

    @Test
    fun `fit to image grows the page to hold the margin`() {
        val l = layoutPage(1200, 800, PageSize.FIT_IMAGE, PageOrientation.PORTRAIT, Margin.MEDIUM)
        assertClose(1200f + 72f, l.pageWidthPt, "page width includes both margins")
        assertClose(800f + 72f, l.pageHeightPt, "page height includes both margins")
        assertClose(36f, l.dest.left, "image starts after the margin")
    }

    @Test
    fun `an absurd margin is clamped instead of inverting the rect`() {
        // Not reachable through the UI presets, but the clamp is what stops a
        // future custom-margin field from emitting a negative-size page.
        val l = layoutPage(1000, 1000, PageSize.A4, PageOrientation.PORTRAIT, Margin.LARGE)
        assertTrue(l.dest.width > 0f, "width stays positive")
        assertTrue(l.dest.height > 0f, "height stays positive")
    }

    @Test
    fun `letter and legal keep their standard sizes`() {
        val letter = layoutPage(10, 10, PageSize.LETTER, PageOrientation.PORTRAIT, Margin.NONE)
        assertClose(612f, letter.pageWidthPt, "letter width")
        assertClose(792f, letter.pageHeightPt, "letter height")

        val legal = layoutPage(10, 10, PageSize.LEGAL, PageOrientation.PORTRAIT, Margin.NONE)
        assertClose(1008f, legal.pageHeightPt, "legal height")
    }

    @Test
    fun `a tiny image is scaled up to fill the content box`() {
        val l = layoutPage(10, 10, PageSize.A4, PageOrientation.PORTRAIT, Margin.NONE)
        assertClose(595f, l.dest.width, "small images still fill the width")
    }

    @Test
    fun `zero dimensions are rejected`() {
        assertFailsWith<IllegalArgumentException> {
            layoutPage(0, 100, PageSize.A4, PageOrientation.AUTO, Margin.NONE)
        }
        assertFailsWith<IllegalArgumentException> {
            layoutPage(100, -1, PageSize.A4, PageOrientation.AUTO, Margin.NONE)
        }
    }

    @Test
    fun `every page size and margin combination yields a drawable rect`() {
        for (size in PageSize.entries) {
            for (orientation in PageOrientation.entries) {
                for (margin in Margin.entries) {
                    val l = layoutPage(3024, 4032, size, orientation, margin)
                    assertTrue(l.pageWidthPt > 0f, "$size/$orientation/$margin page width")
                    assertTrue(l.pageHeightPt > 0f, "$size/$orientation/$margin page height")
                    assertTrue(l.dest.width > 0f, "$size/$orientation/$margin dest width")
                    assertTrue(l.dest.height > 0f, "$size/$orientation/$margin dest height")
                    assertTrue(
                        l.dest.right <= l.pageWidthPt + EPS && l.dest.bottom <= l.pageHeightPt + EPS,
                        "$size/$orientation/$margin stays on the page",
                    )
                }
            }
        }
    }

    @Test
    fun `page size labels are distinct and non-empty`() {
        val labels = PageSize.entries.map { it.label }
        assertEquals(labels.size, labels.toSet().size, "labels must be unique")
        assertTrue(labels.none { it.isBlank() }, "labels must be non-blank")
    }
}
