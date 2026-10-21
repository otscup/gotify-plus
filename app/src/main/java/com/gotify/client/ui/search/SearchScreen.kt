package com.gotify.client.ui.search

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.gotify.client.R
import com.gotify.client.data.model.GotifyApplication
import com.gotify.client.data.model.GotifyMessage
import com.gotify.client.ui.components.*
import com.gotify.client.ui.viewmodel.DateRange
import com.gotify.client.ui.viewmodel.PriorityFilter
import com.gotify.client.ui.viewmodel.SearchFilters
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    query: String,
    results: List<GotifyMessage>,
    applications: Map<Int, GotifyApplication>,
    clientToken: String,
    serverBaseUrl: String,
    filters: SearchFilters,
    onPriorityFilter: (PriorityFilter) -> Unit,
    onAppFilter: (Int?) -> Unit,
    onDateRange: (DateRange) -> Unit,
    onQueryChange: (String) -> Unit,
    onClearQuery: () -> Unit,
    onBack: () -> Unit,
    onMessageClick: (GotifyMessage) -> Unit,
    modifier: Modifier = Modifier
) {
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar   = {
            TopAppBar(
                title = {
                    // Pill-shaped search field that fits the app bar (an outlined field gets clipped at this height).
                    TextField(
                        value         = query,
                        onValueChange = onQueryChange,
                        modifier      = Modifier
                            .fillMaxWidth()
                            .padding(end = 12.dp)
                            .heightIn(min = 48.dp)
                            .focusRequester(focusRequester),
                        placeholder   = { Text(stringResource(R.string.search_hint)) },
                        leadingIcon   = { Icon(Icons.Outlined.Search, contentDescription = null) },
                        singleLine    = true,
                        textStyle     = MaterialTheme.typography.bodyLarge,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Text,
                            imeAction    = ImeAction.Search
                        ),
                        keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                        trailingIcon  = {
                            AnimatedVisibility(visible = query.isNotEmpty(), enter = fadeIn(), exit = fadeOut()) {
                                IconButton(onClick = onClearQuery) {
                                    Icon(Icons.Outlined.Close, stringResource(R.string.cd_clear_search))
                                }
                            }
                        },
                        shape  = CircleShape,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor   = MaterialTheme.colorScheme.surfaceContainerHigh,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            focusedIndicatorColor   = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            disabledIndicatorColor  = Color.Transparent
                        )
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.cd_back))
                    }
                },
                colors = gotifyTopAppBarColors(),
                scrollBehavior = scrollBehavior
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            SearchFilterRow(
                filters          = filters,
                applications     = applications,
                onPriorityFilter = onPriorityFilter,
                onAppFilter      = onAppFilter,
                onDateRange      = onDateRange
            )
            Box(Modifier.weight(1f)) {
            when {

                query.isBlank() && !filters.isActive -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        EmptyState(
                            icon     = Icons.Outlined.Search,
                            title    = stringResource(R.string.search_empty_title),
                            subtitle = stringResource(R.string.search_empty_sub)
                        )
                    }
                }

                results.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        EmptyState(
                            icon     = Icons.Outlined.SearchOff,
                            title    = if (query.isBlank()) stringResource(R.string.search_no_match) else stringResource(R.string.search_no_results, query.trim()),
                            subtitle = stringResource(R.string.search_try_other)
                        )
                    }
                }

                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().widthIn(max = 840.dp).align(Alignment.TopCenter),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        item(key = "count") {
                            SectionHeader(
                                title = pluralStringResource(R.plurals.search_results, results.size, results.size),
                                modifier = Modifier.padding(start = 4.dp, bottom = 2.dp)
                            )
                        }

                        items(results, key = { it.id }) { message ->
                            val app = applications[message.appId]
                            SearchResultCard(
                                message     = message,
                                appName     = app?.name ?: stringResource(R.string.app_fallback, message.appId),
                                appImageUrl = app?.image,
                                query       = query,
                                clientToken = clientToken,
                                authBaseUrl = serverBaseUrl,
                                onClick     = { onMessageClick(message) }
                            )
                        }
                    }
                }
            }
          }
        }
    }
}

@Composable
private fun SearchFilterRow(
    filters: SearchFilters,
    applications: Map<Int, GotifyApplication>,
    onPriorityFilter: (PriorityFilter) -> Unit,
    onAppFilter: (Int?) -> Unit,
    onDateRange: (DateRange) -> Unit
) {
    var appMenuOpen by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box {
            FilterChip(
                selected    = filters.appId != null,
                onClick     = { appMenuOpen = true },
                label       = { Text(filters.appId?.let { applications[it]?.name } ?: stringResource(R.string.filter_any_app), maxLines = 1) },
                trailingIcon = { Icon(Icons.Outlined.ArrowDropDown, null, Modifier.size(FilterChipDefaults.IconSize)) }
            )
            DropdownMenu(expanded = appMenuOpen, onDismissRequest = { appMenuOpen = false }) {
                DropdownMenuItem(text = { Text(stringResource(R.string.filter_any_app)) }, onClick = { onAppFilter(null); appMenuOpen = false })
                applications.values.sortedBy { it.name.lowercase() }.forEach { app ->
                    DropdownMenuItem(text = { Text(app.name) }, onClick = { onAppFilter(app.id); appMenuOpen = false })
                }
            }
        }
        VerticalDivider(Modifier.height(24.dp))
        PriorityFilter.entries.forEach { p ->
            FilterChip(
                selected = filters.priority == p,
                onClick  = { onPriorityFilter(p) },
                label    = { Text(stringResource(when (p) {
                    PriorityFilter.HIGH   -> R.string.filter_priority_high
                    PriorityFilter.NORMAL -> R.string.filter_priority_normal
                    PriorityFilter.LOW    -> R.string.filter_priority_low
                })) }
            )
        }
        VerticalDivider(Modifier.height(24.dp))
        listOf(DateRange.DAY, DateRange.WEEK).forEach { r ->
            FilterChip(
                selected = filters.dateRange == r,
                onClick  = { onDateRange(r) },
                label    = { Text(stringResource(if (r == DateRange.DAY) R.string.filter_last_day else R.string.filter_last_week)) }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchResultCard(
    message: GotifyMessage,
    appName: String,
    appImageUrl: String?,
    query: String,
    clientToken: String,
    authBaseUrl: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick   = onClick,
        modifier  = modifier.fillMaxWidth(),
        shape     = MaterialTheme.shapes.large,
        colors    = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                AppIcon(
                    imageUrl = appImageUrl,
                    appName = appName,
                    clientToken = clientToken,
                    authBaseUrl = authBaseUrl,
                    size = 28.dp
                )
                Text(
                    text       = appName,
                    style      = MaterialTheme.typography.labelLarge,
                    color      = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines   = 1,
                    overflow   = TextOverflow.Ellipsis,
                    modifier   = Modifier.weight(1f)
                )
                if (message.priority >= 8) PriorityBadge(priority = message.priority)
                RelativeTime(isoDate = message.date)
            }

            Spacer(Modifier.height(8.dp))

            if (message.title.isNotBlank()) {
                Text(
                    text       = highlightQuery(message.title, query),
                    style      = MaterialTheme.typography.titleMedium,
                    maxLines   = 1,
                    overflow   = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
            }

            Text(
                text     = highlightQuery(remember(message.message) { previewText(message.message) }, query),
                style    = MaterialTheme.typography.bodyMedium,
                color    = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun highlightQuery(text: String, query: String): AnnotatedString {
    val style = SpanStyle(
        background = MaterialTheme.colorScheme.primaryContainer,
        color      = MaterialTheme.colorScheme.onPrimaryContainer,
        fontWeight = FontWeight.SemiBold
    )
    // The ViewModel searches with the trimmed query, so highlight the same thing.
    val needle = query.trim()
    return remember(text, needle, style) {
        buildAnnotatedString {
            if (needle.isEmpty()) {
                append(text)
                return@buildAnnotatedString
            }
            // ignoreCase indexOf works on the original string, so indices stay valid even when
            // lowercasing would change the text length (e.g. "İ").
            var start = 0
            while (start < text.length) {
                val idx = text.indexOf(needle, start, ignoreCase = true)
                if (idx == -1) {
                    append(text.substring(start))
                    break
                }
                append(text.substring(start, idx))
                withStyle(style) { append(text.substring(idx, idx + needle.length)) }
                start = idx + needle.length
            }
        }
    }
}
