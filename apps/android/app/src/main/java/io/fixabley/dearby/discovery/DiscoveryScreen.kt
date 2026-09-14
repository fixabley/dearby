package io.fixabley.dearby.discovery

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.fixabley.dearby.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiscoveryScreen() {
    val context = LocalContext.current
    val favorites = remember { FavoriteOrganizations(context.applicationContext) }
    var retry by remember { mutableIntStateOf(0) }
    val result = remember(retry) { runCatching { ActivityCatalog.load(context) } }
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var detail by remember { mutableStateOf<Notice?>(null) }
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
                DiscoveryFeed(catalog, favorites, showDetail = { detail = it })
            } else {
                FavoritesList(catalog, favorites, showDetail = { detail = it })
            }
        }
    }
    detail?.let { notice ->
        ModalBottomSheet(
            onDismissRequest = { detail = null },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            LazyColumn(Modifier.fillMaxWidth().padding(horizontal = 24.dp).testTag("notice.detail"),
                contentPadding = PaddingValues(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                item { Text(notice.title, style = MaterialTheme.typography.headlineSmall) }
                item { Text(notice.summary) }
                if (catalog != null) item { NoticeIdentity(notice, catalog) }
                item { HorizontalDivider() }
                item { NoticeFact("참여 대상", notice.audience) }
                item { NoticeFact("참여 조건", notice.eligibility) }
                item { NoticeFact("신청 기간", notice.application) }
                items(notice.schedule) { NoticeFact("활동 일정", it) }
                item { NoticeFact("활동 장소", notice.location) }
                items(notice.benefits) { NoticeFact("혜택", it) }
                items(notice.issues) { NoticeFact("확인 필요", it) }
                item {
                    Text("원문을 검토해 만든 샘플입니다. 현재 모집 여부와 변경된 조건은 원문에서 확인해 주세요.",
                        style = MaterialTheme.typography.bodySmall)
                }
                item {
                    Button(onClick = {
                        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(notice.sourceUrl))) }
                            .onFailure { Toast.makeText(context, "공고를 열 브라우저가 없어요", Toast.LENGTH_SHORT).show() }
                    }) { Text("원문 공고 열기") }
                }
            }
        }
    }
}

@Composable
private fun DiscoveryFeed(catalog: ActivityCatalog, favorites: FavoriteOrganizations, showDetail: (Notice) -> Unit) {
    var feedback by remember { mutableStateOf("") }
    val pager = rememberPagerState(pageCount = { catalog.feed.size })
    Column(Modifier.fillMaxSize()) {
        Text("검토한 공고 샘플 · ${catalog.snapshotDate}",
            Modifier.padding(horizontal = 22.dp, vertical = 10.dp),
            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (catalog.feed.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { Text("표시할 공고가 없어요") }
        } else {
            VerticalPager(pager, Modifier.weight(1f).fillMaxWidth().testTag("discovery.pager"), key = { catalog.feed[it].id }) { index ->
                val notice = catalog.feed[index]
                val organization = catalog.organization(notice.organizationId)
                val save = {
                    if (organization != null) {
                        favorites.save(organization.id)
                        feedback = "${organization.name} 저장됨"
                    } else feedback = "저장할 조직을 확인 중이에요"
                }
                NoticeCard(notice, organization, catalog.contextNames(notice), organization?.id in favorites.ids,
                    "${index + 1} / ${catalog.feed.size}", save, { showDetail(notice) })
            }
        }
        Text(feedback.ifEmpty { "위아래로 넘기기 · 더블탭으로 조직 저장" },
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp).testTag("discovery.feedback"),
            style = MaterialTheme.typography.labelSmall, maxLines = 2)
    }
}

@Composable
private fun NoticeCard(
    notice: Notice, organization: Organization?, contextNames: String, saved: Boolean, position: String,
    save: () -> Unit, showDetail: () -> Unit,
) {
    val currentSave by rememberUpdatedState(save)
    BoxWithConstraints(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp)) {
        val compact = maxHeight < 500.dp
        OutlinedCard(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().padding(if (compact) 16.dp else 22.dp),
                verticalArrangement = Arrangement.spacedBy(if (compact) 10.dp else 16.dp)) {
                Column(Modifier.weight(1f).fillMaxWidth()
                    .pointerInput(notice.id) { detectTapGestures(onDoubleTap = { currentSave() }) }
                    .semantics { customActions = listOf(CustomAccessibilityAction("조직 즐겨찾기에 저장") { currentSave(); true }) }
                    .testTag("activity.${notice.id}"),
                    verticalArrangement = Arrangement.spacedBy(if (compact) 10.dp else 16.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("공고 샘플", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
                        Text(position, style = MaterialTheme.typography.labelMedium)
                    }
                    Text(listOf(notice.categorySummary, contextNames).filter { it.isNotEmpty() }.joinToString(" · "),
                        Modifier.testTag("classification.${notice.id}"),
                        style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(notice.title, style = if (compact) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    if (!compact) {
                        HorizontalDivider()
                        NoticeFact("참여 대상", notice.audience, 2)
                        NoticeFact("신청 마감", notice.application, 2)
                        NoticeFact("활동 장소", notice.location, 2)
                    }
                    if (notice.issues.isNotEmpty()) {
                        Text("확인이 필요한 정보가 있어요", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (organization != null) {
                    Button(onClick = save, modifier = Modifier.fillMaxWidth().testTag("save.${notice.id}")) {
                        Text(if (saved) "저장됨 · ${organization.name}" else "${organization.name} 저장", maxLines = 2)
                    }
                } else {
                    Text("저장할 조직 확인 중", style = MaterialTheme.typography.labelMedium)
                }
                TextButton(onClick = showDetail, modifier = Modifier.fillMaxWidth().testTag("details.${notice.id}")) {
                    Text("공고 정보 · 출처 보기")
                }
            }
        }
    }
}

@Composable
internal fun NoticeFact(title: String, value: String, maxLines: Int = Int.MAX_VALUE) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, maxLines = maxLines, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun FavoritesList(catalog: ActivityCatalog, favorites: FavoriteOrganizations, showDetail: (Notice) -> Unit) {
    val organizations = catalog.organizations.filter { it.id in favorites.ids }
    if (organizations.isEmpty()) {
        Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally) {
            Text("저장한 조직이 없어요", style = MaterialTheme.typography.titleLarge)
            Text("발견 탭의 공고를 더블탭하면 조직이 여기에 저장돼요.", Modifier.padding(top = 12.dp))
        }
    } else {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item { Text("이 기기에 저장돼요", style = MaterialTheme.typography.labelMedium) }
            items(organizations, key = { it.id }) { organization ->
                OutlinedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(organization.name, style = MaterialTheme.typography.titleMedium)
                        val ancestors = catalog.organizationPath(organization.id).dropLast(1)
                        if (ancestors.isNotEmpty()) {
                            Text(ancestors.joinToString(" › ") { it.name }, style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        val notices = catalog.feed.filter { it.organizationId == organization.id }
                        Text(if (notices.isEmpty()) "현재 연결된 공고가 없어요" else "연결된 공고 ${notices.size}개",
                            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        notices.forEach { notice ->
                            TextButton(onClick = { showDetail(notice) }, modifier = Modifier.testTag("favorite.notice.${notice.id}")) {
                                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(notice.title)
                                    Text(listOf(notice.categorySummary, catalog.contextNames(notice))
                                        .filter { it.isNotEmpty() }.joinToString(" · "),
                                        style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                        TextButton(onClick = { favorites.remove(organization.id) }, modifier = Modifier.testTag("remove.${organization.id}")) {
                            Text("즐겨찾기에서 삭제", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }
}
