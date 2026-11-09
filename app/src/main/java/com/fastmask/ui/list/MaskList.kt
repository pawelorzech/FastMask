package com.fastmask.ui.list

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.text
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.fastmask.domain.model.MaskedEmail
import com.fastmask.ui.common.ContentWidth
import com.fastmask.ui.components.expressive.SectionHeader
import com.fastmask.ui.components.expressive.SegmentedGap
import java.time.Instant

@Composable
internal fun MaskList(
    uiState: MaskedEmailListUiState,
    listState: LazyListState,
    highlightEmail: String?,
    onRefresh: () -> Unit,
    onRowOpen: (MaskedEmail) -> Unit,
    onRowCopy: (MaskedEmail) -> Unit,
    onTutorialBounds: (TutorialTarget, Rect) -> Unit,
    demoBanner: @Composable () -> Unit,
) {
    val nowSec = remember(uiState.emails) { Instant.now().epochSecond }
    val sections = remember(uiState.filteredEmails, nowSec) {
        groupMasksBySection(uiState.filteredEmails, nowSec)
    }
    val pullState = rememberPullToRefreshState()
    val spatial = MaterialTheme.motionScheme.defaultSpatialSpec<IntOffset>()
    val effects = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()

    // Wide windows: the list keeps a readable column. The extra inset lives in the
    // LazyColumn's contentPadding (not a width cap) so the scrollable area, the
    // scrollbar and the pull-to-refresh gesture still span the whole window.
    BoxWithConstraints(Modifier.fillMaxSize()) {
    val wideInset = ((maxWidth - ContentWidth.List) / 2).coerceAtLeast(0.dp)
    PullToRefreshBox(
        isRefreshing = uiState.isLoading,
        onRefresh = onRefresh,
        state = pullState,
        modifier = Modifier.fillMaxSize(),
        indicator = {
            PullToRefreshDefaults.LoadingIndicator(
                state = pullState,
                isRefreshing = uiState.isLoading,
                modifier = Modifier.align(Alignment.TopCenter),
            )
        },
    ) {
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(start = wideInset, end = wideInset, top = 0.dp, bottom = 112.dp),
            verticalArrangement = Arrangement.spacedBy(SegmentedGap),
            modifier = Modifier
                .fillMaxSize()
                .onGloballyPositioned { coords ->
                    onTutorialBounds(TutorialTarget.LIST, coords.boundsInRoot())
                },
        ) {
            item(key = "demo-banner") {
                Box(Modifier.animateItem().padding(horizontal = ListGutter)) { demoBanner() }
            }
            sections.forEachIndexed { sectionIndex, section ->
                item(key = "section-${section.section.name}") {
                    SectionHeader(
                        text = stringResource(section.section.titleRes),
                        modifier = Modifier
                            .padding(horizontal = ListGutter)
                            .animateItem(
                                fadeInSpec = effects,
                                placementSpec = spatial,
                                fadeOutSpec = effects,
                            ),
                    )
                }
                itemsIndexed(
                    items = section.items,
                    key = { _, e -> e.id },
                ) { index, email ->
                    var rowModifier = Modifier.padding(horizontal = ListGutter).animateItem(
                        fadeInSpec = effects,
                        placementSpec = spatial,
                        fadeOutSpec = effects,
                    )
                    if (sectionIndex == 0 && index == 0) {
                        rowModifier = rowModifier.onGloballyPositioned { coords ->
                            onTutorialBounds(TutorialTarget.FIRST_MASK, coords.boundsInRoot())
                        }
                    }
                    MaskRow(
                        email = email,
                        index = index,
                        count = section.items.size,
                        nowSec = nowSec,
                        highlighted = highlightEmail != null && email.email == highlightEmail,
                        onOpen = onRowOpen,
                        onCopy = onRowCopy,
                        modifier = rowModifier,
                    )
                }
            }
        }
    }
    }
}
