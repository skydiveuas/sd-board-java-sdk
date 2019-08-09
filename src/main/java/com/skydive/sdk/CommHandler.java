package com.skydive.sdk;

import com.skydive.sdk.actions.*;
import com.skydive.sdk.data.ControlData;
import com.skydive.sdk.data.SignalData;
import com.skydive.sdk.data.SignalPayloadData;
import com.skydive.sdk.events.CommEvent;
import com.skydive.sdk.events.MessageEvent;
import com.skydive.sdk.events.UserEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by NawBar on 2016-10-12.
 */
public class CommHandler implements CommInterface.CommInterfaceListener,
        CommDispatcher.CommDispatcherListener {

    private static Logger logger = LoggerFactory.getLogger(CommHandler.class);

    private CommHandlerAction commHandlerAction;
    private CommInterface commInterface;
    private CommDispatcher dispatcher;

    private UavManager uavManager;

    private List<CommTask> runningTasks;

    private CommTask controlTask;
    private CommTask pingTask;

    public CommHandler(UavManager uavManager, double controlFreq, double pingFreq) {
        this.commHandlerAction = new IdleAction(this);
        this.dispatcher = new CommDispatcher(this);

        this.uavManager = uavManager;

        this.runningTasks = new ArrayList<>();

        controlTask = new CommTask(controlFreq) {
            @Override
            protected String getTaskName() {
                return "control_task";
            }

            @Override
            protected void task() {
                ControlData controlData = getUavManager().getCurrentControlData();
                if (((FlightLoopAction) commHandlerAction).isBreaking()) {
                    controlData.setStopCommand();
                }
                logger.debug("Controlling: " + controlData);
                send(controlData.getMessage());
            }
        };

        pingTask = new CommTask(pingFreq) {
            @Override
            protected String getTaskName() {
                return "ping_task";
            }

            @Override
            protected void task() {
                logger.debug("Pinging...");
                switch (state) {
                    case CONFIRMED:
                        sentPing = new SignalData(SignalData.Command.PING_VALUE, (int) (Math.random() * 1000000000));
                        send(sentPing.getMessage());
                        timestamp = System.currentTimeMillis();
                        break;

                    case WAITING:
                        logger.info("Ping receiving timeout");
                        state = PingTaskState.CONFIRMED;
                        break;
                }
            }
        };
    }

    public void connect(CommInterface commInterface) {
        logger.info("CommHandler: connect over: " + commInterface.getClass().getSimpleName());
        this.commInterface = commInterface;
        this.commInterface.setListener(this);
        this.commInterface.connect();
    }

    void disconnectInterface() {
        logger.info("CommHandler: disconnectInterface");
        stopAllTasks();
        commInterface.disconnect();
    }

    void preformAction(CommHandlerAction.ActionType actionType) throws Exception {
        if (commHandlerAction.isActionDone()) {
            commHandlerAction = actionFactory(actionType, null);
            commHandlerAction.start();
        } else {
            throw new Exception("CommHandler: Previous action not ready at state: " + commHandlerAction.getActionName() + ", aborting...");
        }
    }

    void preformActionUpload(CommHandlerAction.ActionType actionType, SignalPayloadData dataToUpload) throws Exception {
        if (commHandlerAction.isActionDone()) {
            commHandlerAction = actionFactory(actionType, dataToUpload);
            commHandlerAction.start();
        } else {
            throw new Exception("CommHandler: Previous action not ready at state: " + commHandlerAction.getActionName() + ", aborting...");
        }
    }

    void notifyUserEvent(UserEvent userEvent) throws Exception {
        if (commHandlerAction.getActionType() == userEvent.getOwnerAction()) {
            commHandlerAction.notifyUserEvent(userEvent);
        } else {
            throw new Exception("Owner of user event is different than ongoing action, could not handel it");
        }
    }

    @Override
    public void handleCommEvent(CommEvent event) {
        logger.debug("CommHandler: Event " + event.toString() + " received at action " + commHandlerAction.toString());
        switch (event.getType()) {
            case MESSAGE_RECEIVED:
                if (((MessageEvent) event).getMessageType() == CommMessage.MessageType.SIGNAL) {
                    SignalData signalData = new SignalData(((MessageEvent) event).getMessage());
                    if (signalData.getCommand() == SignalData.Command.PING_VALUE) {
                        uavManager.setCommDelay(handlePongReception(signalData));
                        return;
                    }
                }
                break;
        }

        try {
            commHandlerAction.handleEvent(event);
        } catch (Exception e) {
            logger.info(e.toString());
        }
    }

    public UavManager getUavManager() {
        return uavManager;
    }

    public CommHandlerAction.ActionType getCommActionType() {
        return commHandlerAction.getActionType();
    }

    public void send(CommMessage message) {
        logger.debug("Sending message: " + message.toString());
        byte[] array = message.getByteArray();
        commInterface.send(array, array.length);
    }

    public void send(final SignalPayloadData data) {
        for (CommMessage message : data.getMessages()) {
            send(message);
        }
    }

    public void notifyActionDone() {
        logger.info("NotifyActionDone");
        try {
            preformAction(CommHandlerAction.ActionType.APPLICATION_LOOP);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private CommHandlerAction actionFactory(CommHandlerAction.ActionType actionType, SignalPayloadData data) throws Exception {
        switch (actionType) {
            case IDLE:
                return new IdleAction(this);
            case CONNECT:
                return new ConnectAction(this);
            case DISCONNECT:
                return new DisconnectAction(this);
            case APPLICATION_LOOP:
                return new AppLoopAction(this);
            case FLIGHT_LOOP:
                return new FlightLoopAction(this);
            case ACCELEROMETER_CALIBRATION:
                return new CalibrateAccelAction(this);
            case CALIBRATE_MAGNETOMETER:
                return new CalibrateMagnetAction(this);
            case DOWNLOAD_CONTROL_SETTINGS:
                return new DownloadControlSettingsAction(this);
            case UPLOAD_CONTROL_SETTINGS:
                return new UploadControlSettingsAction(this, data);
            case DOWNLOAD_ROUTE_CONTAINER:
                return new DownloadRouteContainerAction(this);
            case UPLOAD_ROUTE_CONTAINER:
                return new UploadRouteContainerAction(this, data);

            default:
                throw new Exception("Unsupported action type");
        }
    }

    @Override
    public void onDataReceived(final byte[] data, final int dataSize) {
        dispatcher.proceedReceiving(data, dataSize);
    }

    @Override
    public void onError(IOException e) {
        logger.info("onError: " + e.getMessage());
        uavManager.notifyUavEvent(new UavEvent(UavEvent.Type.ERROR, e.getMessage()));
        uavManager.notifyUavEvent(new UavEvent(UavEvent.Type.DISCONNECTED));
    }

    @Override
    public void onDisconnected() {
        logger.info("onDisconnected");
        commHandlerAction = new IdleAction(this);
    }

    @Override
    public void onConnected() {
        logger.info("onConnected");
        try {
            preformAction(CommHandlerAction.ActionType.CONNECT);
        } catch (Exception e) {
            e.printStackTrace();
            uavManager.notifyUavEvent(new UavEvent(UavEvent.Type.ERROR, e.getMessage()));
        }
    }

    private SignalData sentPing;
    private long timestamp;

    private PingTaskState state = PingTaskState.CONFIRMED;

    private long handlePongReception(final SignalData pingPongMessage) {
        if (pingPongMessage.getParameterValue() == sentPing.getParameterValue()) {
            // valid ping measurement, compute ping time
            state = PingTaskState.CONFIRMED;
            return (System.currentTimeMillis() - timestamp) / 2;
        } else {
            logger.warn("Pong key does not match to the ping key!");
            return 0;
        }
    }

    private enum PingTaskState {
        WAITING,
        CONFIRMED
    }

    public CommTask getPingTask() {
        return pingTask;
    }

    public void startCommTask(CommTask task) {
        task.start();
        runningTasks.add(task);
    }

    public void stopCommTask(CommTask task) {
        task.stop();
        runningTasks.remove(task);
    }

    private void stopAllTasks() {
        logger.info("StopAllTasks");
        for (CommTask task : runningTasks) {
            stopCommTask(task);
        }
    }

    public CommTask getControlTask() {
        return controlTask;
    }
}
