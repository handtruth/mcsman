package com.handtruth.mc.mcsman.server.docker

import com.handtruth.docker.DockerClient
import com.handtruth.kommon.Log
import com.handtruth.mc.mcsman.common.event.listen
import com.handtruth.mc.mcsman.event.Event
import com.handtruth.mc.mcsman.server.MCSManCore
import com.handtruth.mc.mcsman.server.event.Events
import com.handtruth.mc.mcsman.server.util.TaskBranch
import com.handtruth.mc.mcsman.util.forever
import io.ktor.network.selector.*
import io.ktor.utils.io.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.io.Closeable
import org.koin.core.inject
import kotlinx.coroutines.CancellationException as CancellationExceptionX

internal class Attachments : TaskBranch {

    override val coroutineContext = MCSManCore.fork("docker/attach", supervised = true)
    override val log = coroutineContext[Log]!!

    private val selector: SelectorManager by inject()
    private val docker: DockerClient by inject()
    private val events: Events by inject()

    interface Connection : Closeable

    private inner class ConnectionImpl(
        val containerId: String, val send: ReceiveChannel<String>,
        val input: SendChannel<String>, val output: SendChannel<String>, val errors: SendChannel<String>
    ) : Connection, CoroutineScope {
        override val coroutineContext = this@Attachments.fork(containerId, dispatcher = Dispatchers.Default)
        private val log = coroutineContext[Log]!!

        private fun attach(): Job = launch {
            val attachment = docker.containers.attachX(
                selector, coroutineContext, containerId, stream = true, stdin = true, stdout = true, stderr = true
            )
            log.verbose { "attached" }
            try {
                coroutineScope {
                    launch {
                        forever {
                            val value = send.receive()
                            attachment.input.writeStringUtf8(value)
                            attachment.input.writeChar('\n')
                            attachment.input.flush()
                        }
                    }
                    launch {
                        forever {
                            val value = attachment.stdin.readUTF8Line() ?: throw CancellationExceptionX()
                            input.send(value)
                        }
                    }
                    launch {
                        forever {
                            val value = attachment.stdout.readUTF8Line() ?: throw CancellationExceptionX()
                            output.send(value)
                        }
                    }
                    launch {
                        forever {
                            val value = attachment.stderr.readUTF8Line() ?: throw CancellationExceptionX()
                            errors.send(value)
                        }
                    }
                }
            } finally {
                log.verbose { "detached" }
            }
        }

        fun begin(immediate: Boolean, start: Event, stop: Event) {
            launch {
                log.verbose { "opened" }
                try {
                    var job = if (immediate) attach() else Job()
                    events.listen {
                        when (it) {
                            start -> {
                                job.cancel()
                                job = attach()
                            }
                            stop -> job.cancel()
                        }
                    }
                } finally {
                    log.verbose { "closed" }
                }
            }
        }

        override fun close() {
            cancel()
        }
    }

    fun attach(
        containerId: String, immediate: Boolean, start: Event, stop: Event, send: ReceiveChannel<String>,
        input: SendChannel<String>, output: SendChannel<String>, errors: SendChannel<String>
    ): Connection {
        val connection = ConnectionImpl(containerId, send, input, output, errors)
        connection.begin(immediate, start, stop)
        return connection
    }
}
