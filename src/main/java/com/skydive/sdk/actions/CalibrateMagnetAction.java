package com.skydive.sdk.actions;

import com.skydive.sdk.CommHandler;
import com.skydive.sdk.UavEvent;
import com.skydive.sdk.data.CalibrationSettings;
import com.skydive.sdk.data.DebugData;
import com.skydive.sdk.data.SignalData;
import com.skydive.sdk.events.CommEvent;
import com.skydive.sdk.events.MessageEvent;
import com.skydive.sdk.events.SignalPayloadEvent;
import com.skydive.sdk.events.UserEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Created by nawbar on 12.01.2017.
 */

public class CalibrateMagnetAction extends CommHandlerAction {

    private static Logger logger = LoggerFactory.getLogger(CalibrateMagnetAction.class);

    public enum CalibrationState {
        IDLE,
        INITIAL_COMMAND,
        WAITING_FOR_USER_COMMAND,
        WAITING_FOR_CALIBRATION,
        WAITING_FOR_CALIBRATION_DATA,
        WAITING_FOR_CANCEL_ACK

    }

    private CalibrationState state;

    private boolean calibrationProcedureDone;

    public CalibrateMagnetAction(CommHandler commHandler) {
        super(commHandler);
        state = CalibrationState.IDLE;
        calibrationProcedureDone = false;
    }

    @Override
    public boolean isActionDone() {
        return calibrationProcedureDone;
    }

    @Override
    public void start() {
        logger.info("Starting magnetometer calibration procedure");
        calibrationProcedureDone = false;
        state = CalibrationState.INITIAL_COMMAND;
        commHandler.stopCommTask(commHandler.getPingTask());
        commHandler.send(new SignalData(SignalData.Command.CALIBRATE_MAGNET, SignalData.Parameter.START).getMessage());
    }

    @Override
    public void handleEvent(CommEvent event) throws Exception {
        CalibrationState actualState = state;
        switch (state) {
            case INITIAL_COMMAND:
                if (event.getType() == CommEvent.EventType.MESSAGE_RECEIVED) {
                    switch (((MessageEvent) event).getMessageType()) {
                        case CONTROL:
                            logger.info("DebugData received when waiting for ACK on initial calibrate magnetometer command");
                            commHandler.getUavManager().setDebugData(new DebugData(((MessageEvent) event).getMessage()));
                            break;

                        case SIGNAL:
                            if (event.matchSignalData(new SignalData(SignalData.Command.CALIBRATE_MAGNET, SignalData.Parameter.ACK))) {
                                logger.info("Magnetometer calibration starts");
                                state = CalibrationState.WAITING_FOR_USER_COMMAND;
                                commHandler.getUavManager().notifyUavEvent(new UavEvent(UavEvent.Type.MAGNETOMETER_CALIBRATION_STARTED));
                            } else {
                                logger.info("Unexpected event received at state " + state.toString());
                            }
                            break;
                    }
                }
                break;

            case WAITING_FOR_USER_COMMAND:
                logger.info("Unexpected comm event received at state " + state.toString());
                break;

            case WAITING_FOR_CALIBRATION:
                if (event.matchSignalData(new SignalData(SignalData.Command.CALIBRATE_MAGNET, SignalData.Parameter.DONE))) {
                    state = CalibrationState.WAITING_FOR_CALIBRATION_DATA;

                } else if (event.matchSignalData(new SignalData(SignalData.Command.CALIBRATE_MAGNET, SignalData.Parameter.FAIL))) {
                    commHandler.getUavManager().notifyUavEvent(new UavEvent(UavEvent.Type.MESSAGE, "Bad data acquired during magnetometer calibration!"));
                    calibrationProcedureDone = true;
                    commHandler.notifyActionDone();

                } else {
                    logger.info("Unexpected event received at state " + state.toString());
                }
                break;

            case WAITING_FOR_CALIBRATION_DATA:
                if (event.getType() == CommEvent.EventType.SIGNAL_PAYLOAD_RECEIVED
                        && ((SignalPayloadEvent) event).getDataType() == SignalData.Command.CALIBRATION_SETTINGS_DATA) {
                    SignalPayloadEvent signalEvent = (SignalPayloadEvent) event;

                    CalibrationSettings calibrationSettings = (CalibrationSettings) signalEvent.getData();
                    if (calibrationSettings.isValid()) {
                        logger.info("Calibration settings received after magnetometer calibration");
                        commHandler.send(new SignalData(SignalData.Command.CALIBRATION_SETTINGS, SignalData.Parameter.ACK).getMessage());
                        commHandler.getUavManager().setCalibrationSettings(calibrationSettings);
                        commHandler.getUavManager().notifyUavEvent(new UavEvent(UavEvent.Type.MESSAGE, "Magnetometer calibration successful!"));
                        calibrationProcedureDone = true;
                        commHandler.notifyActionDone();
                    } else {
                        commHandler.getUavManager().notifyUavEvent(new UavEvent(UavEvent.Type.MESSAGE, "Magnetometer calibration failed!"));
                        logger.info("Calibration settings received but the data is invalid, responding with DATA_INVALID");
                        commHandler.send(new SignalData(SignalData.Command.CALIBRATION_SETTINGS, SignalData.Parameter.DATA_INVALID).getMessage());
                        calibrationProcedureDone = true;
                        commHandler.notifyActionDone();
                    }
                } else {
                    throw new Exception("Unexpected message received at state " + state.toString());
                }
                break;

            case WAITING_FOR_CANCEL_ACK:
                if (event.matchSignalData(new SignalData(SignalData.Command.CALIBRATE_MAGNET, SignalData.Parameter.ACK))) {
                    commHandler.getUavManager().notifyUavEvent(
                            new UavEvent(UavEvent.Type.MESSAGE, "Magnetometer calibration canceled."));
                    calibrationProcedureDone = true;
                    commHandler.notifyActionDone();
                } else {
                    throw new Exception("Unexpected message received at state " + state.toString());
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
    public void notifyUserEvent(UserEvent userEvent) {
        if (state == CalibrationState.WAITING_FOR_USER_COMMAND) {
            if (userEvent.getType() == UserEvent.Type.DONE_MAGNETOMETER_CALIBRATION) {
                commHandler.send(new SignalData(SignalData.Command.CALIBRATE_MAGNET, SignalData.Parameter.DONE).getMessage());
                state = CalibrationState.WAITING_FOR_CALIBRATION;

            } else if (userEvent.getType() == UserEvent.Type.CANCEL_MAGNETOMETER_CALIBRATION) {
                commHandler.send(new SignalData(SignalData.Command.CALIBRATE_MAGNET, SignalData.Parameter.SKIP).getMessage());
                state = CalibrationState.WAITING_FOR_CANCEL_ACK;
            }
        } else {
            logger.info("Unexpected user event received at state " + state.toString());
        }
    }

    @Override
    public ActionType getActionType() {
        return ActionType.MAGNETOMETER_CALIBRATION;
    }
}