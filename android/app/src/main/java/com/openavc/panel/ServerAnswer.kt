package com.openavc.panel

/**
 * Which HTTP errors on the panel's own page the app covers with its
 * unreachable overlay.
 */
object ServerAnswer {
    /**
     * The server answers every page with 503 while it starts and after a
     * start that failed: its own page, which says why, counts down and offers
     * Start again, and reloads onto the panel once the server is ready. That
     * page is shown as it is, as a browser shows it.
     */
    private const val STARTING = 503

    fun coveredByOverlay(statusCode: Int): Boolean = statusCode != STARTING
}
