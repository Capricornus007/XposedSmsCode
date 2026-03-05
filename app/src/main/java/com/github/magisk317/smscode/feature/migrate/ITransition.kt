package com.github.magisk317.smscode.feature.migrate

/**
 * Data migration transition interface
 */
interface ITransition {
    /**
     * Whether data migration is needed
     */
    suspend fun shouldTransit(): Boolean

    /**
     * Execute data migration logic
     * @return true if successful
     */
    suspend fun doTransition(): Boolean
}
