package com.devicehive.application;

/*
 * #%L
 * DeviceHive Dao Riak Implementation
 * %%
 * Copyright (C) 2016 DataArt
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */
import com.basho.riak.client.api.RiakClient;
import com.basho.riak.client.api.cap.Quorum;
import com.basho.riak.client.api.commands.kv.FetchValue;
import com.basho.riak.client.api.commands.kv.StoreValue;
import com.basho.riak.client.core.RiakCluster;
import com.basho.riak.client.core.RiakNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;

import javax.annotation.PostConstruct;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Map;

@Configuration
@EnableAutoConfiguration(exclude = {JacksonAutoConfiguration.class,
    DataSourceAutoConfiguration.class, HibernateJpaAutoConfiguration.class,
    DataSourceTransactionManagerAutoConfiguration.class})
@PropertySource("classpath:application-persistence.properties")
public class RiakClusterConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(RiakClusterConfiguration.class);

    private RiakCluster cluster;

    @Autowired
    private Environment env;

    @PostConstruct
    private void init() throws UnknownHostException {
        logger.debug("RiakClusterBuilder initialization started.");

        String riakHost = env.getProperty("riak.host");
        int riakPort = Integer.parseInt(env.getProperty("riak.port"));

        // This example will use only one node listening on localhost:10017
        RiakNode node = new RiakNode.Builder()
                .withRemoteAddress(riakHost)
                .withRemotePort(riakPort)
                .build();

        // This cluster object takes our one node as an argument
        cluster = new RiakCluster.Builder(node).build();

        // The cluster must be started to work, otherwise you will see errors
        cluster.start();

        logger.debug("RiakClusterBuilder initialization finished.");
    }

    @Bean
    @Lazy(false)
    public RiakClient riakClient() {
        return new RiakClient(cluster);
    }

    @Bean
    @Lazy(false)
    public RiakCluster riakCluster() {
        return cluster;
    }

    @Bean
    @Lazy(false)
    public RiakQuorum riakQuorum(@Value("${riak.read-quorum-option:r}") String rqOpt,
            @Value("${riak.read-quorum:default}") String rq,
            @Value("${riak.write-quorum.option:w}") String wqOpt,
            @Value("${riak.write-quorum:default}") String wq) {
        Map<String, FetchValue.Option<Quorum>> readOptions = new HashMap<String, FetchValue.Option<Quorum>>() {
            {
                put("r", FetchValue.Option.R);
                put("pr", FetchValue.Option.PR);
            }
        };
        Map<String, StoreValue.Option<Quorum>> writeOptions = new HashMap<String, StoreValue.Option<Quorum>>() {
            {
                put("w", StoreValue.Option.W);
                put("pw", StoreValue.Option.PW);
                put("dw", StoreValue.Option.DW);
            }
        };
        Map<String, Quorum> quorums = new HashMap<String, Quorum>() {
            {
                put("one", Quorum.oneQuorum());
                put("all", Quorum.allQuorum());
                put("quorum", Quorum.quorumQuorum());
                put("default", Quorum.defaultQuorum());
            }
        };

        FetchValue.Option<Quorum> readQuorumOption = readOptions.get(rqOpt);
        Quorum readQuorum = quorums.get(rq);

        StoreValue.Option<Quorum> writeQuorumOption = writeOptions.get(wqOpt);
        Quorum writeQuorum = quorums.get(wq);

        return new RiakQuorum(readQuorumOption, readQuorum, writeQuorumOption, writeQuorum);
    }
}
