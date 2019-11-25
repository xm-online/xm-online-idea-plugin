package com.icthh.xm.domain.permission

import com.fasterxml.jackson.annotation.JsonIgnore
import java.util.*

data class Privilege(
    @field:JsonIgnore
    var msName: String = "",
    var key: String = ""
) : Comparable<Privilege> {

    var description: SortedMap<String, String> = TreeMap()
    var resources: SortedSet<String> = TreeSet()

    override fun compareTo(o: Privilege): Int {
        return key.compareTo(o.key)
    }
}
