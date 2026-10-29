package com.muyex.randommusicreminder.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.muyex.randommusicreminder.domain.organizer.EventTags

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EventTagsEditor(tags: List<String>, onChange: (List<String>) -> Unit) {
    var input by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    fun add(value: String) {
        runCatching { EventTags.normalize(tags + value) }.onSuccess { onChange(it);input="";error=null }
            .onFailure { error=it.message }
    }
    Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Text("标签分类 ${tags.size}/8",style=MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            tags.forEach { tag -> InputChip(selected=true,onClick={onChange(tags.filterNot { it==tag })},label={Text(tag)},
                trailingIcon={Icon(Icons.Outlined.Close,"移除标签$tag",Modifier.size(16.dp))}) }
        }
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(input,{input=it.take(80);error=null},label={Text("新标签")},singleLine=true,modifier=Modifier.weight(1f),
                keyboardOptions=KeyboardOptions(imeAction=ImeAction.Done),keyboardActions=KeyboardActions(onDone={add(input)}))
            TextButton(onClick={add(input)},enabled=input.isNotBlank() && tags.size<8,modifier=Modifier.heightIn(min=56.dp)) { Text("添加") }
        }
        FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            listOf("工作","生活","出行","待办").filter { suggestion -> tags.none { EventTags.key(it)==EventTags.key(suggestion) } }.forEach { tag ->
                SuggestionChip(onClick={add(tag)},enabled=tags.size<8,label={Text("+ $tag")})
            }
        }
        Text("每个标签最多20字；点击已选标签可移除。",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        error?.let { Text(it,color=MaterialTheme.colorScheme.error) }
    }
}
