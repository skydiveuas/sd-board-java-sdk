package com.skydive.sdk.actions;

import com.skydive.sdk.CommHandler;
import com.skydive.sdk.UavEvent;
import com.skydive.sdk.data.CalibrationSettings;
import com.skydive.sdk.data.DebugData;
import com.skydive.sdk.data.SignalData;
import com.skydive.sdk.events.CommEvent;
import com.skydive.sdk.events.MessageEvent;
import com.skydive.sdk.events.SignalPayloadEvent;

/**
 * Created by Bartosz Nawrot on 2016-10-22.
 */

public class CalibrateAccelAction extends CommHandlerAction {

    public enum CalibrationState {
        IDLE,
        INITIAL_COMMAND,
        WAITING_FOR_CALIBRATION,
        WAITING_FOR_CALIBRATION_DATA
    }

    private CalibrationState state;

    private boolean calibrationProcedureDone;

    public CalibrateAccelAction(CommHandler commHandler) {
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
        logger.info("Starting calibration accelerometer procedure");
        calibrationProcedureDone = false;
        state = CalibrationState.INITIAL_COMMAND;
        commHandler.stopCommTask(commHandler.getPingTask());
        commHandler.send(new SignalData(SignalData.Command.CALIBRATE_ACCEL, SignalData.Parameter.START).getMessage());
    }

    @Override
    public void handleEvent(CommEvent event) throws Exception {
        CalibrationState actualState = state;
        switch (state) {
            case INITIAL_COMMAND:
                if (event.getType() == CommEvent.EventType.MESSAGE_RECEIVED) {
                    switch (((MessageEvent) event).getMessageType()) {
                        case CONTROL:
                            logger.info("DebugData received when waiting for ACK on initial calibrate accelerometer command");
                            commHandler.getUavManager().setDebugData(new DebugData(((MessageEvent) event).getMessage()));
                            break;

                        case SIGNAL:
                            if (event.matchSignalData(new SignalData(SignalData.Command.CALIBRATE_ACCEL, SignalData.Parameter.ACK))) {
                                logger.info("Accelerometer calibration starts");
                                state = CalibrationState.WAITING_FOR_CALIBRATION;
                            } else {
                                logger.info("Unexpected event received at state " + state.toString());
                            }
                            break;
                    }
                }
                break;

            case WAITING_FOR_CALIBRATION:
                if (event.matchSignalData(
                        new SignalData(SignalData.Command.CALIBRATE_ACCEL, SignalData.Parameter.DONE))) {
                    state = CalibrationState.WAITING_FOR_CALIBRATION_DATA;
                    logger.info("Calibration done successfully, data ready");
                } else if (event.matchSignalData(
                        new SignalData(SignalData.Command.CALIBRATE_ACCEL, SignalData.Parameter.NON_STATIC))) {
                    logger.info("Calibration non static");
                    commHandler.getUavManager().notifyUavEvent(new UavEvent(UavEvent.Type.MESSAGE, "Accelerometer calibration non static!"));
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
                        logger.info("Calibration settings received after accelerometer calibration");
                        commHandler.send(new SignalData(SignalData.Command.CALIBRATION_SETTINGS, SignalData.Parameter.ACK).getMessage());
                        commHandler.getUavManager().setCalibrationSettings(calibrationSettings);
                        commHandler.getUavManager().notifyUavEvent(new UavEvent(UavEvent.Type.MESSAGE, "Accelerometer calibration successful"));
                        calibrationProcedureDone = true;
                        commHandler.notifyActionDone();
                        commHandler.getUavManager().notifyUavEvent(new UavEvent((UavEvent.Type.ACCEL_CALIB_DONE)));
                    } else {
                        logger.info("Calibration settings received but the data is invalid, responding with DATA_INVALID");
                        commHandler.send(new SignalData(SignalData.Command.CALIBRATION_SETTINGS, SignalData.Parameter.DATA_INVALID).getMessage());
                    }
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
        return ActionType.ACCELEROMETER_CALIBRATION;
    }
}