package reactor

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import reactor.core.publisher.Mono
import reactor.netty.NettyOutbound
import reactor.netty.tcp.TcpClient
import java.nio.charset.StandardCharsets

/*
 * DISCLAIMER
 *
 * Copyright 2016 ArangoDB GmbH, Cologne, Germany
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Copyright holder is ArangoDB GmbH, Cologne, Germany
 */


/**
 * @author Michele Rastelli
 */

class EchoClient {
    lateinit var outbound: NettyOutbound

    fun send(message: String) {
        outbound
                .sendString(Mono.just(message))
                .then()
                .subscribe()
    }

    fun start() {
        TcpClient.create()
                .host("127.0.0.1")
                .port(9000)
                .handle { i, o ->
                    outbound = o
                    i.receive()
                            .asString(StandardCharsets.UTF_8)
                            .doOnNext(LOGGER::info)
                            .doOnError { LOGGER.error(it.message, it) }
                            .subscribe()

                    Mono.never()
                }
                .connectNow()
                .onDispose()
                .block();
    }

    companion object {
        val LOGGER: Logger = LoggerFactory.getLogger(EchoClient::class.java);
    }
}

fun main() {
    val c = EchoClient()
    Thread {
        c.start()
    }.start()

    Thread.sleep(1000)
    c.send("a")
    Thread.sleep(100)
    c.send("b")
    Thread.sleep(100)
    c.send("c")
    Thread.sleep(100)
    c.send("d")
}