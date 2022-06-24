/*
 * Copyright 2017 HugeGraph Authors
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with this
 * work for additional information regarding copyright ownership. The ASF
 * licenses this file to You under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package com.baidu.hugegraph.api.space;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.inject.Singleton;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;

import com.baidu.hugegraph.HugeException;
import com.baidu.hugegraph.api.API;
import com.baidu.hugegraph.backend.store.hstore.HstoreOptions;
import com.baidu.hugegraph.config.HugeConfig;
import com.baidu.hugegraph.pd.client.PDClient;
import com.baidu.hugegraph.pd.client.PDConfig;
import com.baidu.hugegraph.pd.common.PDException;
import com.baidu.hugegraph.pd.grpc.Metapb;
import com.baidu.hugegraph.pd.grpc.Pdpb;
import com.baidu.hugegraph.server.RestServer;
import com.baidu.hugegraph.util.E;
import com.baidu.hugegraph.util.Log;
import com.codahale.metrics.annotation.Timed;
import com.google.common.collect.ImmutableMap;

@Path("pd")
@Singleton
public class PDAPI extends API {
    private static final Logger LOG = Log.logger(RestServer.class);
    private PDClient client;

    protected synchronized PDClient client(HugeConfig config) {
        if (this.client != null) {
            return this.client;
        }

        String pdPeers = config.get(HstoreOptions.PD_PEERS);

        E.checkArgument(StringUtils.isNotEmpty(pdPeers),
                        "Please set pd addrs use config: pd.peers");

        this.client = PDClient.create(PDConfig.of(pdPeers)
                                              .setEnablePDNotify(true));

        E.checkArgument(client != null,
                        "Get pd client error, The hstore api is not enable.");

        return this.client;
    }

    @GET
    @Timed
    @Produces(APPLICATION_JSON_WITH_CHARSET)
    public Object list(@Context HugeConfig config) {
        Pdpb.GetMembersResponse membersResponse = null;
        try {
            membersResponse = this.client(config).getMembers();
        } catch (PDException e) {
            throw new HugeException("Get PD members error", e);
        }

        List<Map<String, Object>> members = new ArrayList<>();

        for (int i = 0; i < membersResponse.getMembersCount(); i++) {
            Metapb.Member m = membersResponse.getMembers(i);
            m.getRaftUrl();

            Map<String, Object> memberInfo = new HashMap<>();
            memberInfo.put("ip", m.getGrpcUrl());
            memberInfo.put("state", m.getState().name());
            memberInfo.put("is_leader", membersResponse.getLeader().getGrpcUrl()
                                                       .equals(m.getGrpcUrl()));

            members.add(memberInfo);
        }

        return ImmutableMap.of("members", members);
    }
}
