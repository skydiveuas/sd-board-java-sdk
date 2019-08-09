package com.skydive;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Timer;
import java.util.TimerTask;

/**
 * Created by Bartosz Nawrot on 2016-08-30.
 * Abstract class for all synchronous communication tasks.
 * Controlled by start and stop methods, dynamically responds for frequency change.
 */
public abstract class CommTask {
    private Timer timer;

    private static Logger logger = LoggerFactory.getLogger(CommTask.class);

    // frequency of task [Hz]
    private double frequency;

    private boolean isRunning;

    protected CommTask(double frequency) {
        this.frequency = frequency;
        this.isRunning = false;
    }

    public void start() {
        start(frequency);
    }

    private void start(double freq) {
        timer = new Timer(getTaskName() + "_timer");
        TimerTask timerTask = new TimerTask() {
            @Override
            public void run() {
                task();
            }
        };
        long period = (long) ((1.0 / freq) * 1000);
        long delay = period > 200 ? period : 200;
        logger.info("Starting {} task with freq: {} Hz, and delay: {} ms", getTaskName(), freq, delay);
        timer.scheduleAtFixedRate(timerTask, delay, period);
        isRunning = true;
        onStarted();
    }

    public void stop() {
        logger.info("Stopping task: " + getTaskName());
        if (timer != null) {
            timer.cancel();
        }
        isRunning = false;
        onStopped();
    }

    public void setFrequency(double frequency) {
        this.frequency = frequency;
        if (isRunning) {
            stop();
            start();
        }
    }

    protected abstract String getTaskName();

    protected abstract void task();

    protected void onStarted() {
        // nothing to do here, user can override
    }

    protected void onStopped() {
        // nothing to do here, user can override
    }
}
