package io.kagera.api

import io.kagera.api.ScalaGraph._

import scalax.collection.edge.WLDiEdge

/**
 * Petri net interface.
 *
 * TODO enrich this interface with more convenient functions
 */
trait PetriNet[P, T] {

  /**
   * The scala-graph backing petri net.
   *
   * @return
   */
  def innerGraph: BiPartiteGraph[P, T, WLDiEdge]

  /**
   * The set of places of the petri net
   *
   * @return
   *   The set of places
   */
  def places: Set[P]

  /**
   * The set of transitions of the petri net
   *
   * @return
   *   The set of transitions.
   */
  def transitions: Set[T]

  /**
   * The out-adjecent places of a transition.
   *
   * @param t
   * @return
   */
  def outAdjacentPlaces(t: T): Set[P]

  /**
   * The out-adjacent transitions of a place.
   *
   * @param p
   * @return
   */
  def outAdjacentTransitions(p: P): Set[T]

  /**
   * The in-adjacent places of a transition.
   *
   * @param t
   * @return
   */
  def inAdjacentPlaces(t: T): Set[P]

  /**
   * The in-adjacent transitions of a place.
   *
   * @param p
   * @return
   */
  def inAdjacentTransitions(p: P): Set[T]

  /**
   * Returns the in-marking of a transition. That is; a map of place -> arc weight
   *
   * @param t
   * @return
   */
  def inMarking(t: T): Marking[P]

  /**
   * The out marking of a transition. That is; a map of place -> arc weight
   *
   * @param t
   * @return
   */
  def outMarking(t: T): Marking[P]

  /**
   * The set of nodes (places + transitions) in the petri net.
   *
   * @return
   *   The set of nodes.
   */
  def nodes: scala.collection.Set[Either[P, T]]
}
