package io.kagera.api

import io.kagera.api.ScalaGraph._
import io.kagera.api.simple.{ SimpleExecutor, SimplePetriNetProcess, SimpleTokenGame }
import io.kagera.api.tags.Label
import scala.concurrent.{ Await, ExecutionContext, Future }
import scalax.collection.Graph
import scalax.collection.edge.WLDiEdge
import scalaz.{ @@, Tag }

package object colored {

  type Node = Either[Place, Transition]

  type Arc = WLDiEdge[Node]

  type ColoredMarking = Map[Place, Seq[Any]]

  implicit object PlaceLabeler extends Labeled[Place] {
    override def apply(p: Place): @@[String, Label] = Tag[String, Label](p.label)
  }

  implicit object TransitionLabeler extends Labeled[Transition] {
    override def apply(t: Transition): @@[String, Label] = Tag[String, Label](t.label)
  }

  def arc(t: Transition, p: Place, weight: Long, fieldName: String): Arc =
    WLDiEdge[Node, String](Right(t), Left(p))(weight, fieldName)

  def arc(p: Place, t: Transition, weight: Long, fieldName: String): Arc =
    WLDiEdge[Node, String](Left(p), Right(t))(weight, fieldName)

  implicit object ColouredMarkingLike extends MarkingLike[ColoredMarking, Place] {

    override def emptyMarking: ColoredMarking = Map.empty

    override def multiplicity(marking: ColoredMarking): Marking[Place] = marking.map { case (p, tokens) =>
      (p, tokens.size.toLong)
    }.toMap

    override def consume(from: ColoredMarking, other: ColoredMarking): ColoredMarking = other.foldLeft(from) {
      case (marking, (place, tokens)) =>
        val newTokens = marking(place).filterNot(tokens.contains)
        if (newTokens.isEmpty)
          marking - place
        else
          marking + (place -> newTokens)
    }
    override def produce(into: ColoredMarking, other: ColoredMarking): ColoredMarking = other.foldLeft(into) {
      case (marking, (place, tokens)) => marking + (place -> tokens.++(marking(place)))
    }

    override def isSubMarking(marking: ColoredMarking, other: ColoredMarking): Boolean = other.forall {
      case (place, tokens) =>
        marking
          .get(place)
          .map(markingTokens => tokens.forall(markingTokens.contains))
          .getOrElse(false)
    }
  }

  trait ColoredTokenGame extends TokenGame[Place, Transition, ColoredMarking] {
    this: PetriNet[Place, Transition] =>

    override def enabledParameters(m: ColoredMarking): Map[Transition, Iterable[ColoredMarking]] =
      enabledTransitions(m).view.map(t => t -> consumableMarkings(m)(t)).toMap

    def consumableMarkings(marking: ColoredMarking)(t: Transition): Iterable[ColoredMarking] = {
      val firstEnabled = inMarking(t).map { case (place, count) =>
        place -> marking(place).take(count.toInt)
      }
      Seq(firstEnabled)
    }

    // horribly inefficient, fix
    override def isEnabled(marking: ColoredMarking)(t: Transition): Boolean = enabledTransitions(marking).contains(t)
    override def enabledTransitions(marking: ColoredMarking): Set[Transition] =
      simple.findEnabledTransitions(this)(marking.multiplicity)
  }

  trait ColoredExecutor extends TransitionExecutor[Place, Transition, ColoredMarking] {

    this: PetriNet[Place, Transition] with TokenGame[Place, Transition, ColoredMarking] =>

    implicit val ec: ExecutionContext = ExecutionContext.global

    override def fireTransition(marking: ColoredMarking)(t: Transition, data: Option[Any]): Future[ColoredMarking] = {

      // pick the tokens
      enabledParameters(marking)(t).headOption
        .map { consume =>
          val input = consume.map { case (place, data) =>
            (place, innerGraph.connectingEdgeAB(place, t), data)
          }.toSeq

          val output = innerGraph
            .outgoingA(t)
            .map { case place =>
              (innerGraph.connectingEdgeBA(t, place), place)
            }
            .toSeq

          val transitionInput = t.createInput(input, data)

          t.apply(transitionInput).map { transitionOutput =>
            val produce = t.createOutput(transitionOutput, output)
            marking.consume(consume).produce(produce)
          }
        }
        .getOrElse {
          throw new IllegalStateException("Transition not enabled")
        }
    }
  }

  trait ColoredPetriNetProcess
      extends PetriNetProcess[Place, Transition, ColoredMarking]
      with ColoredTokenGame
      with ColoredExecutor

  def process(params: Seq[Arc]*): PetriNetProcess[Place, Transition, ColoredMarking] =
    new ScalaGraphPetriNet(Graph(params.reduce(_ ++ _): _*)) with ColoredPetriNetProcess
}
