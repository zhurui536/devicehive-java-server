package com.devicehive.controller;

import java.util.Date;
import java.util.List;
import java.util.UUID;

import javax.annotation.security.RolesAllowed;
import javax.inject.Inject;
import javax.ws.rs.BadRequestException;
import javax.ws.rs.GET;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.SecurityContext;

import com.devicehive.auth.HivePrincipal;
import com.devicehive.dao.DeviceCommandDAO;
import com.devicehive.dao.DeviceDAO;
import com.devicehive.json.strategies.JsonPolicyApply;
import com.devicehive.json.strategies.JsonPolicyDef;
import com.devicehive.json.strategies.JsonPolicyDef.Policy;
import com.devicehive.messages.MessageDetails;
import com.devicehive.messages.MessageType;
import com.devicehive.messages.bus.DeferredResponse;
import com.devicehive.messages.bus.LocalMessageBus;
import com.devicehive.messages.bus.MessageBus;
import com.devicehive.messages.util.Params;
import com.devicehive.model.Device;
import com.devicehive.model.DeviceCommand;
import com.devicehive.model.User;

/**
 * REST controller for device commands: <i>/device/{deviceGuid}/command</i>.
 * See <a href="http://www.devicehive.com/restful#Reference/DeviceCommand">DeviceHive RESTful API: DeviceCommand</a> for details.
 */
@Path("/device/{deviceGuid}/command")
public class DeviceCommandController {

    @Inject
    private DeviceCommandDAO commandDAO;
    @Inject
    private DeviceDAO deviceDAO;
    @Inject
    private MessageBus messageBus;

    /**
     * Implementation of <a href="http://www.devicehive.com/restful#Reference/DeviceCommand/poll">DeviceHive RESTful API: DeviceCommand: poll</a>
     *
     * @param deviceGuid   Device unique identifier.
     * @param timestampUTC Timestamp of the last received command (UTC). If not specified, the server's timestamp is taken instead.
     * @param waitTimeout  Waiting timeout in seconds (default: 30 seconds, maximum: 60 seconds). Specify 0 to disable waiting.
     * @return Array of <a href="http://www.devicehive.com/restful#Reference/DeviceCommand">DeviceCommand</a>
     */
    @GET
    @RolesAllowed({"CLIENT", "DEVICE", "ADMIN"})
    @Path("/poll")
    @Produces(MediaType.APPLICATION_JSON)
    @JsonPolicyApply(Policy.COMMAND_TO_DEVICE)
    public List<DeviceCommand> poll(
            @PathParam("deviceGuid") String deviceGuid,
            @QueryParam("timestamp") String timestampUTC,
            @QueryParam("waitTimeout") String waitTimeout,
            @Context SecurityContext securityContext) {

        if (deviceGuid == null) {
            throw new BadRequestException("deviceGuid should be provided.");
        }

        Device device = deviceDAO.findByUUID(UUID.fromString(deviceGuid));
        if (device == null) {
            throw new NotFoundException("Device with guid = " + deviceGuid + " not found.");
        }

        Date timestamp = Params.parseUTCDate(timestampUTC);
        long timeout = Params.parseWaitTimeout(waitTimeout);

        User user = ((HivePrincipal) securityContext.getUserPrincipal()).getUser();
        DeferredResponse result = messageBus.subscribe(MessageType.CLIENT_TO_DEVICE_COMMAND,
                MessageDetails.create().ids(device.getId()).timestamp(timestamp).user(user));
        List<DeviceCommand> response = LocalMessageBus.expandDeferredResponse(result, timeout, DeviceCommand.class);

        return response;
    }

    /**
     * Implementation of <a href="http://www.devicehive.com/restful#Reference/DeviceCommand/wait">DeviceHive RESTful API: DeviceCommand: wait</a>
     * 
     * @param waitTimeout Waiting timeout in seconds (default: 30 seconds, maximum: 60 seconds). Specify 0 to disable waiting.
     * @return One of <a href="http://www.devicehive.com/restful#Reference/DeviceCommand">DeviceCommand</a>
     */
    @GET
    @RolesAllowed({ "CLIENT", "DEVICE", "ADMIN" })
    @Path("/{commandId}/poll")
    @Produces(MediaType.APPLICATION_JSON)
    @JsonPolicyApply(Policy.COMMAND_TO_DEVICE)
    public DeviceCommand wait(
            @PathParam("deviceGuid") String deviceGuid,
            @PathParam("commandId") String commandId,
            @QueryParam("waitTimeout") String waitTimeout,
            @Context SecurityContext securityContext) {

        if (deviceGuid == null || commandId == null) {
            throw new BadRequestException("Ids should be provided.");
        }

        Device device = deviceDAO.findByUUID(UUID.fromString(deviceGuid));
        if (device == null) {
            throw new NotFoundException("Device with guid = " + deviceGuid + " not found.");
        }

        DeviceCommand command = commandDAO.findById(Long.valueOf(commandId));
        if (command == null) {
            throw new NotFoundException("DeviceCommand with id = " + commandId + " not found.");
        }

        if (!command.getDevice().getId().equals(device.getId())) {
            throw new BadRequestException("Command with id = " + commandId + " is not belong to device with guid = " + deviceGuid);
        }

        long timeout = Params.parseWaitTimeout(waitTimeout);

        User user = ((HivePrincipal) securityContext.getUserPrincipal()).getUser();
        DeferredResponse result = messageBus.subscribe(MessageType.DEVICE_TO_CLIENT_UPDATE_COMMAND,
                MessageDetails.create().ids(device.getId(), command.getId()).user(user));
        List<DeviceCommand> response = LocalMessageBus.expandDeferredResponse(result, timeout, DeviceCommand.class);

        return response.isEmpty() ? null : response.get(0);
    }

    @GET
    @RolesAllowed({"CLIENT", "DEVICE", "ADMIN"})
    @Produces(MediaType.APPLICATION_JSON)
    @JsonPolicyApply(JsonPolicyDef.Policy.COMMAND_TO_DEVICE)
    public List<DeviceCommand> query(@PathParam("deviceGuid") String guid,
                                     @QueryParam("start") String start,
                                     @QueryParam("end") String end,
                                     @QueryParam("command") String command,
                                     @QueryParam("status") String status,
                                     @QueryParam("sortField") String sortField,
                                     @QueryParam("sortOrder") String sortOrder,
                                     @QueryParam("take") Integer take,
                                     @QueryParam("skip") Integer skip) {
        if (sortOrder != null && !sortOrder.equals("DESC") && !sortOrder.equals("ASC")) {
            throw new BadRequestException("The sort order cannot be equal " + sortOrder);
        }
        boolean sortOrderAsc = true;
        if ("DESC".equals(sortOrder)) {
            sortOrderAsc = false;
        }
        if (!"Timestamp".equals(sortField) && !"Command".equals(sortField) && !"Status".equals(sortField) && sortField
                != null) {
            throw new BadRequestException("The sort field cannot be equal " + sortField);
        }
        if (sortField == null) {
            sortField = "timestamp";
        }
        sortField = sortField.toLowerCase();
        Date startTimestamp = null, endTimestamp = null;
        if (start != null) {
            startTimestamp = Params.parseUTCDate(start);
            if (startTimestamp == null) {
                throw new BadRequestException("unparseable date " + start);
            }
        }
        if (end != null) {
            endTimestamp = Params.parseUTCDate(end);
            if (endTimestamp == null) {
                throw new BadRequestException("unparseable date " + end);
            }
        }
        Device device = getDevice(guid);
        return commandDAO.queryDeviceCommand(device, startTimestamp, endTimestamp, command, status, sortField, sortOrderAsc, take, skip);
    }

    private Device getDevice(String uuid) {
        UUID deviceId;
        try {
            deviceId = UUID.fromString(uuid);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("unparseable guid: " + uuid);
        }

        Device device = deviceDAO.findByUUID(deviceId);
        if (device == null) {
            throw new NotFoundException("device with guid " + uuid + " not found");
        }

        return device;
    }
}
