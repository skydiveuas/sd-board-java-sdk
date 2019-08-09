package com.skydive.sdk.actions;

import com.skydive.sdk.CommHandler;
import com.skydive.sdk.UavEvent;
import com.skydive.sdk.data.DebugData;
import com.skydive.sdk.data.SignalData;
import com.skydive.sdk.data.SignalPayloadData;
import com.skydive.sdk.events.CommEvent;
import com.skydive.sdk.events.MessageEvent;

/**
 * Created by nawba on 12.01.2017.
 */

public class UploadRouteContainerAction extends CommHandlerAction {

    public enum UploadState {
        IDLE,
        INITIAL_COMMAND,
        UPLOADING_DATA

    }

    private UploadState state;
    private boolean uploadProcedureDone;
    private SignalPayloadData routeContainerToUpload;

    public UploadRouteContainerAction(CommHandler commHandler, SignalPayloadData routeContainerToUpload) {
        super(commHandler);
        this.state = UploadState.IDLE;
        this.routeContainerToUpload = routeContainerToUpload;
        uploadProcedureDone = false;
    }

    @Override
    public void start() {
        state = UploadState.INITIAL_COMMAND;
        commHandler.stopCommTask(commHandler.getPingTask());
        commHandler.send(new SignalData(SignalData.Command.UPLOAD_ROUTE, SignalData.Parameter.START).getMessage());
    }

    @Override
    public boolean isActionDone() {
        return uploadProcedureDone;
    }

    @Override
    public void handleEvent(CommEvent event) throws Exception {
        UploadState actualState = state;
        switch (state) {
            case INITIAL_COMMAND:
                if (event.getType() == CommEvent.EventType.MESSAGE_RECEIVED) {
                    switch (((MessageEvent) event).getMessageType()) {
                        case CONTROL:
                            logger.info("DebugData received when waiting for ACK on RouteContainer upload procedure");
                            commHandler.getUavManager().setDebugData(new DebugData(((MessageEvent) event).getMessage()));
                            break;
                        case SIGNAL:
                            if (event.matchSignalData(new SignalData(SignalData.Command.UPLOAD_ROUTE, SignalData.Parameter.ACK))) {
                                logger.info("Starting Route Container upload procedure");
                                state = UploadState.UPLOADING_DATA;
                                commHandler.send(routeContainerToUpload);
                            } else {
                                logger.info("Unexpected event received at state " + state.toString());
                            }
                            break;
                    }
                }
                break;
            case UPLOADING_DATA:
                if (event.matchSignalData(new SignalData(SignalData.Command.ROUTE_CONTAINER, SignalData.Parameter.ACK))) {
                    logger.info("Route Container settings uploaded");
                    commHandler.getUavManager().notifyUavEvent(new UavEvent(UavEvent.Type.MESSAGE, "Route Container settings uploaded successfully!"));
                    uploadProcedureDone = true;
                    commHandler.notifyActionDone();
                } else if (event.matchSignalData(new SignalData(SignalData.Command.ROUTE_CONTAINER, SignalData.Parameter.DATA_INVALID))) {
                    commHandler.getUavManager().notifyUavEvent(new UavEvent(UavEvent.Type.MESSAGE, "Uploading Route Container settings failed!"));
                    logger.info("Route Container settings sent but the data is invalid, responding with DATA_INVALID");
                    commHandler.send(routeContainerToUpload);
                } else if (event.matchSignalData(new SignalData(SignalData.Command.ROUTE_CONTAINER, SignalData.Parameter.TIMEOUT))) {
                    commHandler.getUavManager().notifyUavEvent(new UavEvent(UavEvent.Type.MESSAGE, "Uploading Route Container settings timeout!"));
                    logger.info("Uploading Route Container procedure timeout, responding with TIMEOUT");
                    commHandler.send(routeContainerToUpload);
                } else {
                    logger.info("Unexpected event received at state " + state.toString());
                }
                break;
            default:
                throw new Exception("Event: " + event.toString() + " received at unknown state");
        }
        if (actualState != state) {
            logger.info("HandleEvent done, transition: " + actualState.toString() + " -> " + state.toString());
        } else {
            logger.info("HandleEvent done, no state change");
        }
    }


    @Override
    public ActionType getActionType() {
        return ActionType.UPLOAD_ROUTE_CONTAINER;
    }
}
