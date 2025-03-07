package cn.solarmoon.spark_core.util

fun <T> MutableList<T>.toObservable(
    onAdd: (T) -> Unit = {},
    onRemove: (T) -> Unit = {}
): MutableList<T> = object : MutableList<T> by this {
    override fun add(element: T): Boolean {
        val success = this@toObservable.add(element)
        if (success) onAdd(element)
        return success
    }

    override fun add(index: Int, element: T) {
        this@toObservable.add(index, element)
        onAdd(element)
    }

    override fun addAll(elements: Collection<T>): Boolean {
        val success = this@toObservable.addAll(elements)
        if (success) elements.forEach(onAdd)
        return success
    }

    override fun remove(element: T): Boolean {
        val success = this@toObservable.remove(element)
        if (success) onRemove(element)
        return success
    }

    override fun removeAt(index: Int): T {
        val item = this@toObservable.removeAt(index)
        onRemove(item)
        return item
    }

    override fun clear() {
        val items = this@toObservable.toList()
        this@toObservable.clear()
        items.forEach(onRemove)
    }
}