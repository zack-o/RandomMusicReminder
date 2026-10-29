package com.muyex.randommusicreminder.ui

import java.text.Collator
import java.util.Locale

enum class MusicFilter(val label: String) { ALL("全部"), ENABLED("已启用"), DISABLED("已停用"), INVALID("失效") }
enum class MusicSort(val label: String) {
    NEWEST("最近导入"), NAME("名称"), SHORTEST("时长从短到长"), LONGEST("时长从长到短"), LARGEST("文件从大到小"),
}

fun filterMusic(tracks: List<MusicListItem>, query: String, filter: MusicFilter, sort: MusicSort): List<MusicListItem> {
    val words = query.trim().split(Regex("\\s+")).filter(String::isNotBlank)
    val collator = Collator.getInstance(Locale.CHINA)
    val comparator = when (sort) {
        MusicSort.NEWEST -> compareByDescending<MusicListItem> { it.importedAtEpochMillis }
        MusicSort.NAME -> Comparator { a, b -> collator.compare(a.title, b.title) }
        MusicSort.SHORTEST -> compareBy { it.durationMillis }
        MusicSort.LONGEST -> compareByDescending { it.durationMillis }
        MusicSort.LARGEST -> compareByDescending { it.fileSizeBytes }
    }.thenByDescending { it.id }
    return tracks.filter { track ->
        words.all { track.title.contains(it, ignoreCase = true) } && when (filter) {
            MusicFilter.ALL -> true
            MusicFilter.ENABLED -> track.enabled && track.available
            MusicFilter.DISABLED -> !track.enabled
            MusicFilter.INVALID -> !track.available
        }
    }.sortedWith(comparator)
}

data class MusicImportFailure(val fileName: String, val reason: String)
data class MusicImportProgress(
    val total: Int,
    val processed: Int = 0,
    val imported: Int = 0,
    val currentName: String = "",
    val failures: List<MusicImportFailure> = emptyList(),
)
