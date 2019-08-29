package com.skydive.sdk.actions;

import com.skydive.sdk.CommHandler;
import com.skydive.sdk.CommMessage;
import com.skydive.sdk.data.DebugData;
import com.skydive.sdk.events.CommEvent;
import com.skydive.sdk.events.MessageEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Created by Bartosz Nawrot on 2016-10-14.
 */
public class AppLoopAction extends CommHandlerAction {

    private static Logger logger = LoggerFactory.getLogger(AppLoopAction.class);

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
        logger.info("Starting app loop handling mode");
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
                logger.warn("Unexpected massage received: {}", messageEvent.toString());
            }
        }
    }
}
