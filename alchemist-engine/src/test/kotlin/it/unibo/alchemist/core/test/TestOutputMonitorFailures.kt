/*
 * Copyright (C) 2010-2022, Danilo Pianini and contributors
 * listed, for each module, in the respective subproject's build.gradle.kts file.
 *
 * This file is part of Alchemist, and is distributed under the terms of the
 * GNU General Public License, with a linking exception,
 * as described in the file LICENSE in the Alchemist distribution's top directory.
 */

package it.unibo.alchemist.core.test

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.optional.shouldBePresent
import it.unibo.alchemist.boundary.interfaces.OutputMonitor
import it.unibo.alchemist.model.implementations.positions.Euclidean2DPosition
import it.unibo.alchemist.model.interfaces.Environment
import it.unibo.alchemist.model.interfaces.Time
import it.unibo.alchemist.testsupport.createEmptyEnvironment

class TestOutputMonitorFailures : FreeSpec(
    {
        "the simulation should fail gracefully when an outputmonitor fails during" - {
            "initialization" {
                runTest(
                    object : OutputMonitor<Nothing, Euclidean2DPosition> {
                        override fun initialized(environment: Environment<Nothing, Euclidean2DPosition>) =
                            error("initialization failure")
                    }
                )
            }
            "termination" {
                runTest(
                    object : OutputMonitor<Nothing, Euclidean2DPosition> {
                        override fun finished(
                            environment: Environment<Nothing, Euclidean2DPosition>,
                            time: Time,
                            step: Long
                        ) = error("termination failure")
                    }
                )
            }
        }
    }
) {
    companion object {
        fun runTest(outputMonitor: OutputMonitor<Nothing, Euclidean2DPosition>) {
            val environment = createEmptyEnvironment(outputMonitor)
            with(environment.simulation) {
                play()
                run()
                error.shouldBePresent()
            }
        }
    }
}
