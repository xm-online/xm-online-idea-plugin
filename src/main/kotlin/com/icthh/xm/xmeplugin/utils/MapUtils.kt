package com.icthh.xm.xmeplugin.utils

fun Any?.keyItem(key: String): Any? {
    return if (this is Map<*, *>) {
        this[key]
    } else {
        null
    }
}

fun Any?.eachItem(index: Int, operation: (Any?) -> Unit): Unit {
    if (this is Iterable<*>) {
        this.forEachIndexed { i, item ->
            if (i == index) {
                operation(item)
            }
        }
    }
}

fun Any?.mapItem(operation: (Any?) -> Any?): Any? {
    return if (this is Iterable<*>) {
        this.map { operation(it) }
    } else {
        null
    }
}

fun Any?.flatMapItem(operation: (Any?) -> Iterable<Any?>?): Any? {
    return if (this is Iterable<*>) {
        this.flatMap { operation(it) ?: emptyList() }
    } else {
        null
    }
}

fun Any?.indexItem(index: Int): Any? {
    return if (this is List<*>) {
        this[index]
    } else {
        null
    }
}
