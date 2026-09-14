package io.fixabley.dearby.app

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import io.fixabley.dearby.shared.ui.theme.Spacing
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import io.fixabley.dearby.shared.ui.StatusPanel
import io.fixabley.dearby.shared.ui.StatusKind
import io.fixabley.dearby.shared.ui.buttons.PrimaryButton
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import io.fixabley.dearby.R
import io.fixabley.dearby.features.addtocalendar.model.CalendarDraft
import io.fixabley.dearby.pages.noticedetail.model.NoticeDetailViewModel
import io.fixabley.dearby.entities.notice.model.NoticeVenue
import io.fixabley.dearby.pages.discovery.ui.DiscoveryScreen
import io.fixabley.dearby.pages.favorites.ui.FavoritesScreen
import io.fixabley.dearby.pages.noticedetail.ui.NoticeDetailSheet

@Composable
internal fun DearbyApp(catalogProvider: NoticeSession, onOpenSource: (String) -> Unit, onOpenMap: (NoticeVenue) -> Unit, onAddToCalendar: (CalendarDraft) -> Unit, busyProvider: io.fixabley.dearby.features.calendarbusy.api.BusyProvider? = null) {
    var retry by remember { mutableIntStateOf(0) }
    var loading by remember(catalogProvider) { mutableStateOf(true) }
    var failed by remember(catalogProvider) { mutableStateOf(false) }
    LaunchedEffect(catalogProvider, retry) {
        loading = true
        try { catalogProvider.load(); failed = false }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { failed = true }
        finally { if (currentCoroutineContext().isActive) loading = false }
    }
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var detail by remember { mutableStateOf<NoticeDetailViewModel?>(null) }
    val catalog = catalogProvider.snapshot

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(painterResource(R.drawable.ic_discover), contentDescription = null) },
                    label = { Text("발견") },
                    modifier = Modifier.testTag("tab.discovery"),
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(painterResource(R.drawable.ic_favorite), contentDescription = null) },
                    label = { Text("즐겨찾기") },
                    modifier = Modifier.testTag("tab.favorites"),
                )
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).testTag(if (catalog != null) "catalog.ready" else "catalog.loading")) {
            Text("Dearby", Modifier.padding(horizontal = Spacing.extraLarge, vertical = Spacing.medium),
                style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            if (failed && catalog != null) {
                StatusPanel("공고를 불러오지 못했어요", Modifier.padding(horizontal = Spacing.large),
                    kind = StatusKind.Error, action = { PrimaryButton(onClick = { retry++ }) { Text("다시 시도") } })
            }
            if (catalog == null && loading) {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Spacing.extraLarge)) {
                    StatusPanel("공고를 불러오는 중이에요", kind = StatusKind.Loading)
                }
            } else if (catalog == null) {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Spacing.extraLarge),
                    verticalArrangement = Arrangement.Center) {
                    StatusPanel("공고를 불러오지 못했어요", kind = StatusKind.Error,
                        action = { PrimaryButton(onClick = { retry++ }) { Text("다시 시도") } })
                }
            } else if (selectedTab == 0) {
                DiscoveryScreen(catalog.snapshotDate, catalogProvider.cardStates(), catalogProvider::save, showDetail = { detail = catalogProvider.detail(it) })
            } else {
                FavoritesScreen(catalogProvider.favoriteStates(), catalogProvider::remove, showDetail = { detail = catalogProvider.detail(it) })
            }
        }
    }
    detail?.state?.let { notice ->
        if (busyProvider != null) NoticeDetailRoute(notice, busyProvider, { detail = null }, onOpenSource, onOpenMap, onAddToCalendar)
        else NoticeDetailSheet(notice, onDismiss = { detail = null }, onOpenSource = onOpenSource, onOpenMap = onOpenMap, onAddToCalendar = onAddToCalendar)
    }
}
