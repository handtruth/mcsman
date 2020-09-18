package com.handtruth.mc.mcsman.server

/**
 * Everything that marked with this annotation should be called inside database transaction scope. This scope can be
 * provided by [org.jetbrains.exposed.sql.transactions.transaction] function.
 *
 * If you tired of compiler warnings, you can enable this annotation globally in compiler arguments or use
 * [kotlin.OptIn] annotation before each transaction call.
 *
 * @sample
 *
 * ```kotlin
 * @OptIn(DbTransaction::class)
 * transaction(db) {
 *     ...
 * }
 * ```
 *
 * @see org.jetbrains.exposed.sql.transactions.transaction
 */
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY)
@RequiresOptIn("This function should be called inside a database transaction", RequiresOptIn.Level.WARNING)
@Retention(AnnotationRetention.BINARY)
@MustBeDocumented
annotation class TransactionContext

@Target(AnnotationTarget.FUNCTION)
@RequiresOptIn("This function should be called inside an agent context", RequiresOptIn.Level.WARNING)
@Retention(AnnotationRetention.BINARY)
@MustBeDocumented
annotation class AgentContext

@Target(AnnotationTarget.FUNCTION)
@RequiresOptIn("This function should be called only inside a reactor", RequiresOptIn.Level.WARNING)
@Retention(AnnotationRetention.BINARY)
@MustBeDocumented
annotation class ReactorContext

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
@MustBeDocumented
annotation class AgentCheck
