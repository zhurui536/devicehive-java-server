package com.devicehive.handler.command;

/*
 * #%L
 * DeviceHive Backend Logic
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

import com.devicehive.eventbus.EventBus;
import com.devicehive.model.DeviceCommand;
import com.devicehive.model.eventbus.Subscriber;
import com.devicehive.model.eventbus.Subscription;
import com.devicehive.model.rpc.Action;
import com.devicehive.model.rpc.CommandSubscribeRequest;
import com.devicehive.model.rpc.CommandSubscribeResponse;
import com.devicehive.service.HazelcastService;
import com.devicehive.shim.api.Request;
import com.devicehive.shim.api.Response;
import com.devicehive.shim.api.server.RequestHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;

import java.util.*;

public class CommandSubscribeRequestHandler implements RequestHandler {

    @Autowired
    private EventBus eventBus;

    @Autowired
    private HazelcastService hazelcastService;

    @Override
    public Response handle(Request request) {
        CommandSubscribeRequest body = (CommandSubscribeRequest) request.getBody();
        validate(body);

        Subscriber subscriber = new Subscriber(body.getSubscriptionId(), request.getReplyTo(), request.getCorrelationId());

        Set<Subscription> subscriptions = new HashSet<>();
        if (CollectionUtils.isEmpty(body.getNames())) {
            Subscription subscription = new Subscription(Action.COMMAND_EVENT.name(), body.getDevice());
            subscriptions.add(subscription);
        } else {
            for (String name : body.getNames()) {
                Subscription subscription = new Subscription(Action.COMMAND_EVENT.name(), body.getDevice(), name);
                subscriptions.add(subscription);
            }
        }

        subscriptions.forEach(subscription -> eventBus.subscribe(subscriber, subscription));

        Collection<DeviceCommand> commands = findCommands(body.getDevice(), body.getNames(), body.getTimestamp(), body.getLimit());
        CommandSubscribeResponse subscribeResponse = new CommandSubscribeResponse(body.getSubscriptionId(), commands);

        return Response.newBuilder()
                .withBody(subscribeResponse)
                .withLast(false)
                .withCorrelationId(request.getCorrelationId())
                .buildSuccess();
    }

    private void validate(CommandSubscribeRequest request) {
        Assert.notNull(request, "Request body is null");
        Assert.notNull(request.getDevice(), "Device deviceId is null");
        Assert.notNull(request.getSubscriptionId(), "Subscription id not provided");
    }

    private Collection<DeviceCommand> findCommands(String device, Collection<String> names, Date timestamp, Integer limit) {
        return Optional.ofNullable(timestamp)
                .map(t -> hazelcastService.find(null, names, Collections.singleton(device), limit, t, null, null, DeviceCommand.class))
                .orElse(Collections.emptyList());
    }
}
