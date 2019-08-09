package com.skydive.sdk.actions;

import com.skydive.sdk.CommHandler;
import com.skydive.sdk.events.CommEvent;

/**
 * Created by NawBar on 2016-10-12.
 */
public class IdleAction extends CommHandlerAction {

    public IdleAction(CommHandler commHandler) {
        super(commHandler);
    }

    @Override
    public boolean isActionDone() {
        return true;
    }

    @Override
    public void start() {
        logger.info("Starting IDLE action - no action");
    }

    @Override
    public void handleEvent(CommEvent event) {
        logger.info("Idle action drops event");
    }

    @Override
    public ActionType getActionType() {
        return ActionType.IDLE;
    }
}
