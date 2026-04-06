package top.phj233.smartdatainsightagent.util

import org.slf4j.MDC

/**
 * 在执行 block 期间临时设置 MDC 上下文，执行完成后恢复之前的值。
 * @param entries 要设置的 MDC 键值对，值为 null 表示移除该键。
 * @param block 要执行的代码块
 * @return block 的返回值
 * @author phj233
 * @since 2026/4/6 17:00
 * @version
 */
inline fun <T> withMdc(vararg entries: Pair<String, String?>, block: () -> T): T {
    val previousValues = applyMdcEntries(entries)
    try {
        return block()
    } finally {
        restoreMdcEntries(previousValues)
    }
}

/**
 * suspend 版本的 withMdc，适用于协程中的 MDC 上下文管理。
 * 注意：MDC 在协程中并不是自动传播的，因此需要在每个挂起点手动管理 MDC 上下文。
 * @param entries 要设置的 MDC 键值对，值为 null 表示移除该键。
 * @param block 要执行的 suspend 代码块
 * @return block 的返回值
 */
suspend inline fun <T> withMdcSuspend(vararg entries: Pair<String, String?>, crossinline block: suspend () -> T): T {
    val previousValues = applyMdcEntries(entries)
    try {
        return block()
    } finally {
        restoreMdcEntries(previousValues)
    }
}

/**
 * 应用 MDC 条目并返回之前的值，以便后续恢复。
 * @param entries 要设置的 MDC 键值对，值为 null 表示移除该键。
 * @return 之前的 MDC 值映射
 * @author phj233
 * @since 2026/4/6 17:05
 * @version
 */
@PublishedApi
internal fun applyMdcEntries(entries: Array<out Pair<String, String?>>): HashMap<String, String?> {
    val previousValues = HashMap<String, String?>(entries.size)
    for ((key, value) in entries) {
        previousValues[key] = MDC.get(key)
        if (value == null) {
            MDC.remove(key)
        } else {
            MDC.put(key, value)
        }
    }
    return previousValues
}
/**
 * 恢复之前的 MDC 条目值。
 * @param previousValues 之前的 MDC 值映射
 * @author phj233
 * @since 2026/4/6 17:05
 * @version
 */
@PublishedApi
internal fun restoreMdcEntries(previousValues: Map<String, String?>) {
    for ((key, previousValue) in previousValues) {
        if (previousValue == null) {
            MDC.remove(key)
        } else {
            MDC.put(key, previousValue)
        }
    }
}

/**
 * 临时替换整个 MDC 上下文，执行 block 后恢复之前的上下文。
 * @param contextMap 要设置的 MDC 上下文映射，null 表示清空 MDC。
 * @param block 要执行的代码块
 * @return block 的返回值
 * @author phj233
 * @since 2026/4/6 17:10
 * @version
 */
inline fun <T> withMdcContext(contextMap: Map<String, String>?, block: () -> T): T {
    val previous = MDC.getCopyOfContextMap()
    if (contextMap == null) {
        MDC.clear()
    } else {
        MDC.setContextMap(contextMap)
    }
    try {
        return block()
    } finally {
        if (previous == null) {
            MDC.clear()
        } else {
            MDC.setContextMap(previous)
        }
    }
}

