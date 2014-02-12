package epic.parser.models

import epic.parser._
import epic.lexicon.Lexicon
import epic.trees._
import epic.trees.annotations.{Xbarize, TreeAnnotator}
import java.io.File
import epic.util.SafeLogging
import breeze.linalg.{Counter2, Axis, sum, Counter}
import epic.parser.projections.GrammarRefinements
import epic.trees.BinaryRule
import epic.trees.UnaryRule
import epic.trees.TreeInstance
import epic.trees.annotations.Xbarize
import epic.trees.TreeInstance
import epic.parser.ParserParams.XbarGrammar

/**
 * @author jda
 */
class BerkeleyModel[Label,RefinedLabel,Word](val refinedGrammar: SimpleRefinedGrammar[Label,RefinedLabel,Word]) {

  val parser = Parser(refinedGrammar)

  def split: BerkeleyModel[Label,RefinedLabel,Word] = ???

}

object BerkeleyModel {

  type Word = String
  type SplitLabel = (AnnotatedLabel, Int)

  def makeInitial(trainTrees: IndexedSeq[TreeInstance[AnnotatedLabel, Word]],
                  initialAnnotator: TreeAnnotator[AnnotatedLabel, Word, AnnotatedLabel]):  BerkeleyModel[AnnotatedLabel,SplitLabel,Word] = {

    val annotatedTrees = trainTrees map initialAnnotator
    val (xbarGrammar, xbarLexicon) = XbarGrammar().xbarGrammar(annotatedTrees)

    // get all productions in the training set
    // will be used to set initial weights
    val (baseWordCounts, baseBinaryCounts, baseUnaryCounts) = GenerativeParser.extractCounts(annotatedTrees)

    def toRefined(label: AnnotatedLabel): Seq[(AnnotatedLabel, Int)] = List((label, 0))
    def fromRefined(label: (AnnotatedLabel, Int)): AnnotatedLabel = label._1
    val presplitLabels = xbarGrammar.labelIndex.map(l => l -> toRefined(l)).toMap
    def splitRule[L, SL](rule: Rule[L], split: L=>Seq[SL]): Seq[Rule[SL]] = rule match {
      case BinaryRule(a, b, c) => for (aa <- split(a); bb <- split(b); cc <- split(c)) yield BinaryRule(aa, bb, cc)
      // note that because everything only gets a single refinement, we don't care whether a == b
      case UnaryRule(a, b, chain) => for(aa <- split(a); bb <- split(b)) yield UnaryRule(aa, bb, chain)
    }

    val baseGrammar: BaseGrammar[AnnotatedLabel] = BaseGrammar(annotatedTrees.head.tree.label,
                                                               baseBinaryCounts,
                                                               baseUnaryCounts)
    val firstRefinements = GrammarRefinements(xbarGrammar,
                                              baseGrammar,
                                              (_: AnnotatedLabel).baseAnnotatedLabel)
    val stateSplitRefinements = GrammarRefinements(baseGrammar,
                                                   toRefined _,
                                                   {splitRule(_: Rule[AnnotatedLabel], presplitLabels)},
                                                   fromRefined _)
    val finalRefinements = firstRefinements compose stateSplitRefinements

    def wordCountKeyRefiner = {(tag: AnnotatedLabel, word: Word) => (toRefined(tag).head, word)}.tupled
    def ruleCountKeyRefiner[R] = {(tag: AnnotatedLabel, rule: Rule[AnnotatedLabel]) => (toRefined(tag).head, splitRule(rule, presplitLabels).head.asInstanceOf[R])}.tupled

    //val splitWordCounts = Counter2[SplitLabel, Word, Double](baseWordCounts.mapPairs((k, v) => (wordCountKeyRefiner(k), v)))
    //val splitBinaryCounts = Counter2[SplitLabel, BinaryRule[SplitLabel], Double](baseBinaryCounts.mapPairs((k, v) => (ruleCountKeyRefiner(k), v)))
    //val splitUnaryCounts = Counter2[SplitLabel, UnaryRule[SplitLabel], Double](baseUnaryCounts.mapPairs((k, v) => (ruleCountKeyRefiner(k), v)))

    val splitWordCounts = Counter2[SplitLabel, Word, Double](for {
      (k, v) <- baseWordCounts.iterator
      rk = wordCountKeyRefiner(k)
    } yield (rk._1, rk._2, v))
    val splitBinaryCounts = Counter2[SplitLabel, BinaryRule[SplitLabel], Double](for {
      (k, v) <- baseBinaryCounts.iterator
      rk = ruleCountKeyRefiner(k)
    } yield (rk._1, rk._2, v))
    val splitUnaryCounts = Counter2[SplitLabel, UnaryRule[SplitLabel], Double](for {
      (k, v) <- baseUnaryCounts.iterator
      rk = ruleCountKeyRefiner(k)
    } yield (rk._1, rk._2, v))

    // val splitBinaryCounts = Counter2[SplitLabel, BinaryRule[SplitLabel], Double](for {
    //   (k, v) <- baseBinaryCounts.iterator
    // } yield (ruleCountKeyRefiner(k), v))
    // val splitUnaryCounts = Counter2[SplitLabel, UnaryRule[SplitLabel], Double](for {
    //   (k, v) <- baseUnaryCounts.iterator
    // } yield (ruleCountKeyRefiner(k), v))

    val refinedGrammar = RefinedGrammar.generative(baseGrammar, xbarLexicon, finalRefinements, splitBinaryCounts, splitUnaryCounts, splitWordCounts)

    new BerkeleyModel(refinedGrammar)
  }

}
