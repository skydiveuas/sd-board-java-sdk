package com.skydive.sdk.actions;

import com.skydive.sdk.CommHandler;
import com.skydive.sdk.UavEvent;
import com.skydive.sdk.data.ControlSettings;
import com.skydive.sdk.data.DebugData;
import com.skydive.sdk.data.RouteContainer;
import com.skydive.sdk.data.SignalData;
import com.skydive.sdk.events.CommEvent;
import com.skydive.sdk.events.MessageEvent;
import com.skydive.sdk.events.SignalPayloadEvent;
import com.skydive.sdk.events.UserEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Created by Bartosz Nawrot on 2016-10-14.
 */
public class FlightLoopAction extends CommHandlerAction {

    private static Logger logger = LoggerFactory.getLogger(FlightLoopAction.class);

    public enum FlightLoopState {
        IDLE,
        INITIAL_COMMAND,
        WAITING_FOR_CONTROLS,
        WAITING_FOR_ROUTE_COMMAND,
        WAITING_FOR_ROUTE,
        FLYING,
        BREAKING,
    }

    private FlightLoopState state;

    private boolean flightLoopDone;

    public FlightLoopAction(final CommHandler commHandler) {
        super(commHandler);
        state = FlightLoopState.IDLE;
        flightLoopDone = false;

    }

    @Override
    public boolean isActionDone() {
        return flightLoopDone;
    }

    @Override
    public void start() {
        logger.info("Starting flight loop");
        commHandler.stopCommTask(commHandler.getPingTask());
        flightLoopDone = false;
        state = FlightLoopState.INITIAL_COMMAND;
        commHandler.send(new SignalData(SignalData.Command.FLIGHT_LOOP, SignalData.Parameter.START).getMessage());
    }

    @Override
    public void handleEvent(CommEvent event) throws Exception {
        FlightLoopState actualState = state;
        switch (state) {
            case INITIAL_COMMAND:
                if (event.getType() == CommEvent.EventType.MESSAGE_RECEIVED) {
                    switch (((MessageEvent) event).getMessageType()) {
                        case CONTROL:
                            logger.info("DebugData received when waiting for ACK on initial flight loop command");
                            commHandler.getUavManager().setDebugData(new DebugData(((MessageEvent) event).getMessage()));
                            break;

                        case SIGNAL:
                            if (event.matchSignalData(new SignalData(SignalData.Command.FLIGHT_LOOP, SignalData.Parameter.ACK))) {
                                logger.info("Flight loop initial command successful");
                                state = FlightLoopState.WAITING_FOR_CONTROLS;

                            } else if (event.matchSignalData(new SignalData(SignalData.Command.FLIGHT_LOOP, SignalData.Parameter.NOT_ALLOWED))) {
                                logger.info("Flight loop not allowed!");
                                flightLoopDone = true;
                                commHandler.notifyActionDone();
                                commHandler.getUavManager().notifyUavEvent(new UavEvent(UavEvent.Type.WARNING, "Flight loop not allowed!"));

                            } else {
                                logger.info("Unexpected event received!!!");
                            }
                            break;
                    }
                }
                break;

            case WAITING_FOR_CONTROLS:
                if (event.getType() == CommEvent.EventType.SIGNAL_PAYLOAD_RECEIVED
                        && ((SignalPayloadEvent) event).getDataType() == SignalData.Command.CONTROL_SETTINGS_DATA) {

                    ControlSettings controlSettings = (ControlSettings) ((SignalPayloadEvent) event).getData();
                    if (controlSettings.isValid()) {
                        commHandler.send(new SignalData(SignalData.Command.CONTROL_SETTINGS, SignalData.Parameter.ACK).getMessage());
                        logger.info("Flight control settings received successfully");

                    } else {
                        logger.info("Control settings received but the data is invalid, responding with DATA_INVALID");
                        commHandler.send(new SignalData(SignalData.Command.CONTROL_SETTINGS, SignalData.Parameter.DATA_INVALID).getMessage());
                        break;
                    }

                    state = FlightLoopState.WAITING_FOR_ROUTE_COMMAND;

                } else {
                    logger.info("Unexpected event received!!!");
                }
                break;

            case WAITING_FOR_ROUTE_COMMAND:
                if (event.matchSignalData(new SignalData(SignalData.Command.FLIGHT_LOOP, SignalData.Parameter.VIA_ROUTE_ALLOWED))) {
                    logger.info("Via route allowed, waiting for RouteContainer");
                    state = FlightLoopState.WAITING_FOR_ROUTE;

                } else if (event.matchSignalData(new SignalData(SignalData.Command.FLIGHT_LOOP, SignalData.Parameter.VIA_ROUTE_NOT_ALLOWED))) {
                    logger.info("Via NOT route allowed, proceeding");
                    logger.info("Flight loop initialization successful");
                    state = FlightLoopState.FLYING;

                    commHandler.startCommTask(commHandler.getPingTask());
                    commHandler.startCommTask(commHandler.getControlTask());

                    commHandler.getUavManager().notifyUavEvent(new UavEvent(UavEvent.Type.FLIGHT_STARTED));
                    commHandler.send(new SignalData(SignalData.Command.FLIGHT_LOOP, SignalData.Parameter.READY).getMessage());

                } else {
                    logger.info("Unexpected event received!!!");
                }
                break;

            case WAITING_FOR_ROUTE:
                if (event.getType() == CommEvent.EventType.SIGNAL_PAYLOAD_RECEIVED
                        && ((SignalPayloadEvent) event).getDataType() == SignalData.Command.ROUTE_CONTAINER_DATA) {

                    RouteContainer routeContainer = (RouteContainer) ((SignalPayloadEvent) event).getData();
                    if (routeContainer.isValid()) {
                        logger.info("Flight route container received successfully");
                        commHandler.send(new SignalData(SignalData.Command.ROUTE_CONTAINER, SignalData.Parameter.ACK).getMessage());

                    } else {
                        logger.info("Route container received but the data is invalid, responding with DATA_INVALID");
                        commHandler.send(new SignalData(SignalData.Command.ROUTE_CONTAINER, SignalData.Parameter.DATA_INVALID).getMessage());
                        break;
                    }

                    logger.info("Flight loop initialization successful");
                    state = FlightLoopState.FLYING;

                    commHandler.startCommTask(commHandler.getPingTask());
                    commHandler.startCommTask(commHandler.getControlTask());

                    commHandler.getUavManager().notifyUavEvent(new UavEvent(UavEvent.Type.FLIGHT_STARTED));
                    commHandler.send(new SignalData(SignalData.Command.FLIGHT_LOOP, SignalData.Parameter.READY).getMessage());

                } else {
                    logger.info("Unexpected event received!!!");
                }
                break;

            case BREAKING:
            case FLYING:
                if (event.getType() == CommEvent.EventType.MESSAGE_RECEIVED) {
                    switch (((MessageEvent) event).getMessageType()) {
                        case CONTROL:
                            commHandler.getUavManager().setDebugData(new DebugData(((MessageEvent) event).getMessage()));
                            break;

                        case SIGNAL:
                            handleSignalWhileFlying(new SignalData(((MessageEvent) event).getMessage()));
                            break;
                    }
                }
                break;

            default:
                throw new Exception("Event: " + event.toString() + " received at unknown state");
        }

        if (actualState != state) {
            logger.info("HandleEvent done, transition: " + actualState.toString() + " -> " + state.toString());
        }
    }

    private void handleSignalWhileFlying(final SignalData command) {
        logger.info("SignalData received in flight loop: " + command.toString());
        if (command.getCommand() == SignalData.Command.FLIGHT_LOOP) {

            commHandler.stopCommTask(commHandler.getControlTask());
            commHandler.stopCommTask(commHandler.getPingTask());

            if (command.getParameter() == SignalData.Parameter.BREAK_ACK && state == FlightLoopState.BREAKING) {
                commHandler.getUavManager().notifyUavEvent(new UavEvent(UavEvent.Type.FLIGHT_ENDED, "by user."));
            } else {
                commHandler.getUavManager().notifyUavEvent(new UavEvent(UavEvent.Type.FLIGHT_ENDED, "by board."));
            }

            state = FlightLoopState.IDLE;
            flightLoopDone = true;
            commHandler.notifyActionDone();
        }
    }

    @Override
    public void notifyUserEvent(UserEvent userEvent) {
        if (userEvent.getType() == UserEvent.Type.END_FLIGHT_LOOP) {
            state = FlightLoopState.BREAKING;
        }
    }

    @Override
    public ActionType getActionType() {
        return ActionType.FLIGHT_LOOP;
    }

    public boolean isBreaking() {
        return state == FlightLoopState.BREAKING;
    }
}