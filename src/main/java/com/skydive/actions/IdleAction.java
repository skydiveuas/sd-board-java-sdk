package com.skydive.actions;

import com.skydive.CommHandler;
import com.skydive.events.CommEvent;

import static com.skydive.actions.CommHandlerAction.ActionType.IDLE;

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
        return IDLE;
    }
}
