package com.muyex.randommusicreminder.domain.organizer

import org.junit.Assert.*
import org.junit.Test

class EventTagsTest {
    @Test fun trimsCollapsesSpacesAndDeduplicatesIgnoringCase() {
        assertEquals(listOf("工作", "Ticket Sale"),EventTags.normalize(listOf(" 工作 ","工作","Ticket   Sale","ticket sale")))
    }
    @Test fun rejectsBlankLongOrTooManyTagsWithoutTruncation() {
        listOf(listOf(" "),listOf("长".repeat(21)),listOf("\u0000"),(1..9).map(Int::toString)).forEach { tags ->
            assertThrows(IllegalArgumentException::class.java) { EventTags.normalize(tags) }
        }
        assertEquals(20,EventTags.normalize(listOf("🎵".repeat(20))).single().codePointCount(0,40))
    }
    @Test fun selectedTagsIntersectAndSearchIncludesDetails() {
        val tags=listOf("工作","抢票")
        assertTrue(EventTags.matches("开售","准备身份证",tags,"开售 身份证",setOf("工作","抢票")))
        assertFalse(EventTags.matches("开售","准备身份证",tags,"",setOf("生活")))
        assertTrue(EventTags.matches("开售","",tags,"抢票",emptySet()))
        assertFalse(EventTags.matches("开售","",tags,"开售 缴费",emptySet()))
    }
}
