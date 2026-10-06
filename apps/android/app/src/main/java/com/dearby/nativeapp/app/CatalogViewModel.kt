package com.dearby.nativeapp.app

import androidx.lifecycle.ViewModel
import com.dearby.nativeapp.entities.catalog.model.demoActivities
import com.dearby.nativeapp.entities.catalog.model.demoAppliedActivityIds
import com.dearby.nativeapp.pages.catalog.ActivityState
import com.dearby.nativeapp.pages.catalog.CatalogState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class CatalogViewModel : ViewModel() {
    private val mutable = MutableStateFlow(CatalogState(demoActivities.map { activity ->
        ActivityState(activity.id, activity.title, activity.summary, activity.status, activity.participation,
            activity.date, listOf("참가 대상" to activity.audience, "비용" to "무료", "장소" to activity.location,
                "시간대" to activity.schedule.timeZone), activity.url, activity.schedule.startAt,
            applied = activity.id in demoAppliedActivityIds, sessions = when (activity.id) {
                "conference" -> listOf("13:00 – 13:30" to "등록 및 오프닝", "13:30 – 14:30" to "개발 세션", "14:30 – 15:00" to "휴식", "15:00 – 16:00" to "디자인 세션", "16:00 – 17:00" to "네트워킹")
                "camp" -> listOf("10:00 – 11:00" to "오리엔테이션", "11:00 – 13:00" to "팀 아이디어", "13:00 – 14:00" to "점심", "14:00 – 17:00" to "함께 만들기", "17:00 – 18:00" to "결과 공유")
                else -> listOf("14:00 – 14:30" to "인사 나누기", "14:30 – 16:00" to "커뮤니티 이야기", "16:00 – 17:00" to "네트워킹")
            })
    }))
    val state = mutable.asStateFlow()
    fun schedules(id: String) = demoActivities.filter { it.id == id }.map { it.schedule }
    fun filter(value: String) { require(value in listOf("전체", "참가등록형", "선발형")); mutable.update { it.copy(filter = value) } }
    fun apply(id: String, value: Boolean) = update(id) { it.copy(applied = value, confirmed = value && it.confirmed) }
    fun confirm(id: String, value: Boolean) = update(id) { it.copy(confirmed = it.applied && value) }
    private fun update(id: String, change: (ActivityState) -> ActivityState) {
        mutable.update { state -> state.copy(activities = state.activities.map { if (it.id == id) change(it) else it }) }
    }
}
