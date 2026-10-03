package com.dearby.nativeapp.app

import androidx.lifecycle.ViewModel
import com.dearby.nativeapp.entities.catalog.model.demoActivities
import com.dearby.nativeapp.pages.catalog.ActivityState
import com.dearby.nativeapp.pages.catalog.CatalogState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class CatalogViewModel : ViewModel() {
    private val mutable = MutableStateFlow(CatalogState(demoActivities.map { activity ->
        ActivityState(activity.id, activity.title, activity.summary, activity.status, activity.participation,
            activity.date, listOf("참가 대상" to activity.audience, "비용" to "무료", "장소" to activity.location,
                "시간대" to activity.schedule.timeZone), activity.url)
    }))
    val state = mutable.asStateFlow()
    fun schedules(id: String) = demoActivities.filter { it.id == id }.map { it.schedule }
    fun filter(value: String) { require(value in listOf("전체", "참가등록형", "선발형")); mutable.update { it.copy(filter = value) } }
    fun report(id: String, value: Boolean) { mutable.update { state -> state.copy(activities = state.activities.map { if (it.id == id) it.copy(report = value) else it }) } }
}
