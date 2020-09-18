package com.handtruth.mc.mcsman.server.event

import com.handtruth.mc.mcsman.event.Event

interface Veto<in E : Event> {
    suspend fun impose(event: E): Answer

    enum class Answer {
        Unknown, Allow, Deny
    }

    companion object {
        suspend fun <E : Event> compose(event: E, vetoes: Iterator<Veto<E>>): Answer {
            var current = Answer.Unknown
            for (veto in vetoes) {
                when (veto.impose(event)) {
                    Answer.Unknown -> {}
                    Answer.Allow -> current = Answer.Allow
                    Answer.Deny -> return Answer.Deny
                }
            }
            return current
        }

        suspend inline fun <E : Event> compose(event: E, vararg vetoes: Veto<E>): Answer {
            return compose(event, vetoes.iterator())
        }

        suspend inline fun <E : Event> compose(event: E, vetoes: List<Veto<E>>): Answer {
            return compose(event, vetoes.iterator())
        }

        inline fun allowing(action: () -> Boolean): Answer {
            return if (action())
                Answer.Allow
            else
                Answer.Unknown
        }

        inline fun denying(action: () -> Boolean): Answer {
            return if (action())
                Answer.Deny
            else
                Answer.Unknown
        }
    }
}
