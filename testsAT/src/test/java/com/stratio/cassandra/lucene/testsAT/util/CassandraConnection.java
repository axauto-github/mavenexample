/*
 * Copyright (C) 2014 Stratio (http://stratio.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.stratio.cassandra.lucene.testsAT.util;

import com.datastax.driver.core.*;
import com.stratio.cassandra.lucene.testsAT.BaseAT;
import org.slf4j.Logger;

import javax.management.JMException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static com.stratio.cassandra.lucene.testsAT.util.CassandraConfig.*;

/**
 * @author Andres de la Pena {@literal <adelapena@stratio.com>}
 */
public class CassandraConnection {

    private static final Logger logger = BaseAT.logger;

    private static Cluster cluster;
    private static Session session;
    private static List<CassandraJMXClient> jmxClients;

    public static void connect() {
        if (cluster == null) {
            try {

                cluster = Cluster.builder().addContactPoint(HOST).build();

                cluster.getConfiguration().getQueryOptions().setConsistencyLevel(CONSISTENCY).setFetchSize(FETCH);
                cluster.getConfiguration()
                       .getSocketOptions()
                       .setReadTimeoutMillis(60000)
                       .setConnectTimeoutMillis(100000);

                session = cluster.connect();

                jmxClients = new ArrayList<>(JMX_SERVICES.length);
                for (String service : JMX_SERVICES) {
                    jmxClients.add(new CassandraJMXClient(service).connect());
                }

            } catch (Exception e) {
                throw new RuntimeException("Error while connecting to Cassandra server", e);
            }
        }
    }

    public static void disconnect() {
        session.close();
        cluster.close();
        jmxClients.forEach(CassandraJMXClient::disconnect);
    }

    public static synchronized ResultSet execute(Statement statement) {
        logger.debug("CQL: {}", statement);
        return session.execute(statement);
    }

    static PreparedStatement prepare(String query) {
        return session.prepare(query);
    }

    static List<Object> getJMXAttribute(String bean, String attribute) {
        try {
            List<Object> out = new ArrayList<>(jmxClients.size());
            for (CassandraJMXClient client : jmxClients) {
                out.add(client.getAttribute(bean, attribute));
            }
            return out;
        } catch (JMException | IOException e) {
            throw new RuntimeException(String.format("Error while reading JMX attribute %s.%s", bean, attribute), e);
        }
    }

    static void invokeJMXMethod(String bean, String operation, Object[] params, String[] signature) {
        try {
            for (CassandraJMXClient client : jmxClients) {
                client.invoke(bean, operation, params, signature);
            }
        } catch (JMException | IOException e) {
            throw new RuntimeException(String.format("Error while invoking JMX method %s", operation), e);
        }
    }

}
