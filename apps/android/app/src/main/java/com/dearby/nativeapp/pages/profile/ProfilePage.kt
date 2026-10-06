package com.dearby.nativeapp.pages.profile

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.RemoveCircleOutline
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dearby.nativeapp.shared.ui.*
import com.dearby.nativeapp.widgets.card.cardContent.CardContent
import com.dearby.nativeapp.widgets.card.cardContent.CardState
import com.dearby.nativeapp.widgets.card.cardContent.ContactState
import com.dearby.nativeapp.widgets.profile.profileFields.HistoryPeriodFields
import com.dearby.nativeapp.widgets.profile.profileFields.ProfileContactFields
import com.dearby.nativeapp.widgets.profile.profileFields.ProfileExtraContact

/**
 * 내 프로필 (#149). Signed out it offers card making or sign-in without blocking the tab; signed in it shows the
 * account's profile with an edit action.
 */
@Composable fun ProfilePage(state: ProfileViewState, edit: () -> Unit, compose: () -> Unit, signIn: () -> Unit, retry: () -> Unit, contact: (ContactState) -> Unit) {
    Column(Modifier.fillMaxSize()) {
        ScreenHeader("내 프로필") { if (state.phase == ProfilePhase.LOADED) TextButton(edit) { Text("편집") } }
        when (state.phase) {
            ProfilePhase.SIGNED_OUT -> Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("명함을 만들거나 로그인하면 프로필을 저장하고 고칠 수 있어요.", color = Quiet, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium)
                DearbyButton(compose, Modifier.fillMaxWidth()) { Text("명함 만들기") }
                DearbyOutlineButton(signIn, Modifier.fillMaxWidth()) { Text("로그인") }
            }
            ProfilePhase.LOADING -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Teal) }
            ProfilePhase.FAILED -> Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("프로필을 불러오지 못했어요", Modifier.semantics { heading() }, style = MaterialTheme.typography.titleMedium)
                DearbyOutlineButton(retry, Modifier.fillMaxWidth()) { Text("다시 시도") }
            }
            ProfilePhase.LOADED -> state.profile?.let { profile ->
                // CardContent scrolls inside itself, so it gets the remaining height rather than an outer scroll.
                Column(Modifier.weight(1f).padding(20.dp)) {
                    CardContent(CardState("profile", profile.name.ifBlank { "이름 없음" }, profile.job, "", "", profile.introduction, profile.contacts, profile.histories),
                        Modifier.weight(1f, fill = false), onContact = contact, expanded = true)
                }
            }
        }
    }
}

/** Edits the profile; a failed save keeps the form and shows [error]. */
@Composable fun ProfileEditPage(
    form: ProfileFormState, errors: ProfileErrors?, saving: Boolean, error: String?,
    change: (ProfileFormState) -> Unit, addContact: (String) -> Unit, addHistory: () -> Unit, save: () -> Unit, close: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        ScreenHeader("프로필 편집", close)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Text("기본 정보", Modifier.semantics { heading() }, style = MaterialTheme.typography.titleMedium)
            Labeled("이름 · 필수") { DearbyInlineField("이름, 필수", form.name, { change(form.copy(name = it)) }, editing = true, placeholder = "실명 또는 활동명") }
            errors?.name?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            Labeled("직무") { DearbyInlineField("직무", form.job, { change(form.copy(job = it)) }, editing = true, placeholder = "예: 서비스 기획 · 커뮤니티") }
            Labeled("소개") { DearbyInlineField("소개", form.introduction, { change(form.copy(introduction = it)) }, editing = true, placeholder = "처음 만난 사람에게 건넬 한두 문장", singleLine = false) }
            Text("연락처", Modifier.semantics { heading() }, style = MaterialTheme.typography.titleMedium)
            ProfileContactFields(form.phone, form.email, form.extras, { change(form.copy(phone = it)) }, { change(form.copy(email = it)) },
                { row -> change(form.copy(extras = form.extras.map { if (it.id == row.id) row else it })) },
                { id -> change(form.copy(extras = form.extras.filter { it.id != id })) }, addContact,
                phoneError = errors?.phone, emailError = errors?.email)
            Text("활동 이력", Modifier.semantics { heading() }, style = MaterialTheme.typography.titleMedium)
            form.histories.forEach { history ->
                fun update(next: HistoryFormState) = change(form.copy(histories = form.histories.map { if (it.id == history.id) next else it }))
                OutlinedCard(Modifier.fillMaxWidth(), border = BorderStroke(1.dp, Line), shape = RoundedCornerShape(12.dp)) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Labeled("활동 이름 · 필수", Modifier.weight(1f)) { DearbyInlineField("활동 이름, 필수", history.title, { update(history.copy(title = it)) }, editing = true, placeholder = "예: Dearby 메이커 캠프") }
                            IconButton({ change(form.copy(histories = form.histories.filter { it.id != history.id })) }) { Icon(Icons.Outlined.RemoveCircleOutline, "활동 이력 삭제", tint = Quiet) }
                        }
                        Labeled("역할") { DearbyInlineField("역할", history.role, { update(history.copy(role = it)) }, editing = true, placeholder = "예: 서비스 기획") }
                        HistoryPeriodFields(history.start, history.end, history.ongoing, { update(history.copy(start = it)) }, { update(history.copy(end = it)) },
                            { update(history.copy(ongoing = it)) }, error = errors?.histories?.get(history.id))
                    }
                }
            }
            DearbyOutlineButton(addHistory, Modifier.fillMaxWidth()) { Text("활동 이력 추가") }
        }
        HorizontalDivider()
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            error?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
            DearbyButton(save, Modifier.fillMaxWidth(), enabled = !saving) {
                if (saving) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) else Text("저장")
            }
        }
    }
}

@Composable private fun Labeled(label: String, modifier: Modifier = Modifier, field: @Composable () -> Unit) = Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
    Text(label, color = Quiet, style = MaterialTheme.typography.labelMedium)
    field()
}
