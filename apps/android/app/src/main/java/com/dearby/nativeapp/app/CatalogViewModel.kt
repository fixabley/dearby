package com.dearby.nativeapp.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dearby.nativeapp.app.providers.activityStates
import com.dearby.nativeapp.app.providers.catalogDate
import com.dearby.nativeapp.app.providers.savedGroups
import com.dearby.nativeapp.entities.catalog.api.CatalogRepository
import com.dearby.nativeapp.entities.catalog.model.CatalogModel
import com.dearby.nativeapp.entities.catalog.model.CatalogLocalModel
import com.dearby.nativeapp.pages.catalog.CatalogState
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.time.Instant

class CatalogViewModel(private val repository: CatalogRepository, private val now: () -> Instant = Instant::now) : ViewModel() {
    private val mutable = MutableStateFlow(CatalogState())
    val state = mutable.asStateFlow()
    private var catalog: CatalogModel? = null
    private var local = CatalogLocalModel()
    private var expiry: Job? = null
    init { refresh() }
    fun recheckTime() = project()
    private fun project() {
        val instant = now()
        mutable.update { it.copy(activities = catalog?.activityStates(local, instant).orEmpty(), savedGroups = catalog?.savedGroups(local).orEmpty(), generatedAt = catalog?.generatedAt?.let { catalogDate(it) }) }
        expiry?.cancel()
        val boundary = catalog?.activities?.mapNotNull { it.nextChange(instant) }?.minOrNull()
        expiry = boundary?.let { viewModelScope.launch {
            delay(java.time.Duration.between(instant, it).toMillis().coerceAtLeast(1))
            project()
        } }
    }
    fun refresh() {
        if (mutable.value.loading) return
        mutable.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            try {
                if (!mutable.value.storageReady) {
                    try { local = repository.local(); mutable.update { it.copy(storageReady = true, storageError = null) } }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { mutable.update { it.copy(storageError = "기기 저장 기록을 읽지 못했습니다. 다시 시도해 주세요.") } }
                }
                if (catalog == null) {
                    try { catalog = repository.cached() }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { /* A corrupt cache must not prevent network recovery. */ }
                    mutable.update { it.copy(cached = catalog != null) }; project()
                }
                catalog = repository.refresh()
                mutable.update { it.copy(cached = false) }
                project()
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { mutable.update { it.copy(error = "활동을 새로 불러오지 못했습니다. 이전 기록이 있으면 보존됩니다.", cached = catalog != null) }; project() }
            finally { mutable.update { it.copy(loading = false) } }
        }
    }
    fun toggleProgram(id: String) = write { it.copy(programs = if (id in it.programs) it.programs - id else it.programs + id) }
    fun toggleOrganization(id: String) = write { it.copy(organizations = if (id in it.organizations) it.organizations - id else it.organizations + id) }
    fun report(id: String, value: String, success: () -> Unit) { require(value in setOf("applied", "not_applied")); write(success) { it.copy(reports = it.reports + (id to value)) } }
    private fun write(success: () -> Unit = {}, change: (CatalogLocalModel) -> CatalogLocalModel) {
        if (mutable.value.writing || !mutable.value.storageReady) return
        mutable.update { it.copy(writing = true, storageError = null) }
        viewModelScope.launch {
            try { val next = change(local); repository.save(next); local = next; project(); success() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { mutable.update { it.copy(storageError = "저장하지 못했습니다. 이전 상태를 유지합니다. 다시 시도해 주세요.") } }
            finally { mutable.update { it.copy(writing = false) } }
        }
    }
}
