package com.handtruth.mc.mcsman.server.session

import com.handtruth.mc.mcsman.server.util.PasswordHashStrategy

class NonePasswordHashStrategy : PasswordHashStrategy {
    override fun hash(password: String) = password
    override fun verify(data: String, password: String) = data == password
    override fun toString() = "HashStrategy(none)"
}
