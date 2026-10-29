package com.muyex.randommusicreminder.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class MusicLibraryToolsTest {
    private val tracks = listOf(
        MusicListItem(1, "Forest Rain", "", true, true, 60_000, 20, 100),
        MusicListItem(2, "Forest Piano", "", false, true, 30_000, 50, 300),
        MusicListItem(3, "Broken Rain", "", true, false, 20_000, 30, 200),
        MusicListItem(4, "Forest Rain Live", "", true, true, 60_000, 40, 100),
    )
    private fun ids(query: String = "", filter: MusicFilter = MusicFilter.ALL, sort: MusicSort = MusicSort.NEWEST) =
        filterMusic(tracks, query, filter, sort).map { it.id }

    @Test fun searchIsCaseInsensitiveAndAllWordsMustMatch() { assertEquals(listOf(4L, 1L), ids("  FOREST  rain ")) }
    @Test fun enabledFilterExcludesInvalidTracks() { assertEquals(listOf(4L, 1L), ids(filter = MusicFilter.ENABLED)) }
    @Test fun filtersDisabledAndInvalidSeparately() {
        assertEquals(listOf(2L), ids(filter = MusicFilter.DISABLED))
        assertEquals(listOf(3L), ids(filter = MusicFilter.INVALID))
    }
    @Test fun sortUsesMetadataAndStableIdTieBreak() {
        assertEquals(listOf(2L, 3L, 4L, 1L), ids())
        assertEquals(listOf(3L, 2L, 4L, 1L), ids(sort = MusicSort.SHORTEST))
        assertEquals(listOf(4L, 1L, 2L, 3L), ids(sort = MusicSort.LONGEST))
        assertEquals(listOf(2L, 4L, 3L, 1L), ids(sort = MusicSort.LARGEST))
    }
    @Test fun noMatchesDoesNotChangeOriginalList() {
        assertEquals(emptyList<Long>(), ids("no match"))
        assertEquals(4, tracks.size)
    }
    @Test fun nameSortIsDeterministic() {
        assertEquals(listOf(3L, 2L, 1L, 4L), ids(sort = MusicSort.NAME))
    }
}
