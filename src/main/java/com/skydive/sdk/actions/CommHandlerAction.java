package com.skydive.sdk.actions;

import com.skydive.sdk.CommHandler;
import com.skydive.sdk.events.CommEvent;
import com.skydive.sdk.events.UserEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Created by NawBar on 2016-10-12.
 */
public abstract class CommHandlerAction {

    protected static Logger logger = LoggerFactory.getLogger(CommHandlerAction.class);

    protected CommHandler commHandler;

    public enum ActionType {
        IDLE,
        CONNECT,
        DISCONNECT,
        APPLICATION_LOOP,
        FLIGHT_LOOP,
        ACCELEROMETER_CALIBRATION,
        MAGNETOMETER_CALIBRATION,
        UPLOAD_CONTROL_SETTINGS,
        DOWNLOAD_CONTROL_SETTINGS,
        UPLOAD_ROUTE_CONTAINER,
        DOWNLOAD_ROUTE_CONTAINER,
        CALIBRATE_MAGNETOMETER;
    }

    CommHandlerAction(CommHandler commHandler) {
        this.commHandler = commHandler;
    }

    public abstract boolean isActionDone();

    public abstract void start();

    public abstract void handleEvent(CommEvent event) throws Exception;

    public void notifyUserEvent(UserEvent userEvent) {
        logger.info("User event handled by base handler, no action");
    }

    public abstract ActionType getActionType();

    public String getActionName() {
        return getActionType().toString();
    }

    @Override
    public String toString() {
        return getActionName();
    }
}