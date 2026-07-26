package com.reversetutor.feature.chat

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect

internal class DragBoundsRegistry<K> {
    private val bounds = linkedMapOf<K, Rect>()

    fun update(id: K, rect: Rect) {
        bounds[id] = rect
    }

    fun boundsOf(id: K): Rect? = bounds[id]

    fun remove(id: K) {
        bounds.remove(id)
    }

    fun retainOnly(validIds: Set<K>) {
        bounds.keys.retainAll(validIds)
    }

    fun hitTest(pointer: Offset, validTargetsInPriorityOrder: Iterable<K>, excluded: Set<K> = emptySet()): K? =
        validTargetsInPriorityOrder.firstOrNull { target ->
            target !in excluded && bounds[target]?.contains(pointer) == true
        }

    fun trackedIds(): Set<K> = bounds.keys.toSet()
}
