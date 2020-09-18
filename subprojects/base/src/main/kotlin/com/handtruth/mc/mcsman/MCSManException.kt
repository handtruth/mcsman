package com.handtruth.mc.mcsman

open class MCSManException : RuntimeException {
    constructor() : super()
    constructor(message: String) : super(message)
    constructor(message: String, cause: Throwable) : super(message, cause)
    constructor(cause: Throwable) : super(cause)
}

class AlreadyExistsMCSManException(message: String) : MCSManException(message)
class NotExistsMCSManException(message: String) : MCSManException(message)
class AlreadyInStateMCSManException(message: String) : MCSManException(message)
class AuthenticationMCSManException(message: String) : MCSManException(message)
class AccessDeniedMCSManException(message: String) : MCSManException(message) {
    constructor(obj: String, subject: Any, permission: String) :
            this("access to \"$obj\" required permission \"$permission\" for \"$subject\"")
}
