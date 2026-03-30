package com.gpxvideo.feature.timeline

import java.util.UUID

sealed class TimelineAction {
    abstract fun execute(state: TimelineState): TimelineState
    abstract fun reverse(state: TimelineState): TimelineState

    data class AddClip(val clip: TimelineClipState, val trackId: UUID) : TimelineAction() {
        override fun execute(state: TimelineState): TimelineState {
            return state.copy(
                tracks = state.tracks.map { track ->
                    if (track.id == trackId) {
                        track.copy(clips = (track.clips + clip).sortedBy { it.startTimeMs })
                    } else track
                }
            ).recalculateDuration()
        }

        override fun reverse(state: TimelineState): TimelineState {
            return state.copy(
                tracks = state.tracks.map { track ->
                    if (track.id == trackId) {
                        track.copy(clips = track.clips.filter { it.id != clip.id })
                    } else track
                }
            ).recalculateDuration()
        }
    }

    data class RemoveClip(val clip: TimelineClipState, val trackId: UUID) : TimelineAction() {
        override fun execute(state: TimelineState): TimelineState {
            return state.copy(
                tracks = state.tracks.map { track ->
                    if (track.id == trackId) {
                        track.copy(clips = track.clips.filter { it.id != clip.id })
                    } else track
                },
                selectedClipId = if (state.selectedClipId == clip.id) null else state.selectedClipId
            ).recalculateDuration()
        }

        override fun reverse(state: TimelineState): TimelineState {
            return state.copy(
                tracks = state.tracks.map { track ->
                    if (track.id == trackId) {
                        track.copy(clips = (track.clips + clip).sortedBy { it.startTimeMs })
                    } else track
                }
            ).recalculateDuration()
        }
    }

    data class MoveClip(
        val clipId: UUID,
        val trackId: UUID,
        val oldStartMs: Long,
        val oldEndMs: Long,
        val newStartMs: Long,
        val newEndMs: Long
    ) : TimelineAction() {
        override fun execute(state: TimelineState): TimelineState {
            return state.updateClip(clipId) {
                it.copy(startTimeMs = newStartMs, endTimeMs = newEndMs)
            }.recalculateDuration()
        }

        override fun reverse(state: TimelineState): TimelineState {
            return state.updateClip(clipId) {
                it.copy(startTimeMs = oldStartMs, endTimeMs = oldEndMs)
            }.recalculateDuration()
        }
    }

    data class TrimClip(
        val clipId: UUID,
        val oldStart: Long,
        val oldEnd: Long,
        val oldTrimStart: Long,
        val oldTrimEnd: Long,
        val newStart: Long,
        val newEnd: Long,
        val newTrimStart: Long,
        val newTrimEnd: Long
    ) : TimelineAction() {
        override fun execute(state: TimelineState): TimelineState {
            return state.updateClip(clipId) {
                it.copy(
                    startTimeMs = newStart,
                    endTimeMs = newEnd,
                    trimStartMs = newTrimStart,
                    trimEndMs = newTrimEnd
                )
            }.recalculateDuration()
        }

        override fun reverse(state: TimelineState): TimelineState {
            return state.updateClip(clipId) {
                it.copy(
                    startTimeMs = oldStart,
                    endTimeMs = oldEnd,
                    trimStartMs = oldTrimStart,
                    trimEndMs = oldTrimEnd
                )
            }.recalculateDuration()
        }
    }

    data class SplitClip(
        val originalClip: TimelineClipState,
        val firstClip: TimelineClipState,
        val secondClip: TimelineClipState,
        val trackId: UUID
    ) : TimelineAction() {
        override fun execute(state: TimelineState): TimelineState {
            return state.copy(
                tracks = state.tracks.map { track ->
                    if (track.id == trackId) {
                        val withoutOriginal = track.clips.filter { it.id != originalClip.id }
                        track.copy(
                            clips = (withoutOriginal + firstClip + secondClip)
                                .sortedBy { it.startTimeMs }
                        )
                    } else track
                },
                selectedClipId = null
            ).recalculateDuration()
        }

        override fun reverse(state: TimelineState): TimelineState {
            return state.copy(
                tracks = state.tracks.map { track ->
                    if (track.id == trackId) {
                        val withoutSplit = track.clips.filter {
                            it.id != firstClip.id && it.id != secondClip.id
                        }
                        track.copy(
                            clips = (withoutSplit + originalClip).sortedBy { it.startTimeMs }
                        )
                    } else track
                }
            ).recalculateDuration()
        }
    }

    data class AddTrack(val track: TimelineTrackState) : TimelineAction() {
        override fun execute(state: TimelineState): TimelineState {
            return state.copy(
                tracks = (state.tracks + track).sortedBy { it.order }
            )
        }

        override fun reverse(state: TimelineState): TimelineState {
            return state.copy(
                tracks = state.tracks.filter { it.id != track.id },
                selectedTrackId = if (state.selectedTrackId == track.id) null else state.selectedTrackId
            )
        }
    }

    data class RemoveTrack(val track: TimelineTrackState) : TimelineAction() {
        override fun execute(state: TimelineState): TimelineState {
            return state.copy(
                tracks = state.tracks.filter { it.id != track.id },
                selectedTrackId = if (state.selectedTrackId == track.id) null else state.selectedTrackId
            ).recalculateDuration()
        }

        override fun reverse(state: TimelineState): TimelineState {
            return state.copy(
                tracks = (state.tracks + track).sortedBy { it.order }
            ).recalculateDuration()
        }
    }

    data class ReorderTrack(
        val trackId: UUID,
        val oldOrder: Int,
        val newOrder: Int,
        val previousOrders: Map<UUID, Int>
    ) : TimelineAction() {
        override fun execute(state: TimelineState): TimelineState {
            val reordered = state.tracks.map { track ->
                when {
                    track.id == trackId -> track.copy(order = newOrder)
                    newOrder < oldOrder && track.order in newOrder until oldOrder ->
                        track.copy(order = track.order + 1)
                    newOrder > oldOrder && track.order in (oldOrder + 1)..newOrder ->
                        track.copy(order = track.order - 1)
                    else -> track
                }
            }.sortedBy { it.order }
            return state.copy(tracks = reordered)
        }

        override fun reverse(state: TimelineState): TimelineState {
            return state.copy(
                tracks = state.tracks.map { track ->
                    track.copy(order = previousOrders[track.id] ?: track.order)
                }.sortedBy { it.order }
            )
        }
    }

    data class ToggleTrackVisibility(val trackId: UUID) : TimelineAction() {
        override fun execute(state: TimelineState): TimelineState {
            return state.copy(
                tracks = state.tracks.map { track ->
                    if (track.id == trackId) track.copy(isVisible = !track.isVisible) else track
                }
            )
        }

        override fun reverse(state: TimelineState): TimelineState = execute(state)
    }

    data class ToggleTrackLock(val trackId: UUID) : TimelineAction() {
        override fun execute(state: TimelineState): TimelineState {
            return state.copy(
                tracks = state.tracks.map { track ->
                    if (track.id == trackId) track.copy(isLocked = !track.isLocked) else track
                }
            )
        }

        override fun reverse(state: TimelineState): TimelineState = execute(state)
    }
}

private fun TimelineState.updateClip(
    clipId: UUID,
    transform: (TimelineClipState) -> TimelineClipState
): TimelineState {
    return copy(
        tracks = tracks.map { track ->
            track.copy(
                clips = track.clips.map { clip ->
                    if (clip.id == clipId) transform(clip) else clip
                }.sortedBy { it.startTimeMs }
            )
        }
    )
}

private fun TimelineState.recalculateDuration(): TimelineState {
    val maxEnd = tracks.flatMap { it.clips }.maxOfOrNull { it.endTimeMs } ?: 0L
    return copy(totalDurationMs = maxEnd)
}

class UndoManager(private val maxHistory: Int = 50) {
    private val undoStack = ArrayDeque<TimelineAction>()
    private val redoStack = ArrayDeque<TimelineAction>()

    fun execute(action: TimelineAction, state: TimelineState): TimelineState {
        undoStack.addLast(action)
        if (undoStack.size > maxHistory) {
            undoStack.removeFirst()
        }
        redoStack.clear()
        return action.execute(state)
    }

    fun undo(state: TimelineState): TimelineState? {
        val action = undoStack.removeLastOrNull() ?: return null
        redoStack.addLast(action)
        return action.reverse(state)
    }

    fun redo(state: TimelineState): TimelineState? {
        val action = redoStack.removeLastOrNull() ?: return null
        undoStack.addLast(action)
        return action.execute(state)
    }

    val canUndo: Boolean get() = undoStack.isNotEmpty()
    val canRedo: Boolean get() = redoStack.isNotEmpty()
}
