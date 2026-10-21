package com.gotify.client.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import android.content.Context
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.gotify.client.R
import androidx.core.net.toUri
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.net.URI
import java.time.temporal.ChronoUnit
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.lazy.LazyListState
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

// ─── Connection Status Dot ────────────────────────────────────────────────────

enum class ConnectionStatus { CONNECTED, CONNECTING, DISCONNECTED, ERROR }

@Composable
fun ConnectionDot(status: ConnectionStatus, modifier: Modifier = Modifier) {
    val color = when (status) {
        ConnectionStatus.CONNECTED    -> com.gotify.client.ui.theme.AccentGreen
        ConnectionStatus.CONNECTING   -> com.gotify.client.ui.theme.AccentOrange
        ConnectionStatus.DISCONNECTED -> MaterialTheme.colorScheme.outline
        ConnectionStatus.ERROR        -> com.gotify.client.ui.theme.AccentRed
    }
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue  = if (status == ConnectionStatus.CONNECTING) 0.3f else 1f,
        animationSpec = infiniteRepeatable(
            animation  = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )
    Box(
        modifier = modifier
            .size(8.dp)
            .clip(CircleShape)
            .background(color.copy(alpha = alpha))
    )
}

// ─── App Icon ─────────────────────────────────────────────────────────────────
//
// FIX 4 — Guaranteed initials always render. The image overlays on top only
// if it loads successfully. If Coil gets a 401 / network error / empty URL,
// the letter + colour background is already drawn underneath and visible.
//
// FIX 4 — Auth header: Gotify's /image/ endpoint requires X-Gotify-Key.
// We pass it via ImageRequest headers so Coil can actually download the icon.

@Composable
fun AppIcon(
    imageUrl:  String?,          // fully-resolved URL (null = show initials only)
    appName:   String,
    modifier:  Modifier = Modifier,
    clientToken: String = "",    // Gotify client token for auth header
    authBaseUrl: String = "",     // Only send the token to this server origin
    size:      Dp = 40.dp
) {
    val shape   = MaterialTheme.shapes.medium
    // Deterministic hue from app name so every app always has the same colour
    val hue     = (appName.hashCode().and(0x7FFFFFFF) % 360).toFloat()
    val bgColor = Color.hsl(hue, 0.55f, 0.38f)
    val initial = appName.firstOrNull()?.uppercaseChar()?.toString() ?: "?"
    val initialSize = with(LocalDensity.current) { (size * 0.42f).toSp() }

    Box(
        modifier         = modifier
            .size(size)
            .clip(shape)
            .background(bgColor),      // ← always drawn first
        contentAlignment = Alignment.Center
    ) {
        // Initial letter — always visible as a background layer
        Text(
            text       = initial,
            color      = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize   = initialSize,
            lineHeight = initialSize
        )

        // Image layer — only renders if the URL is non-empty AND loads successfully
        if (!imageUrl.isNullOrBlank()) {
            val context = LocalContext.current
            val request = remember(imageUrl, clientToken, authBaseUrl) {
                ImageRequest.Builder(context)
                    .data(imageUrl)
                    .apply {
                        if (shouldAttachGotifyKey(imageUrl, clientToken, authBaseUrl)) {
                            addHeader("X-Gotify-Key", clientToken)
                        }
                    }
                    .crossfade(true)
                    .build()
            }

            SubcomposeAsyncImage(
                model              = request,
                contentDescription = appName,
                contentScale       = ContentScale.Crop,
                modifier           = Modifier.fillMaxSize(),
                // Loading state: show nothing (initials underneath are visible)
                loading = { },
                // Error state: show nothing (initials underneath are visible)
                error   = { }
            )
        }
    }
}

// ─── Priority Badge ───────────────────────────────────────────────────────────

@Composable
fun PriorityBadge(priority: Int, modifier: Modifier = Modifier) {
    if (priority == 0) return
    val color = priorityColor(priority)
    val label = priorityLabel(priority)
    Surface(
        modifier = modifier,
        shape    = MaterialTheme.shapes.extraSmall,
        color    = color.copy(alpha = 0.15f)
    ) {
        Text(
            text       = label,
            modifier   = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            style      = MaterialTheme.typography.labelSmall,
            color      = color,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
fun priorityColor(priority: Int): Color = when {
    priority == 0 -> MaterialTheme.colorScheme.outlineVariant
    priority <= 3 -> MaterialTheme.colorScheme.onSurfaceVariant
    priority <= 7 -> com.gotify.client.ui.theme.AccentOrange
    else          -> com.gotify.client.ui.theme.AccentRed
}

@Composable
fun priorityLabel(priority: Int): String = stringResource(
    when {
        priority == 0 -> R.string.priority_silent
        priority <= 3 -> R.string.priority_low
        priority <= 7 -> R.string.priority_normal
        else          -> R.string.priority_high
    }
)

// ─── Relative Timestamp ───────────────────────────────────────────────────────

@Composable
fun RelativeTime(isoDate: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val now by produceState(initialValue = Instant.now(), key1 = isoDate) {
        while (isActive) {
            delay(60_000)
            value = Instant.now()
        }
    }
    val text = remember(isoDate, now) { formatRelativeTime(isoDate, now, context) }
    Text(
        text     = text,
        style    = MaterialTheme.typography.labelSmall,
        color    = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
    )
}

/** Gotify sends RFC 3339 with the server's offset (e.g. +05:30); Instant.parse only accepts 'Z' before Android 14. */
fun parseGotifyDate(isoDate: String): Instant? =
    runCatching { java.time.OffsetDateTime.parse(isoDate).toInstant() }.getOrNull()

/** "Today", "Yesterday", "Monday, Sep 22", or "Sep 22, 2025" for list section headers. */
fun dayLabel(isoDate: String, context: Context, today: java.time.LocalDate = java.time.LocalDate.now(), zone: ZoneId = ZoneId.systemDefault()): String {
    val date = parseGotifyDate(isoDate)?.atZone(zone)?.toLocalDate() ?: return context.getString(R.string.day_earlier)
    val locale = context.resources.configuration.locales.get(0)
    return when {
        date == today                -> context.getString(R.string.day_today)
        date == today.minusDays(1)   -> context.getString(R.string.day_yesterday)
        date.year == today.year      -> date.format(DateTimeFormatter.ofPattern("EEEE, MMM d", locale))
        else                         -> date.format(DateTimeFormatter.ofPattern("MMM d, yyyy", locale))
    }
}

/** Opaque so it can be pinned with `stickyHeader` without cards showing through. */
@Composable
fun DateHeader(label: String, modifier: Modifier = Modifier) {
    Text(
        text       = label,
        style      = MaterialTheme.typography.labelLarge,
        color      = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier   = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .padding(start = 4.dp, top = 10.dp, bottom = 6.dp)
            .semantics { heading() }
    )
}

private fun formatRelativeTime(isoDate: String, now: Instant, context: Context): String {
    return try {
        val instant = parseGotifyDate(isoDate) ?: return isoDate
        val diff    = ChronoUnit.MINUTES.between(instant, now)
        when {
            diff < 1     -> context.getString(R.string.rel_just_now)
            diff < 60    -> context.getString(R.string.rel_minutes_ago, diff)
            diff < 1440  -> context.getString(R.string.rel_hours_ago, diff / 60)
            diff < 10080 -> context.getString(R.string.rel_days_ago, diff / 1440)
            else -> DateTimeFormatter.ofPattern("MMM d", context.resources.configuration.locales.get(0))
                .withZone(ZoneId.systemDefault())
                .format(instant)
        }
    } catch (e: Exception) { isoDate }
}

// ─── Message Card ─────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessageCard(
    title:       String,
    message:     String,
    appName:     String,
    appImageUrl: String?,
    priority:    Int,
    date:        String,
    onClick:     () -> Unit,
    onDelete:    () -> Unit,
    modifier:    Modifier = Modifier,
    clientToken: String = "",
    authBaseUrl: String = "",
    isRead:      Boolean = false
) {
    val swipeState = rememberSwipeToDismissBoxState()
    val scope = rememberCoroutineScope()
    val deleteActionLabel = stringResource(R.string.cd_delete_message)
    val unreadLabel = stringResource(R.string.cd_unread)
    SwipeToDismissBox(
        state                       = swipeState,
        modifier                    = modifier.semantics {
            customActions = listOf(CustomAccessibilityAction(deleteActionLabel) { onDelete(); true })
        },
        enableDismissFromStartToEnd = false,
        onDismiss                   = {
            // Snap back; the caller shows a confirm dialog before deleting on the server.
            onDelete()
            scope.launch { swipeState.reset() }
        },
        backgroundContent           = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(MaterialTheme.shapes.large)
                    .background(MaterialTheme.colorScheme.errorContainer)
                    .padding(horizontal = 24.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                Icon(
                    Icons.Outlined.DeleteOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
    ) {
        Card(
            onClick   = onClick,
            modifier  = Modifier.fillMaxWidth(),
            shape     = MaterialTheme.shapes.large,
            colors    = CardDefaults.cardColors(
                containerColor = if (isRead) MaterialTheme.colorScheme.surfaceContainerLow
                                 else MaterialTheme.colorScheme.surfaceContainer
            ),
            border    = if (isRead) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Row(
                modifier              = Modifier.padding(14.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                AppIcon(
                    imageUrl    = appImageUrl,
                    appName     = appName,
                    clientToken = clientToken,
                    authBaseUrl = authBaseUrl,
                    size        = 40.dp
                )
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // Name + time take the free space so the badge and unread dot stay pinned to the end.
                        Row(
                            modifier              = Modifier.weight(1f),
                            verticalAlignment     = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                        Text(
                            text       = appName,
                            style      = MaterialTheme.typography.labelLarge,
                            color      = if (isRead) MaterialTheme.colorScheme.onSurfaceVariant
                                         else MaterialTheme.colorScheme.primary,
                            maxLines   = 1,
                            overflow   = TextOverflow.Ellipsis,
                            modifier   = Modifier.weight(1f, fill = false)
                        )
                        Text("·", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        RelativeTime(isoDate = date)
                        }
                        // Every Gotify message has a priority; in a list only "High" is worth the ink.
                        if (priority >= 8) PriorityBadge(priority = priority)
                        if (!isRead) {
                            Box(
                                Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary)
                                    .semantics { contentDescription = unreadLabel }
                            )
                        }
                    }
                    if (title.isNotBlank()) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text       = title,
                            style      = MaterialTheme.typography.titleMedium,
                            fontWeight = if (isRead) FontWeight.Medium else FontWeight.SemiBold,
                            color      = MaterialTheme.colorScheme.onSurface,
                            maxLines   = 1,
                            overflow   = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text     = remember(message) { previewText(message) },
                        style    = MaterialTheme.typography.bodyMedium,
                        color    = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

private val MD_IMAGE   = Regex("""!\[([^\]]*)]\([^)]*\)""")
private val MD_LINK    = Regex("""\[([^\]]+)]\([^)]*\)""")
private val MD_EMPH    = Regex("""(\*\*|__|~~|`)""")
private val MD_HEADING = Regex("""(?m)^\s{0,3}#{1,6}\s+""")
private val MD_BULLET  = Regex("""(?m)^\s*[-*+]\s+""")
private val WHITESPACE = Regex("""\s+""")

/**
 * One-line list preview: drops common Markdown markup (bold, links, headings, bullets) and
 * collapses line breaks, so previews read as text rather than syntax.
 */
fun previewText(raw: String): String = raw
    .replace(MD_IMAGE) { it.groupValues[1] }
    .replace(MD_LINK) { it.groupValues[1] }
    .replace(MD_HEADING, "")
    .replace(MD_BULLET, "• ")
    .replace(MD_EMPH, "")
    .replace(WHITESPACE, " ")
    .trim()

internal fun shouldAttachGotifyKey(imageUrl: String, token: String, authBaseUrl: String): Boolean {
    if (token.isBlank() || authBaseUrl.isBlank()) return false
    val image = runCatching { URI(imageUrl) }.getOrNull() ?: return false
    val base = runCatching { URI(authBaseUrl) }.getOrNull() ?: return false
    return image.scheme in setOf("http", "https") &&
        base.scheme in setOf("http", "https") &&
        image.scheme.equals(base.scheme, ignoreCase = true) &&
        image.host.equals(base.host, ignoreCase = true) &&
        effectivePort(image) == effectivePort(base)
}

private fun effectivePort(uri: URI): Int = when {
    uri.port != -1 -> uri.port
    uri.scheme.equals("https", ignoreCase = true) -> 443
    else -> 80
}

// ─── Section Header ───────────────────────────────────────────────────────────

@Composable
fun SectionHeader(
    title:    String,
    modifier: Modifier = Modifier,
    trailing: @Composable (() -> Unit)? = null
) {
    Row(
        modifier              = modifier.fillMaxWidth(),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text          = title.uppercase(),
            style         = MaterialTheme.typography.labelSmall,
            color         = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight    = FontWeight.Bold,
            letterSpacing = 1.2.sp
        )
        trailing?.invoke()
    }
}

// ─── Empty State ──────────────────────────────────────────────────────────────

@Composable
fun EmptyState(
    icon:     ImageVector,
    title:    String,
    subtitle: String,
    modifier: Modifier = Modifier,
    action:   (@Composable () -> Unit)? = null
) {
    Column(
        modifier              = modifier
            .widthIn(max = 360.dp)
            .padding(32.dp),
        horizontalAlignment   = Alignment.CenterHorizontally
    ) {
        Box(
            modifier         = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector        = icon,
                contentDescription = null,
                modifier           = Modifier.size(32.dp),
                tint               = MaterialTheme.colorScheme.primary
            )
        }
        Spacer(Modifier.height(16.dp))
        Text(
            text      = title,
            style     = MaterialTheme.typography.titleMedium,
            color     = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text      = subtitle,
            style     = MaterialTheme.typography.bodyMedium,
            color     = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        if (action != null) {
            Spacer(Modifier.height(20.dp))
            action()
        }
    }
}

// ─── Loading Shimmer ──────────────────────────────────────────────────────────

@Composable
fun ShimmerBox(modifier: Modifier = Modifier, shape: Shape = RoundedCornerShape(6.dp)) {
    val infiniteTransition = rememberInfiniteTransition(label = "shimmer")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue  = 0.7f,
        animationSpec = infiniteRepeatable(
            animation  = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "shimmerAlpha"
    )
    Box(
        modifier = modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.04f + 0.08f * alpha))
    )
}

@Composable
fun MessageCardSkeleton(modifier: Modifier = Modifier) {
    Card(
        modifier  = modifier.fillMaxWidth(),
        shape     = MaterialTheme.shapes.large,
        colors    = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Row(
            modifier              = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ShimmerBox(Modifier.size(40.dp), shape = MaterialTheme.shapes.medium)
            Column(
                modifier            = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ShimmerBox(Modifier.size(width = 120.dp, height = 10.dp))
                ShimmerBox(Modifier.fillMaxWidth(0.6f).height(14.dp))
                ShimmerBox(Modifier.fillMaxWidth().height(10.dp))
                ShimmerBox(Modifier.fillMaxWidth(0.8f).height(10.dp))
            }
        }
    }
}

// ─── Info chip ───────────────────────────────────────────────────────────────

/** Small tonal pill for metadata (counts, states) so every screen uses the same treatment. */
@Composable
fun InfoChip(
    text:           String,
    modifier:       Modifier = Modifier,
    icon:           ImageVector? = null,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
    contentColor:   Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    Surface(modifier = modifier, shape = CircleShape, color = containerColor, contentColor = contentColor) {
        Row(
            modifier              = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (icon != null) Icon(icon, contentDescription = null, modifier = Modifier.size(12.dp))
            Text(text, style = MaterialTheme.typography.labelSmall, maxLines = 1)
        }
    }
}

// ─── Lists ───────────────────────────────────────────────────────────────────

/**
 * Lets a horizontally scrolling row inside a padded list run to the screen edges,
 * so chips scroll off the edge instead of being clipped at the list padding.
 */
fun Modifier.horizontalBleed(bleed: Dp): Modifier = layout { measurable, constraints ->
    val extra = (bleed * 2).roundToPx()
    val placeable = measurable.measure(
        constraints.copy(
            minWidth = constraints.minWidth + extra,
            maxWidth = if (constraints.hasBoundedWidth) constraints.maxWidth + extra else constraints.maxWidth
        )
    )
    layout(placeable.width - extra, placeable.height) { placeable.place(-extra / 2, 0) }
}

/** Calls [onLoadMore] when the user scrolls near the end of the list (infinite scroll). */
@Composable
fun LoadMoreEffect(listState: LazyListState, hasMore: Boolean, onLoadMore: () -> Unit, threshold: Int = 6) {
    val currentLoadMore by rememberUpdatedState(onLoadMore)
    LaunchedEffect(listState, hasMore) {
        if (!hasMore) return@LaunchedEffect
        // Keyed on the item count too, so a page that still leaves us at the end triggers the next one.
        snapshotFlow {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: -1
            val nearEnd = info.totalItemsCount > 0 && last >= info.totalItemsCount - threshold
            nearEnd to info.totalItemsCount
        }
            .distinctUntilChanged()
            .filter { (nearEnd, _) -> nearEnd }
            .collect { currentLoadMore() }
    }
}

/** Shown at the end of a paged list while more items exist. */
@Composable
fun LoadingMoreIndicator(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.5.dp)
    }
}

// ─── Top app bar ─────────────────────────────────────────────────────────────

/** Flat on the page background at rest; tonal once content scrolls beneath it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun gotifyTopAppBarColors(): TopAppBarColors = TopAppBarDefaults.topAppBarColors(
    containerColor         = MaterialTheme.colorScheme.background,
    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer
)

// ─── Undoable delete ─────────────────────────────────────────────────────────

/** Deletes immediately (ViewModel commits after its undo window) and offers Undo in a snackbar. */
@Composable
fun rememberUndoableDelete(
    snackbarHostState: SnackbarHostState,
    onDelete: (Long) -> Unit,
    onUndo:   (Long) -> Unit
): (Long) -> Unit {
    val scope = rememberCoroutineScope()
    val deletedText = stringResource(R.string.msg_deleted)
    val undoText = stringResource(R.string.action_undo)
    val currentDelete by rememberUpdatedState(onDelete)
    val currentUndo by rememberUpdatedState(onUndo)
    return remember(snackbarHostState) {
        { id ->
            currentDelete(id)
            scope.launch {
                snackbarHostState.currentSnackbarData?.dismiss()
                val result = snackbarHostState.showSnackbar(
                    message     = deletedText,
                    actionLabel = undoText,
                    duration    = SnackbarDuration.Short
                )
                if (result == SnackbarResult.ActionPerformed) currentUndo(id)
            }
        }
    }
}

// ─── Snackbar helpers ────────────────────────────────────────────────────────

suspend fun SnackbarHostState.showInfo(message: String) =
    showSnackbar(message = message, duration = SnackbarDuration.Short)

suspend fun SnackbarHostState.showError(message: String) =
    showSnackbar(message = message, duration = SnackbarDuration.Long, actionLabel = "Dismiss")
