package com.dearby.nativeapp.widgets.profile.profileFields

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.RemoveCircleOutline
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dearby.nativeapp.shared.ui.*

/** One uploaded evidence file as shown in the list. */
data class EvidenceRow(val id: String, val fileName: String, val isPdf: Boolean, val sizeKb: Int)

/** 저장된 이력의 인증자료 목록과 사진·PDF 올리기(이력당 3개, 5MB). 파일 고르기·업로드는 화면이 맡는다. */
@Composable fun EvidenceFields(items: List<EvidenceRow>, busy: Boolean, error: String?, onPickImage: () -> Unit, onPickPdf: () -> Unit, onDelete: (String) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items.forEach { item ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(if (item.isPdf) Icons.Outlined.Description else Icons.Outlined.Image, null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary)
                Column(Modifier.weight(1f)) {
                    Text(item.fileName, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                    Text("${item.sizeKb}KB", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                }
                IconButton({ onDelete(item.id) }, enabled = !busy) { Icon(Icons.Outlined.RemoveCircleOutline, "${item.fileName} 삭제", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
        if (items.size < 3) Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            DearbyOutlineButton(onPickImage, Modifier.weight(1f), enabled = !busy) { Text("사진") }
            DearbyOutlineButton(onPickPdf, Modifier.weight(1f), enabled = !busy) { Text("PDF") }
        } else Text("인증자료는 3개까지 올릴 수 있어요.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        if (busy) CircularProgressIndicator(Modifier.size(20.dp), color = MaterialTheme.colorScheme.primary, strokeWidth = 2.dp)
        error?.let { DearbyFieldError(it) }
    }
}
