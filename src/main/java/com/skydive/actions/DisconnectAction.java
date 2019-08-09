package com.skydive.actions;

import com.skydive.CommHandler;
import com.skydive.UavEvent;
import com.skydive.data.SignalData;
import com.skydive.events.CommEvent;

/**
 * Created by nawba on 17.10.2016.
 */
public class DisconnectAction extends CommHandlerAction {

    private boolean disconnectionProcedureDone;

    public DisconnectAction(CommHandler commHandler) {
        super(commHandler);
        disconnectionProcedureDone = false;
    }

    @Override
    public boolean isActionDone() {
        return disconnectionProcedureDone;
    }

    @Override
    public void start() {
        logger.info("Starting disconnection procedure");
        commHandler.stopCommTask(commHandler.getPingTask());
        commHandler.send(new SignalData(SignalData.Command.APP_LOOP, SignalData.Parameter.BREAK).getMessage());
    }

    @Override
    public void handleEvent(CommEvent event) throws Exception {
        if (event.matchSignalData(new SignalData(SignalData.Command.APP_LOOP, SignalData.Parameter.BREAK_ACK))) {
            // app loop disconnected
            disconnectionProcedureDone = true;
            commHandler.getUavManager().notifyUavEvent(new UavEvent(UavEvent.Type.DISCONNECTED));
        }
    }

    @Override
    public ActionType getActionType() {
        return ActionType.DISCONNECT;
    }
}
