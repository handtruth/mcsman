package com.handtruth.mc.mcsman.protocol

enum class PaketID {
    Handshake,
    Authorization,
    NoOp,
    Error,
    EventStream,
    Stream,
    Extension,
    Bundle,
    Module,
    Event,
    Server,
    Service,
    Volume,
    Session,
    User,
    Group,
    GlobalAccess,
    ImageAccess,
    ServerAccess,
    VolumeAccess
}
