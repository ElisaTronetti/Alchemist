/*
 * Copyright (C) 2010-2020, Danilo Pianini and contributors
 * listed in the main project's alchemist/build.gradle.kts file.
 *
 * This file is part of Alchemist, and is distributed under the terms of the
 * GNU General Public License, with a linking exception,
 * as described in the file LICENSE in the Alchemist distribution's top directory.
 */

package it.unibo.alchemist.model.implementations.environments

import it.unibo.alchemist.model.implementations.geometry.euclidean2d.Segment2DImpl
import it.unibo.alchemist.model.implementations.geometry.AdimensionalShape
import it.unibo.alchemist.model.implementations.positions.Euclidean2DPosition
import it.unibo.alchemist.model.interfaces.Neighborhood
import it.unibo.alchemist.model.interfaces.Node
import it.unibo.alchemist.model.interfaces.environments.Euclidean2DEnvironment
import it.unibo.alchemist.model.interfaces.environments.Physics2DEnvironment
import it.unibo.alchemist.model.interfaces.geometry.GeometricShapeFactory
import it.unibo.alchemist.model.interfaces.geometry.euclidean2d.Euclidean2DShape
import it.unibo.alchemist.model.interfaces.geometry.euclidean2d.Euclidean2DShapeFactory
import it.unibo.alchemist.model.interfaces.geometry.euclidean2d.Euclidean2DTransformation
import it.unibo.alchemist.model.interfaces.geometry.euclidean2d.Segment2D
import it.unibo.alchemist.model.interfaces.nodes.NodeWithShape
import java.lang.Exception
import java.lang.IllegalStateException

/**
 * Implementation of [Physics2DEnvironment].
 */
open class Continuous2DEnvironment<T> :
    Euclidean2DEnvironment<T>,
    Abstract2DEnvironment<T, Euclidean2DPosition>(),
    Physics2DEnvironment<T> {

    companion object {
        @JvmStatic private val serialVersionUID: Long = 1L

        private val adimensional =
            AdimensionalShape<Euclidean2DPosition, Euclidean2DTransformation>(Euclidean2DEnvironment.origin)
    }

    override val shapeFactory: Euclidean2DShapeFactory = GeometricShapeFactory.getInstance()
    private val defaultHeading = Euclidean2DPosition(0.0, 0.0)
    private val nodeToHeading = mutableMapOf<Node<T>, Euclidean2DPosition>()
    private var largestShapeDiameter: Double = 0.0

    override fun getNodesWithin(shape: Euclidean2DShape): List<Node<T>> = when {
        shape.diameter + largestShapeDiameter <= 0 -> emptyList()
        else -> getNodesWithinRange(shape.centroid, (shape.diameter + largestShapeDiameter) / 2)
            .filter { shape.intersects(getShape(it)) }
    }

    override fun getHeading(node: Node<T>) = nodeToHeading.getOrPut(node, { defaultHeading })

    override fun setHeading(node: Node<T>, direction: Euclidean2DPosition) {
        nodeToHeading[node] = direction
    }

    override fun getShape(node: Node<T>): Euclidean2DShape = when (node) {
        !is NodeWithShape<*, *, *> -> adimensional
        else -> shapeFactory.requireCompatible(node.shape).transformed {
            origin(getPosition(node))
            rotate(getHeading(node))
        }
    }

    /**
     * Keeps track of the largest diameter of the shapes.
     */
    override fun nodeAdded(node: Node<T>, position: Euclidean2DPosition, neighborhood: Neighborhood<T>) {
        super.nodeAdded(node, position, neighborhood)
        check(node.canFit(position)) {
            node as NodeWithShape<T, *, *>
            val overlappingPositions = node.overlappingNodes(position).map { getPosition(it) }
            "node in $position overlaps with nodes in $overlappingPositions. Node's diameter is ${node.shape.diameter}"
        }
        if (node is NodeWithShape<*, *, *> && node.shape.diameter > largestShapeDiameter) {
            largestShapeDiameter = node.shape.diameter
        }
    }

    /**
     * {@inheritDoc}.
     */
    override fun nodeRemoved(node: Node<T>, neighborhood: Neighborhood<T>) =
        super.nodeRemoved(node, neighborhood).also {
            nodeToHeading.remove(node)
            if (node is NodeWithShape<*, *, *> && largestShapeDiameter <= node.shape.diameter) {
                largestShapeDiameter = nodes.asSequence()
                    .filterIsInstance<NodeWithShape<*, *, *>>()
                    .map { it.shape.diameter }
                    .max() ?: 0.0
            }
        }

    /**
     * Moves the [node] to the [farthestPositionReachable] towards the desired [newpos]. If the node is shapeless,
     * it is simply moved to [newpos].
     */
    override fun moveNodeToPosition(node: Node<T>, newpos: Euclidean2DPosition) =
        if (node is NodeWithShape<T, *, *>) {
            val farthestPosition = farthestPositionReachable(node, newpos)
            check(node.canFit(farthestPosition)) { "Internal bug in ${this::class.qualifiedName}" }
            super.moveNodeToPosition(node, farthestPosition)
        } else {
            super.moveNodeToPosition(node, newpos)
        }

    /**
     * A node should be added only if it doesn't collide with already existing nodes and fits in the environment's
     * limits.
     */
    override fun nodeShouldBeAdded(node: Node<T>, position: Euclidean2DPosition): Boolean = node.canFit(position)

    /**
     * Creates an euclidean position from the given coordinates.
     * @param coordinates coordinates array
     * @return Euclidean2DPosition
     */
    override fun makePosition(vararg coordinates: Number) = with(coordinates) {
        require(size == 2)
        Euclidean2DPosition(coordinates[0].toDouble(), coordinates[1].toDouble())
    }

    /**
     * For a better understanding of how to compute collision points with circular hitboxes see
     * [this discussion](https://bit.ly/3f00NvJ).
     */
    override fun farthestPositionReachable(
        node: NodeWithShape<T, *, *>,
        desiredPosition: Euclidean2DPosition,
        hitboxRadius: Double
    ): Euclidean2DPosition {
        val currentPosition = getPosition(node)
        val desiredMovement = Segment2DImpl(currentPosition, desiredPosition)
        val nodesOnPath = nodesOnPath(node, desiredMovement)
        if (nodesOnPath.any { getPosition(it).distanceTo(currentPosition) < it.shape.radius + hitboxRadius }) {
            return currentPosition
        }
        return nodesOnPath
            .map { getShape(it) }
            .flatMap { other ->
                desiredMovement.intersectCircle(other.centroid, other.radius + hitboxRadius).asList
            }
            .minBy { currentPosition.distanceTo(it) }
            ?: desiredPosition
    }

    /**
     * @returns all nodes that the given [node] would collide with while performing the [desiredMovement].
     * Such segment should connect the [node]'s current position and its desired position.
     */
    private fun nodesOnPath(node: NodeWithShape<T, *, *>, desiredMovement: Segment2D<*>): List<NodeWithShape<T, *, *>> =
        with(node.shape) {
            shapeFactory.rectangle(desiredMovement.length + diameter, diameter)
                .transformed {
                    desiredMovement.midPoint.let { origin(it.x, it.y) }
                    rotate(desiredMovement.toVector.asAngle)
                }
                .let { movementArea ->
                    getNodesWithin(movementArea).filterIsInstance<NodeWithShape<T, *, *>>().minusElement(node)
                }
        }

    private fun Node<T>.canFit(position: Euclidean2DPosition): Boolean =
        this !is NodeWithShape<T, *, *> || overlappingNodes(position).isEmpty()

    private fun NodeWithShape<T, *, *>.overlappingNodes(position: Euclidean2DPosition): List<NodeWithShape<T, *, *>> =
        getNodesWithin(shapeFactory.requireCompatible(shape).transformed { origin(position) })
            .filterIsInstance<NodeWithShape<T, *, *>>()
            .minusElement(this)
}
