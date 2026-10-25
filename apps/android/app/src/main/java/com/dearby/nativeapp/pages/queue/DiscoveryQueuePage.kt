package com.dearby.nativeapp.pages.queue

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.dearby.nativeapp.shared.ui.InitialAvatar
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

private const val SCENE_MILLIS = 4_000L
private val Accent = Color(0xFF7FE3E3)
private val Heart = Color(0xFFFF4D6D)
private val Glass = Color.Black.copy(alpha = 0.35f)

/**
 * 오늘의 맞춤 대기열 (contract "맞춤 대기열(스토리형 쇼츠)"): full-screen [VerticalPager], one activity per page and an end page after the last.
 * Each activity plays its scenes (as many as the server sends), [SCENE_MILLIS] apiece (one progress segment each, tap right/left for the next/previous
 * scene); after the last it stays there, every segment full, until a swipe or `다음`.
 * [reduceMotion] (the system's 애니메이션 삭제) keeps the cover, text and heart still while the scenes still change; [autoAdvance] false (a screen
 * reader) leaves the scenes to taps and the custom actions. [skipEarly] fires when an activity is left forward on its first scene, [complete]
 * when its last scene ends. The `되돌리기` snackbar answers [undo] or [commit].
 * With [QueueViewState.ad] the page after the last activity is the native ad that [ad] draws (2026-10-25), passed over by a swipe like a card.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable fun DiscoveryQueuePage(
    state: QueueViewState, reduceMotion: Boolean, autoAdvance: Boolean, close: () -> Unit, retry: () -> Unit, page: (Int) -> Unit,
    like: (String, Boolean?) -> Unit, detail: (String) -> Unit, react: (String, QueueReaction) -> Unit,
    complete: (String) -> Unit, skipEarly: (String) -> Unit, refill: () -> Unit, undo: () -> Unit, commit: () -> Unit,
    /** Favorited activity ids (the ♥ is the 즐겨찾기 heart) and those in 내 활동 (the `+` beside it, [plus]). */
    liked: Set<String> = emptySet(), inMine: Set<String> = emptySet(), plus: (String) -> Unit = {},
    /** A card stayed centred for a second (usage statistics `queue_card_view`). */
    cardViewed: (String) -> Unit = {},
    ad: (@Composable (Modifier) -> Unit)? = null,
) {
    val snackbar = remember { SnackbarHostState() }
    var sheetFor by remember { mutableStateOf<QueueCardState?>(null) }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        when {
            state.cards.isEmpty() && state.loading -> CircularProgressIndicator(Modifier.align(Alignment.Center), color = Color.White)
            state.cards.isEmpty() && state.failed -> Column(Modifier.align(Alignment.Center).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("대기열을 불러오지 못했어요", color = Color.White, style = MaterialTheme.typography.titleMedium)
                Button(retry) { Text("다시 시도") }
                TextButton(close) { Text("닫기", color = Color.White) }
            }
            else -> {
                val cards = state.cards
                val pager = rememberPagerState(initialPage = state.index.coerceIn(0, state.end)) { state.end + 1 }
                val scope = rememberCoroutineScope()
                // The scene each activity is on, so leaving one on scene ① can be told apart (SKIP_EARLY) and coming back resumes it.
                val scenes = remember { mutableStateMapOf<String, Int>() }
                val latest by rememberUpdatedState(cards)
                LaunchedEffect(pager) {
                    var previous = pager.currentPage
                    snapshotFlow { pager.currentPage }.distinctUntilChanged().collect { now ->
                        if (now > previous) latest.getOrNull(previous)?.takeIf { (scenes[it.id] ?: 0) == 0 }?.let { skipEarly(it.id) }
                        previous = now
                        page(now)
                    }
                }
                // Undo and 더 받기 move the queue ([QueueViewState.moves]); the user's own paging only reports back through [page].
                LaunchedEffect(pager.settledPage) { delay(1_000); cards.getOrNull(pager.settledPage)?.let { cardViewed(it.id) } }
                LaunchedEffect(state.moves) { if (pager.currentPage != state.index) pager.scrollToPage(state.index.coerceIn(0, state.end)) }
                VerticalPager(pager, Modifier.fillMaxSize(), key = { if (it < cards.size) cards[it].id else if (it < state.end) "ad" else "end" }) { index ->
                    val card = cards.getOrNull(index)
                    if (card == null && index < state.end) AdPage(ad, close)
                    else if (card == null) EndPage(state, refill, close)
                    else {
                        val next = { scope.launch { pager.animateScrollToPage(index + 1) }; Unit }
                        QueueCard(card, index, cards.size, active = pager.settledPage == index && sheetFor == null, reduceMotion, autoAdvance,
                            scene = scenes[card.id] ?: 0, setScene = { scenes[card.id] = it }, next = next, complete = { complete(card.id) },
                            like = { like(card.id, it) }, detail = { detail(card.id) }, more = { sheetFor = card },
                            react = { react(card.id, it) }, close = close, liked = card.id in liked, added = card.id in inMine, plus = { plus(card.id) })
                    }
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp))
    }
    LaunchedEffect(state.undo) {
        state.undo ?: return@LaunchedEffect
        if (snackbar.showSnackbar(state.undo.message, "되돌리기", duration = SnackbarDuration.Short) == SnackbarResult.ActionPerformed) undo() else commit()
    }
    sheetFor?.let { card ->
        ModalBottomSheet({ sheetFor = null }) {
            QueueReaction.entries.filter { it != QueueReaction.LESS_FIELD || card.field != null }.forEach { reaction ->
                ListItem(headlineContent = { Text(if (reaction == QueueReaction.LESS_FIELD) "${reaction.label} · ${card.field}" else reaction.label) },
                    leadingContent = { Icon(reaction.icon, null) },
                    modifier = Modifier.clickable { sheetFor = null; react(card.id, reaction) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent))
            }
            Spacer(Modifier.navigationBarsPadding().height(12.dp))
        }
    }
}

private val QueueReaction.icon: ImageVector get() = when (this) {
    QueueReaction.DISMISS -> Icons.Rounded.ThumbDown
    QueueReaction.LESS_FIELD -> Icons.Rounded.RemoveCircleOutline
    QueueReaction.HIDE_PROGRAM -> Icons.Rounded.VisibilityOff
    QueueReaction.INELIGIBLE -> Icons.Rounded.Block
}

@Composable private fun QueueCard(
    card: QueueCardState, index: Int, count: Int, active: Boolean, reduceMotion: Boolean, autoAdvance: Boolean,
    scene: Int, setScene: (Int) -> Unit, next: () -> Unit, complete: () -> Unit,
    like: (Boolean?) -> Unit, detail: () -> Unit, more: () -> Unit, react: (QueueReaction) -> Unit, close: () -> Unit,
    liked: Boolean, added: Boolean, plus: () -> Unit,
) {
    // After the last scene the card stays put, every segment full (2026-10-24 user decision "멈춰줘"); only a swipe or `다음` moves on.
    val last = card.scenes.lastIndex
    val forward = { if (scene < last) setScene(scene + 1) else complete() }
    val back = { if (scene > 0) setScene(scene - 1) }
    var progress by remember(card.id, scene) { mutableFloatStateOf(0f) }
    // The scene clock runs on frame time, not on an animation, so the system animation scale cannot shorten it.
    LaunchedEffect(active, scene, autoAdvance) {
        if (!active || !autoAdvance || progress >= 1f) return@LaunchedEffect
        if (reduceMotion) { delay(SCENE_MILLIS); progress = 1f }
        else {
            val start = withFrameMillis { it }
            while (progress < 1f) progress = ((withFrameMillis { it } - start) / SCENE_MILLIS.toFloat()).coerceAtMost(1f)
        }
        forward()
    }
    var burst by remember { mutableIntStateOf(0) }
    val doubleLike = { like(true); burst++ }
    Box(Modifier.fillMaxSize().clipToBounds()) {
        Cover(card, reduceMotion)
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color.Black.copy(0.45f), 0.3f to Color.Black.copy(0.05f), 0.55f to Color.Black.copy(0.1f), 1f to Color.Black.copy(0.85f))))
        // Tap right for the next scene, left for the previous; a double tap likes (contract "두 번 탭은 관심 있어요").
        Box(Modifier.fillMaxSize().pointerInput(card.id, scene) {
            detectTapGestures(onTap = { if (it.x < size.width * 0.3f) back() else forward() }, onDoubleTap = { doubleLike() })
        })
        Column(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth().clearAndSetSemantics {}, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                repeat(card.scenes.size) { k ->
                    val value = when { k < scene -> 1f; k > scene -> 0f; reduceMotion || !autoAdvance -> 1f; else -> progress }
                    LinearProgressIndicator({ value }, Modifier.weight(1f).height(3.dp), color = Color.White, trackColor = Color.White.copy(0.35f), gapSize = 0.dp, drawStopIndicator = {})
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Logo(card, 30.dp)
                Text(card.organization, color = Color.White, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                Text("· 오늘의 맞춤 대기열 ${index + 1}/$count", Modifier.weight(1f), color = Color.White.copy(0.75f), style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                IconButton(close) { Icon(Icons.Rounded.Close, "닫기", tint = Color.White) }
            }
        }
        val actions = listOf(
            CustomAccessibilityAction("다음 장면") { forward(); true }, CustomAccessibilityAction("이전 장면") { back(); true },
            CustomAccessibilityAction(if (liked) "즐겨찾기 해제" else "즐겨찾기") { like(null); true },
            CustomAccessibilityAction(if (added) "내 활동에 있음" else "내 활동에 추가") { plus(); true },
            CustomAccessibilityAction("자세히 보기") { detail(); true }, CustomAccessibilityAction("다음 활동") { next(); true },
        ) + QueueReaction.entries.filter { it != QueueReaction.LESS_FIELD || card.field != null }.map { CustomAccessibilityAction(it.label) { react(it); true } }
        Column(Modifier.align(Alignment.BottomStart).padding(start = 18.dp, end = 84.dp, bottom = 50.dp).semantics(mergeDescendants = true) { customActions = actions },
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            val still = reduceMotion
            val label: @Composable (String) -> Unit = { Text(it, Modifier.rise(0, scene, still), color = Accent, fontWeight = FontWeight.SemiBold, fontSize = 15.sp) }
            when (val shown = card.scenes[scene.coerceIn(0, last)]) {
                is QueueSceneState.Hook -> {
                    card.tagLine?.let { label(it) }
                    Text(shown.hook, Modifier.rise(1, scene, still), color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 30.sp, lineHeight = 1.25.em, letterSpacing = (-0.5).sp)
                    shown.name?.let { Text(it, Modifier.rise(2, scene, still), color = Color.White.copy(0.9f), fontSize = 15.sp) }
                }
                is QueueSceneState.Highlights -> { label("이런 걸 해요"); Facts(shown.facts, scene, still) }
                is QueueSceneState.Eligibility -> {
                    label("지원 대상")
                    if (shown.facts.isNotEmpty()) Facts(shown.facts, scene, still) else shown.text?.let { Sentence(it, scene, still) }
                }
                is QueueSceneState.Benefit -> { label("혜택"); Sentence(shown.text, scene, still) }
                is QueueSceneState.Deadline -> {
                    label(shown.title)
                    shown.count?.let { Text(it, Modifier.rise(1, scene, still), color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 64.sp, lineHeight = 1.em) }
                    Text(shown.deadline ?: if (shown.event) "행사 날짜는 공식 안내에서 확인해요" else "마감일은 공식 안내에서 확인해요", Modifier.rise(2, scene, still), color = Color.White.copy(0.9f), fontSize = 16.sp)
                    if (shown.reasons.isNotEmpty()) Row(Modifier.rise(2, scene, still), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        shown.reasons.take(3).forEach { reason ->
                            Surface(color = Color.White.copy(0.22f), contentColor = Color.White, shape = RoundedCornerShape(50)) {
                                Text(reason, Modifier.padding(horizontal = 10.dp, vertical = 4.dp), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, maxLines = 1)
                            }
                        }
                    }
                }
            }
        }
        Column(Modifier.align(Alignment.BottomEnd).padding(end = 10.dp, bottom = 56.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Rail("즐겨찾기") {
                FilledIconToggleButton(liked, { like(it) }, Modifier.size(48.dp), colors = IconButtonDefaults.filledIconToggleButtonColors(
                    containerColor = Glass, contentColor = Color.White, checkedContainerColor = Glass, checkedContentColor = Heart)) {
                    Icon(if (liked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, if (liked) "즐겨찾기 해제" else "즐겨찾기", Modifier.size(26.dp))
                }
            }
            // `+` right beside the heart (contract "`+` 내 활동에 추가").
            Rail("내 활동") { RailButton(if (added) Icons.Rounded.CheckCircle else Icons.Rounded.AddCircleOutline, if (added) "내 활동에 있음" else "내 활동에 추가", plus) }
            Rail("자세히") { RailButton(Icons.Rounded.Info, "자세히 보기", detail) }
            Rail("다음") { RailButton(Icons.Rounded.KeyboardArrowUp, "다음 활동", next) }
            Rail(null) { RailButton(Icons.Rounded.MoreHoriz, "더 보기", more) }
        }
        HeartBurst(burst, reduceMotion, Modifier.align(Alignment.Center))
    }
}

@Composable private fun Facts(facts: List<QueueFact>, scene: Int, still: Boolean) {
    facts.forEachIndexed { i, fact ->
        Row(Modifier.rise(i + 1, scene, still), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(36.dp).background(Color.White.copy(0.18f), CircleShape), contentAlignment = Alignment.Center) { Icon(fact.icon.vector, null, Modifier.size(20.dp), tint = Color.White) }
            Text(fact.text, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 19.sp)
        }
    }
}

@Composable private fun Sentence(text: String, scene: Int, still: Boolean) {
    Text(text, Modifier.rise(1, scene, still), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 24.sp, lineHeight = 1.3.em)
}

@Composable private fun Rail(label: String?, button: @Composable () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        button()
        // The button already says it; the label is for sight only.
        label?.let { Text(it, Modifier.clearAndSetSemantics {}, color = Color.White, fontSize = 12.sp) }
    }
}

@Composable private fun RailButton(icon: ImageVector, description: String, onClick: () -> Unit) {
    FilledIconButton(onClick, Modifier.size(48.dp), colors = IconButtonDefaults.filledIconButtonColors(containerColor = Glass, contentColor = Color.White)) { Icon(icon, description) }
}

/** The cover with a slow Ken Burns zoom and drift; no cover (or a failed one) is the program logo on a gradient of the card's hue. */
@Composable private fun Cover(card: QueueCardState, still: Boolean) {
    var failed by remember(card.coverUrl) { mutableStateOf(false) }
    val drift = if (still) null else rememberInfiniteTransition("kenBurns").animateFloat(0f, 1f, infiniteRepeatable(tween(9_000, easing = FastOutSlowInEasing), RepeatMode.Reverse), "drift")
    Box(Modifier.fillMaxSize().graphicsLayer {
        val t = drift?.value ?: 0f
        scaleX = 1.05f + 0.17f * t; scaleY = scaleX
        translationX = -0.04f * size.width * t; translationY = -0.03f * size.height * t
    }) {
        if (card.coverUrl != null && !failed) AsyncImage(card.coverUrl, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop, onError = { failed = true })
        else {
            Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Color.hsl(card.hue, 0.65f, 0.55f), Color.hsl(card.hue, 0.7f, 0.28f), Color.hsl(card.hue, 0.8f, 0.08f)))))
            Logo(card, 120.dp, Modifier.align(LogoAlignment).clearAndSetSemantics {})
        }
    }
}
private val LogoAlignment = androidx.compose.ui.BiasAlignment(0f, -0.35f)

@Composable private fun Logo(card: QueueCardState, size: androidx.compose.ui.unit.Dp, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(size * 0.27f)
    if (card.logoUrl != null) AsyncImage(card.logoUrl, null, modifier.size(size).clip(shape).background(Color.White).padding(size * 0.08f))
    else InitialAvatar(card.organization, size, modifier, shape)
}

/** The double-tap heart: a pop and fade, or shown still for a moment when motion is reduced. */
@Composable private fun HeartBurst(burst: Int, still: Boolean, modifier: Modifier) {
    if (burst == 0) return
    val t = remember(burst) { Animatable(0f) }
    LaunchedEffect(burst) { if (still) { t.snapTo(0.5f); delay(600); t.snapTo(1f) } else t.animateTo(1f, tween(800)) }
    val v = t.value
    if (v >= 1f) return
    val scale = if (still) 1f else if (v < 0.25f) 0.3f + v / 0.25f * 0.85f else 1.15f + (v - 0.25f) / 0.75f * 0.15f
    val alpha = if (still) 1f else if (v < 0.25f) v / 0.25f else if (v < 0.7f) 1f else (1f - v) / 0.3f
    Icon(Icons.Rounded.Favorite, null, modifier.size(120.dp).graphicsLayer { scaleX = scale; scaleY = scale; this.alpha = alpha }, tint = Color.White)
}

/** Text rising from below, [order] × 150 ms after the scene (re)starts ([key]); nothing moves when [still]. */
@Composable private fun Modifier.rise(order: Int, key: Any, still: Boolean): Modifier {
    if (still) return this
    val t = remember(key) { Animatable(0f) }
    LaunchedEffect(key) { delay(150L * order); t.animateTo(1f, tween(500)) }
    return graphicsLayer { alpha = t.value; translationY = (1f - t.value) * 18.dp.toPx() }
}

/** The ad below a close button; the ad view itself carries the `광고` badge and AdChoices. */
@Composable private fun AdPage(ad: (@Composable (Modifier) -> Unit)?, close: () -> Unit) {
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.End) {
            IconButton(close) { Icon(Icons.Rounded.Close, "닫기", tint = Color.White) }
        }
        ad?.invoke(Modifier.weight(1f).fillMaxWidth())
    }
}

@Composable private fun EndPage(state: QueueViewState, refill: () -> Unit, close: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically)) {
        Icon(Icons.Rounded.CheckCircle, null, Modifier.size(56.dp), tint = Accent)
        Text("오늘 대기열을 다 봤어요", color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(when { state.failed -> "새 대기열을 받지 못했어요. 잠시 뒤 다시 시도해 주세요."; state.exhausted -> "지금은 더 보여 드릴 활동이 없어요"; else -> "남은 후보로 대기열을 다시 만들 수 있어요" }, color = Color.White.copy(0.75f), style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(8.dp))
        Button(refill, enabled = !state.refilling && !state.exhausted) {
            if (state.refilling) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text("더 받기")
        }
        TextButton(close) { Text("닫기", color = Color.White) }
    }
}

private val QueueIcon.vector: ImageVector get() = when (this) {
    QueueIcon.COST -> Icons.Rounded.Payments
    QueueIcon.PERIOD -> Icons.Rounded.CalendarMonth
    QueueIcon.TEAM -> Icons.Rounded.Groups
    QueueIcon.MENTOR -> Icons.Rounded.SupervisorAccount
    QueueIcon.AWARD -> Icons.Rounded.EmojiEvents
    QueueIcon.PLACE -> Icons.Rounded.Place
    QueueIcon.TALK -> Icons.Rounded.Mic
    QueueIcon.TICKET -> Icons.Rounded.ConfirmationNumber
    QueueIcon.AGE -> Icons.Rounded.Cake
    QueueIcon.EDUCATION -> Icons.Rounded.School
    QueueIcon.SCHOOL_YEAR -> Icons.Rounded.Numbers
    QueueIcon.EMPLOYMENT -> Icons.Rounded.Work
    QueueIcon.MAJOR -> Icons.AutoMirrored.Rounded.MenuBook
    QueueIcon.NATIONALITY -> Icons.Rounded.Public
    QueueIcon.EXPERIENCE -> Icons.Rounded.Schedule
    QueueIcon.OTHER -> Icons.Rounded.Info
}
