package com.ryccoatika.contactmanager.data

/** Deterministic StringProvider for tests: encodes the resource id + args. */
class FakeStringProvider : StringProvider {
    override fun get(id: Int, vararg args: Any): String =
        if (args.isEmpty()) "#$id" else "#$id(${args.joinToString(",")})"

    override fun getQuantity(id: Int, count: Int, vararg args: Any): String =
        "#$id[$count]" + if (args.isEmpty()) "" else "(${args.joinToString(",")})"
}
