package com.ryccoatika.contactmanager.ui.common

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp

/** A single shimmering placeholder block; compose these into screen skeletons. */
@Composable
fun ShimmerBox(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(8.dp),
) {
    val scheme = MaterialTheme.colorScheme
    val base = scheme.surfaceVariant
    val highlight = if (scheme.surface.luminance() > 0.5f) Color.White else lerp(base, Color.White, 0.16f)
    val transition = rememberInfiniteTransition(label = "shimmer")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1300, easing = LinearEasing)),
        label = "shimmer-progress",
    )
    Box(
        modifier
            .clip(shape)
            // The gradient is built once per size (drawWithCache), then slid across by
            // translating the draw. Rebuilding the Brush per frame instead would allocate
            // a fresh native shader on every frame of every box — ShaderBrush caches its
            // shader per instance, so a new instance defeats the cache — and a screenful
            // of skeleton rows at 120Hz churns through enough of them to OOM.
            .drawWithCache {
                val sweep = size.width * 1.4f
                val band =
                    Brush.linearGradient(
                        colors = listOf(base, highlight, base),
                        start = Offset.Zero,
                        end = Offset(sweep, 0f),
                    )
                val bandSize = Size(sweep, size.height)
                onDrawBehind {
                    drawRect(base)
                    translate(left = progress * (size.width + sweep) - sweep) {
                        drawRect(band, size = bandSize)
                    }
                }
            },
    )
}

/** Skeleton for the contact list (Home). */
@Composable
fun ContactListSkeleton(modifier: Modifier = Modifier, rows: Int = 9) {
    Column(modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        repeat(rows) {
            Row(
                Modifier.fillMaxWidth().height(64.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ShimmerBox(Modifier.size(44.dp), CircleShape)
                Spacer(Modifier.width(13.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    ShimmerBox(Modifier.fillMaxWidth(0.5f).height(14.dp))
                    ShimmerBox(Modifier.fillMaxWidth(0.32f).height(11.dp))
                }
            }
        }
    }
}

/** Skeleton of rounded card blocks (Accounts, Editor, Duplicates). */
@Composable
fun CardsSkeleton(
    modifier: Modifier = Modifier,
    count: Int = 4,
    height: androidx.compose.ui.unit.Dp = 76.dp,
) {
    Column(
        modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        repeat(count) {
            ShimmerBox(Modifier.fillMaxWidth().height(height), MaterialTheme.shapes.medium)
        }
    }
}

/** Skeleton for the contact detail (hero + account cards). */
@Composable
fun DetailSkeleton(modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().padding(16.dp)) {
        Column(
            Modifier.fillMaxWidth().padding(vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            ShimmerBox(Modifier.size(88.dp), CircleShape)
            Spacer(Modifier.height(12.dp))
            ShimmerBox(Modifier.fillMaxWidth(0.5f).height(20.dp))
            Spacer(Modifier.height(8.dp))
            ShimmerBox(Modifier.fillMaxWidth(0.3f).height(12.dp))
            Spacer(Modifier.height(18.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                repeat(3) { ShimmerBox(Modifier.weight(1f).height(56.dp), MaterialTheme.shapes.medium) }
            }
        }
        Spacer(Modifier.height(16.dp))
        repeat(2) {
            ShimmerBox(Modifier.fillMaxWidth().height(120.dp), MaterialTheme.shapes.medium)
            Spacer(Modifier.height(12.dp))
        }
    }
}
