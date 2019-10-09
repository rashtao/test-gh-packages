package reactor

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import reactor.core.publisher.Mono
import reactor.netty.tcp.TcpServer
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

class EchoServer {
    fun start() {
        TcpServer.create()
                .host("localhost")
                .port(9000)
                .handle { inbound, outbound ->
                    inbound.receive()
                            .asString(StandardCharsets.UTF_8)
                            .doOnNext(LOGGER::info)
                            .flatMap {
                                outbound
                                        .sendString(Mono.just(it))
                                        .neverComplete()
                            }
                }
                .bindNow().onDispose().block();
    }

    companion object {
        val LOGGER: Logger = LoggerFactory.getLogger(EchoServer::class.java)
    }
}

fun main() {
    EchoServer().start()
}