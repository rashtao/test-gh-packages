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


package com.arangodb.next.communication;

import com.arangodb.next.connection.*;
import deployments.ContainerDeployment;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import reactor.core.Exceptions;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * @author Michele Rastelli
 */
@Testcontainers
class CommunicationActiveFailoverTest {

    private final ImmutableCommunicationConfig.Builder config;
    private final List<HostDescription> hosts;

    @Container
    private final static ContainerDeployment deployment = ContainerDeployment.ofActiveFailover(3);

    CommunicationActiveFailoverTest() {
        hosts = deployment.getHosts();
        config = CommunicationConfig.builder()
                .topology(ArangoTopology.ACTIVE_FAILOVER)
                .addAllHosts(hosts)
                .authenticationMethod(deployment.getAuthentication());
    }

    @ParameterizedTest
    @EnumSource(ArangoProtocol.class)
    void create(ArangoProtocol protocol) {
        ArangoCommunication communication = ArangoCommunication.create(config
                .protocol(protocol)
                .build()).block();
        assertThat(communication).isNotNull();

        ArangoCommunicationImpl communicationImpl = ((ArangoCommunicationImpl) communication);
        ActiveFailoverConnectionPool connectionPool = (ActiveFailoverConnectionPool) communicationImpl.getConnectionPool();
        Map<HostDescription, List<ArangoConnection>> connectionsByHost = connectionPool.getConnectionsByHost();
        HostDescription[] expectedKeys = hosts.toArray(new HostDescription[0]);
        assertThat(connectionsByHost)
                .containsKeys(expectedKeys)
                .containsOnlyKeys(expectedKeys);

        HostDescription leader = connectionPool.getLeader();
        assertThat(hosts).contains(leader);

        // check if every connection is connected
        connectionsByHost.forEach((key, value) -> value.forEach(connection -> {
            assertThat(connection.isConnected().block()).isTrue();
            ConnectionTestUtils.performRequest(connection);
        }));

        CommunicationTestUtils.executeRequest(communication);
        communication.close().block();
    }

    @ParameterizedTest
    @EnumSource(ArangoProtocol.class)
    void acquireHostList(ArangoProtocol protocol) {

        ArangoCommunication communication = ArangoCommunication.create(config
                .protocol(protocol)
                .hosts(hosts.subList(0, 1))
                .build()).block();
        assertThat(communication).isNotNull();

        ArangoCommunicationImpl communicationImpl = ((ArangoCommunicationImpl) communication);
        ActiveFailoverConnectionPool connectionPool = (ActiveFailoverConnectionPool) communicationImpl.getConnectionPool();
        Map<HostDescription, List<ArangoConnection>> currentHosts = connectionPool.getConnectionsByHost();
        assertThat(currentHosts).hasSize(hosts.size());
        assertThat(currentHosts.keySet()).containsExactlyInAnyOrderElementsOf(hosts);

        HostDescription leader = connectionPool.getLeader();
        assertThat(hosts).contains(leader);

        // check if every connection is connected
        currentHosts.forEach((key, value) -> value.forEach(connection -> {
            assertThat(connection.isConnected().block()).isTrue();
        }));

        CommunicationTestUtils.executeRequest(communication);
        communication.close().block();
    }

    @ParameterizedTest
    @EnumSource(ArangoProtocol.class)
    void wrongHostConnectionFailure(ArangoProtocol protocol) {
        Throwable thrown = catchThrowable(() -> ArangoCommunication.create(config
                .protocol(protocol)
                .hosts(Collections.singleton(HostDescription.of("wrongHost", 8529)))
                .build()).block());
        assertThat(Exceptions.unwrap(thrown)).isInstanceOf(IOException.class);
    }

}