package com.example.fineweather.viewmodels

/** Why a value that loaded fine has nothing to show. */
enum class Reason { NO_DATA_YET, NOT_AVAILABLE }

enum class FailKind { RATE_LIMITED, NETWORK, OTHER }

/** One piece of data on its way to the screen. */
sealed interface Loadable<out T> {
    /** Nothing was requested yet. */
    data object Idle : Loadable<Nothing>

    data object Loading : Loadable<Nothing>

    data class Ready<T>(val value: T) : Loadable<T>

    data class Unavailable(val reason: Reason) : Loadable<Nothing>

    data class Failed(val kind: FailKind) : Loadable<Nothing>
}

fun <T> Loadable<T>.valueOrNull(): T? = (this as? Loadable.Ready)?.value

/** A state without a value fits any [Loadable] type; only call this when this is not [Loadable.Ready]. */
@Suppress("UNCHECKED_CAST")
internal fun <R> Loadable<*>.carryOver(): Loadable<R> {
    check(this !is Loadable.Ready) { "A value cannot be carried over to another type" }
    return this as Loadable<R>
}

fun <T, R> Loadable<T>.map(transform: (T) -> R): Loadable<R> =
    when (this) {
        is Loadable.Ready -> Loadable.Ready(transform(value))
        else -> carryOver()
    }

/** Ready once both are; otherwise still loading if either is, else whichever has no value. */
fun <A, B, R> combineLoadables(
    a: Loadable<A>,
    b: Loadable<B>,
    transform: (A, B) -> R,
): Loadable<R> =
    when {
        a is Loadable.Ready && b is Loadable.Ready -> Loadable.Ready(transform(a.value, b.value))
        a is Loadable.Loading || b is Loadable.Loading -> Loadable.Loading
        a !is Loadable.Ready -> a.carryOver()
        else -> b.carryOver()
    }

/** A resolved place for the status row beneath search. */
data class PlaceLabel(
    val name: String,
    val coordinates: String,
    val locationDetails: String = "",
)

/** The one-line status under the search field. */
sealed interface SearchStatus {
    data object Idle : SearchStatus

    data class Searching(val query: String) : SearchStatus

    data class Resolved(val label: PlaceLabel) : SearchStatus

    data class NotFound(val query: String) : SearchStatus
}
