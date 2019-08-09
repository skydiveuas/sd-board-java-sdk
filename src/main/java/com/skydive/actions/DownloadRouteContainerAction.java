package com.skydive.actions;

import com.skydive.CommHandler;
import com.skydive.UavEvent;
import com.skydive.data.DebugData;
import com.skydive.data.RouteContainer;
import com.skydive.data.SignalData;
import com.skydive.events.CommEvent;
import com.skydive.events.MessageEvent;
import com.skydive.events.SignalPayloadEvent;

/**
 * Created by nawbar on 12.01.2017.
 */

public class DownloadRouteContainerAction extends CommHandlerAction {

    private enum DownloadState {
        IDLE,
        INITIAL_COMMAND,
        DOWNLOADING_DATA
    }

    private DownloadState state;

    public DownloadRouteContainerAction(CommHandler commHandler) {
        super(commHandler);
        this.state = DownloadState.IDLE;
    }

    @Override
    public void start() {
        state = DownloadState.INITIAL_COMMAND;
        commHandler.stopCommTask(commHandler.getPingTask());
        commHandler.send(new SignalData(SignalData.Command.DOWNLOAD_ROUTE, SignalData.Parameter.START).getMessage());
    }

    @Override
    public boolean isActionDone() {
        return false;
    }

    @Override
    public void handleEvent(CommEvent event) throws Exception {
        DownloadState actualState = state;
        switch (state) {
            case INITIAL_COMMAND:
                if (event.getType() == CommEvent.EventType.MESSAGE_RECEIVED) {
                    switch (((MessageEvent) event).getMessageType()) {
                        case CONTROL:
                            logger.info("DebugData received when waiting for ACK on RouteContainer download procedure");
                            commHandler.getUavManager().setDebugData(new DebugData(((MessageEvent) event).getMessage()));
                            break;
                        case SIGNAL:
                            if (event.matchSignalData(new SignalData(SignalData.Command.DOWNLOAD_ROUTE, SignalData.Parameter.ACK))) {
                                logger.info("Starting Route Container upload procedure");
                                state = DownloadState.DOWNLOADING_DATA;
                            } else {
                                logger.info("Unexpected event received at state " + state.toString());
                            }
                            break;
                    }
                }
                break;
            case DOWNLOADING_DATA:
                if (event.getType() == CommEvent.EventType.SIGNAL_PAYLOAD_RECEIVED
                        && ((SignalPayloadEvent) event).getDataType() == SignalData.Command.ROUTE_CONTAINER_DATA) {

                    SignalPayloadEvent signalEvent = (SignalPayloadEvent) event;
                    RouteContainer routeContainer = (RouteContainer) signalEvent.getData();

                    if (routeContainer.isValid()) {
                        logger.info("Route Container settings received");
                        commHandler.send(new SignalData(SignalData.Command.DOWNLOAD_SETTINGS, SignalData.Parameter.ACK).getMessage());
                        commHandler.getUavManager().setRouteContainer(routeContainer);
                        commHandler.getUavManager().notifyUavEvent(new UavEvent(UavEvent.Type.MESSAGE, "Route Container settings downloaded successfully!"));
                        commHandler.notifyActionDone();
                    } else {
                        commHandler.getUavManager().notifyUavEvent(new UavEvent(UavEvent.Type.MESSAGE, "Downloading Route Container settings failed!"));
                        logger.info("Route Container settings received but the data is invalid, responding with DATA_INVALID");
                        commHandler.send(new SignalData(SignalData.Command.DOWNLOAD_SETTINGS, SignalData.Parameter.DATA_INVALID).getMessage());
                        commHandler.notifyActionDone();
                    }
                } else {
                    logger.info("Unexpected event received at state " + state.toString());
                }

                break;
            default:
                throw new Exception("Event: " + event.toString() + " received at at unnown state");
        }
        if (actualState != state) {
            logger.info("HandleEvent done, transition: " + actualState.toString() + " -> " + state.toString());
        } else {
            logger.info("HandleEvent done, no state change");
        }
    }

    @Override
    public ActionType getActionType() {
        return ActionType.DOWNLOAD_ROUTE_CONTAINER;
    }
}
