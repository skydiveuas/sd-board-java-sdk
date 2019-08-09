package com.skydive.actions;

import com.skydive.CommHandler;
import com.skydive.CommMessage;
import com.skydive.data.DebugData;
import com.skydive.events.CommEvent;
import com.skydive.events.MessageEvent;

/**
 * Created by Bartosz Nawrot on 2016-10-14.
 */
public class AppLoopAction extends CommHandlerAction {

    public AppLoopAction(CommHandler commHandler) {
        super(commHandler);
    }

    @Override
    public ActionType getActionType() {
        return ActionType.APPLICATION_LOOP;
    }

    @Override
    public boolean isActionDone() {
        return true;
    }

    @Override
    public void start() {
        logger.info("AppLoopAction: Starting app loop handling mode");
        commHandler.startCommTask(commHandler.getPingTask());
    }

    @Override
    public void handleEvent(CommEvent event) throws Exception {
        if (event.getType() == CommEvent.EventType.MESSAGE_RECEIVED) {
            final MessageEvent messageEvent = ((MessageEvent) event);

            if (messageEvent.getMessageType() == CommMessage.MessageType.CONTROL) {
                // debug data received
                commHandler.getUavManager().setDebugData(new DebugData(messageEvent.getMessage()));
            } else {
                logger.info("AppLoopAction: Unexpected massage received: " + messageEvent.toString());
            }
        }
    }
}
