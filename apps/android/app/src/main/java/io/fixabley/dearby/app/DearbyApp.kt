package io.fixabley.dearby.app

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import io.fixabley.dearby.R
import io.fixabley.dearby.features.addtocalendar.model.CalendarDraft
import io.fixabley.dearby.features.addtocalendar.model.applicationCalendarDraft
import io.fixabley.dearby.features.addtocalendar.model.phaseCalendarDraft
import io.fixabley.dearby.entities.activitycatalog.model.ActivityDetail
import io.fixabley.dearby.entities.activitycatalog.model.ActivityVenue
import io.fixabley.dearby.entities.activitycatalog.api.ActivityDetailRepository
import io.fixabley.dearby.features.favoriteorganization.model.FavoritesState
import io.fixabley.dearby.pages.discovery.ui.DiscoveryScreen
import io.fixabley.dearby.pages.favorites.ui.FavoritesScreen
import io.fixabley.dearby.pages.noticedetail.ui.NoticeDetailSheet

@Composable
internal fun DearbyApp(catalogProvider: ActivityDetailRepository, favorites: FavoritesState, onOpenSource: (String) -> Unit, onOpenMap: (ActivityVenue) -> Unit, onAddToCalendar: (CalendarDraft) -> Unit) {
    var retry by remember { mutableIntStateOf(0) }
    val result = remember(catalogProvider, retry) { runCatching { catalogProvider.load() } }
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var detail by remember { mutableStateOf<ActivityDetail?>(null) }
    val catalog = result.getOrNull()

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
        Column(Modifier.fillMaxSize().padding(padding)) {
            Text("Dearby", Modifier.padding(horizontal = 22.dp, vertical = 12.dp),
                style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            if (catalog == null) {
                Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
                    Text("공고를 불러오지 못했어요")
                    Button(onClick = { retry++ }) { Text("다시 시도") }
                }
            } else if (selectedTab == 0) {
                DiscoveryScreen(catalog, favorites.ids, favorites::save, showDetail = { detail = catalogProvider.detail(it.id) })
            } else {
                FavoritesScreen(catalog, favorites.ids, favorites::remove, showDetail = { detail = catalogProvider.detail(it.id) })
            }
        }
    }
    detail?.let { notice ->
        NoticeDetailSheet(notice, onDismiss = { detail = null }, onOpenSource = onOpenSource, onOpenMap = onOpenMap, applicationDraft = applicationCalendarDraft(notice), phaseDrafts = notice.schedules.map { phaseCalendarDraft(notice, it) }, onAddToCalendar = onAddToCalendar)
    }
}
