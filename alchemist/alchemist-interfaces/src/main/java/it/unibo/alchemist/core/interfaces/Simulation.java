/*
 * Copyright (C) 2010-2014, Danilo Pianini and contributors
 * listed in the project's pom.xml file.
 *
 * This file is part of Alchemist, and is distributed under the terms of
 * the GNU General Public License, with a linking exception, as described
 * in the file LICENSE in the Alchemist distribution's top directory.
 */
package it.unibo.alchemist.core.interfaces;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

import org.jooq.lambda.fi.lang.CheckedRunnable;

import it.unibo.alchemist.boundary.interfaces.OutputMonitor;
import it.unibo.alchemist.model.interfaces.Environment;
import it.unibo.alchemist.model.interfaces.Time;

/**
 * This interface forces simulations to be independent threads, and make them
 * controllable from an external console.
 *
 * @param <T>
 *            The type which describes the concentration of a molecule
 */
public interface Simulation<T> extends Runnable {

    /**
     * Adds an {@link OutputMonitor} to this simulation.
     *
     * @param op
     *            the OutputMonitor to add
     */
    void addOutputMonitor(OutputMonitor<T> op);

    /**
     * Removes an {@link OutputMonitor} to this simulation. If the
     * {@link OutputMonitor} was not among those already added, this method does
     * nothing.
     *
     * @param op
     *            the OutputMonitor to add
     */
    void removeOutputMonitor(OutputMonitor<T> op);

    /**
     * Allows to access the current environment.
     *
     * @return a reference to the current Environment. The environment is not a
     *         copy but back-ends the real environment used in the simulation.
     *         Manipulate it carefully
     */
    Environment<T> getEnvironment();

    /**
     * @return the step at which this simulation will eventually stop.
     */
    long getFinalStep();

    /**
     * Allows to at which time this simulation will end.
     *
     * @return the final time
     */
    Time getFinalTime();

    /**
     * Allows to access the current status.
     *
     * @return the current Status of the simulation
     */
    Status getStatus();

    /**
     * Allows to access the current simulation step.
     *
     * @return the current step
     */
    long getStep();

    /**
     * Allows to know which is the current simulation time.
     *
     * @return the current time
     */
    Time getTime();

    /**
     * Suspends the caller until the simulation reaches the selected
     * {@link Status}.
     *
     * @param s
     *            The {@link Status} the simulation must reach before returning
     *            from this method
     * @param timeout
     *            The maximum lapse of time the caller wants to wait before
     *            being resumed (0 means "no limit")
     * @param timeunit
     *            The {@link TimeUnit} used to define "timeout"
     *
     * @return the status of the Simulation at the end of the wait
     */
    Status waitFor(Status s, long timeout, TimeUnit timeunit);

    /**
     * Sends a play command to the simulation. There is no guarantee on when
     * this command will be actually processed.
     */
    void play();

    /**
     * Executes a certain number of steps, then pauses it.
     * 
     * @param steps
     *            the number of steps to execute
     */
    void goToStep(long steps);

    /**
     * Executes the simulation until the target time is reached, then pauses it.
     * 
     * @param t
     *            the target time
     */
    void goToTime(Time t);

    /**
     * Sends a pause command to the simulation. There is no guarantee on when
     * this command will be actually processed.
     */
    void pause();

    /**
     * Sends a terminate command to the simulation. There is no guarantee on when
     * this command will be actually processed.
     */
    void terminate();

    /**
     * Schedules a runnable to be executed by the Simulation thread, useful for
     * synchronization purposes (e.g. make sure that the environment is not
     * being changed while the requested operation is being executed). An
     * exception thrown by the passed runnable will make the simulation
     * terminate.
     * 
     * @param r
     *            the runnable to execute
     */
    void schedule(CheckedRunnable r);

    Optional<Throwable> getError();

}
