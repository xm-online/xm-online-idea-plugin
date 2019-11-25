package com.icthh.xm.domain.permission

import com.fasterxml.jackson.annotation.JsonIgnore

class Role(
    @field:JsonIgnore
    var key: String = ""
)  : Comparable<Role> {


    var description: String? = null
    var createdDate: String? = null
    var createdBy: String? = null
    var updatedDate: String? = null
    var updatedBy: String? = null

    override fun compareTo(o: Role): Int {
        return key.compareTo(o.key)
    }
}
