/*
 * Copyright (C) 2010-2022, Danilo Pianini and contributors
 * listed, for each module, in the respective subproject's build.gradle.kts file.
 *
 * This file is part of Alchemist, and is distributed under the terms of the
 * GNU General Public License, with a linking exception,
 * as described in the file LICENSE in the Alchemist distribution's top directory.
 */

package it.unibo.alchemist.server.surrogates.utility

import io.kotest.core.spec.style.StringSpec
import io.kotest.inspectors.forAllKeys
import io.kotest.matchers.shouldBe
import it.unibo.alchemist.TestUtility.webRendererTestEnvironments
import it.unibo.alchemist.common.model.surrogate.EmptyConcentrationSurrogate
import it.unibo.alchemist.common.model.surrogate.NodeSurrogate
import it.unibo.alchemist.common.model.surrogate.PositionSurrogate
import it.unibo.alchemist.model.interfaces.Environment
import it.unibo.alchemist.model.interfaces.Node
import it.unibo.alchemist.model.interfaces.Position
import it.unibo.alchemist.model.interfaces.geometry.Vector
import it.unibo.alchemist.server.surrogates.utility.ToConcentrationSurrogate.toEmptyConcentration
import it.unibo.alchemist.server.surrogates.utility.ToPositionSurrogate.toSuitablePositionSurrogate

class ToNodeSurrogateTest<T, P> : StringSpec({
    "ToNodeSurrogate should map a Node to a NodeSurrogate" {
        webRendererTestEnvironments<T, P>().forEach {
            val node: Node<T> = it.nodes.first()
            val nodeSurrogate = node.toNodeSurrogate(
                it,
                toEmptyConcentration,
                toSuitablePositionSurrogate(it.dimensions)
            )
            checkToNodeSurrogate(it, node, nodeSurrogate)
        }
    }
}) where T : Any, P : Position<P>, P : Vector<P>

fun <T, P, TS, PS> checkToNodeSurrogate(
    environment: Environment<T, P>,
    node: Node<T>,
    nodeSurrogate: NodeSurrogate<TS, PS>
) where T : Any, P : Position<out P>, TS : Any, PS : PositionSurrogate {
    node.id shouldBe nodeSurrogate.id
    node.contents.forAllKeys { molecule ->
        val surrogateContentKey = molecule.toMoleculeSurrogate()
        checkToMoleculeSurrogate(molecule, surrogateContentKey)
        nodeSurrogate.contents.containsKey(surrogateContentKey)
        nodeSurrogate.contents[surrogateContentKey] shouldBe EmptyConcentrationSurrogate
    }
    node.contents.size shouldBe nodeSurrogate.contents.size
    checkToPositionSurrogate(environment.getPosition(node), nodeSurrogate.position)
}
