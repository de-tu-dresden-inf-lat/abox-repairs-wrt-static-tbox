package de.tu_dresden.inf.lat.abox_repairs.saturation;


import java.io.IOException;
import java.util.*;


import de.tu_dresden.inf.lat.abox_repairs.ontology_tools.FreshOWLEntityFactory;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.semanticweb.owlapi.model.*;
import org.semanticweb.rulewerk.core.model.api.*;
import org.semanticweb.rulewerk.core.reasoner.Algorithm;
import org.semanticweb.rulewerk.core.reasoner.KnowledgeBase;
import org.semanticweb.rulewerk.owlapi.OwlToRulesConverter;
import org.semanticweb.rulewerk.reasoner.vlog.VLogReasoner;

/**
 * Computes the chase for a given ontology, adding all derived ABox assertions to the given ontology.
 *
 * @author Patrick Koopmann
 */
public class ChaseGenerator implements ABoxSaturator {

	private static Logger logger = LogManager.getLogger(ChaseGenerator.class);

	private OWLDataFactory factory;
	private OWLOntology ontology;

	private int addedAssertions = 0;
	private int addedIndividuals = 0;
	private double duration = 0.0;

	public void saturate(OWLOntology ontology) throws SaturationException {

		long start = System.nanoTime();

		this.ontology = ontology;

		int indsBefore = ontology.getIndividualsInSignature().size() + ontology.getAnonymousIndividuals().size();

		factory = ontology.getOWLOntologyManager().getOWLDataFactory();

		final OwlToRulesConverter owlToRulesConverter = new OwlToRulesConverter();
		owlToRulesConverter.addOntology(ontology);


//		System.out.println("TBox axioms as rules:");
		final Set<Rule> rules = owlToRulesConverter.getRules();
//		for (final Rule rule : rules) {
//			System.out.println(" - rule: " + rule);
//		}
//		System.out.println();
//		System.out.println("Facts:");
		final Set<Fact> facts = owlToRulesConverter.getFacts();
//		for (final PositiveLiteral fact : facts) {
//			System.out.println(" - fact: " + fact);
//		}
//		System.out.println();

		final KnowledgeBase kb = new KnowledgeBase();
		kb.addStatements(new ArrayList<>(owlToRulesConverter.getRules()));
		kb.addStatements(owlToRulesConverter.getFacts());

		try (VLogReasoner reasoner = new VLogReasoner(kb)) {

			reasoner.setAlgorithm(Algorithm.RESTRICTED_CHASE);

//			System.out.println("Reasoning default algorithm: " + reasoner.getAlgorithm());
			try {
				reasoner.reason();
			} catch (IOException e) {
				throw new SaturationException("exception during vlog reasoning", e);
			}

			addedAssertions = 0;
			reasoner.getInferences().forEach(fact -> {
			//	System.out.println("Fact: "+fact);
			//	System.out.println("Abox: "+fact2Axiom(fact));
				Optional<OWLAxiom> axiom = fact2Axiom(fact);
				if(axiom.isPresent() && !ontology.containsAxiom(axiom.get())){
					//System.out.println("Newly derived: "+axiom);
					ontology.add(axiom.get());
					addedAssertions++;
				}
			});
		}

		int indsAfter = ontology.getIndividualsInSignature().size() + ontology.getAnonymousIndividuals().size();

		addedIndividuals = indsAfter - indsBefore;

		duration = ((double)(System.nanoTime() - start)/1_000_000_000);

		logger.info("Saturation took "+duration);
	}

	@Override
	public int addedAssertions() {
		return addedAssertions;
	}

	@Override
	public int addedIndividuals() {
		return addedIndividuals;
	}

	@Override
	public double getDuration() {
		return duration;
	}

	/* We should rather use an instance of OWLAnonymousIndividual if arguments.get(0).isVariable() is true, and use an
	*  instance of OWLNamedIndividual only in the remaining cases.
	*  A benefit is that then there is a clear distinction between named individuals (individual names in the paper)
	*  and anonymous individuals (variables in the paper), i.e., the class AnonymousVariableDetector is then not
	*  required anymore.
	*  To make these instances of OWLAnonymousIndividual accessible, the reasoner facade can add fresh instances of
	*  OWLNamedIndividual plus a corresponding instance of OWLSameIndividualAxiom---just like it was done to make the
	*  complex, anonymous instances of OWLClassExpression accessible. */
	private Optional<OWLAxiom> fact2Axiom(Fact fact) {
		List<Term> arguments = fact.getArguments();
		Predicate predicate = fact.getPredicate();
		if(arguments.size()==1){
//			if (FreshOWLEntityFactory.isFreshOWLClassName(predicate.getName()))
			if (FreshOWLEntityFactory.FreshOWLClassFactory.of(ontology).isNameOfFreshEntity(predicate.getName()))
				return Optional.empty();
			else
				return Optional.of(factory.getOWLClassAssertionAxiom(
					factory.getOWLClass(toIRI(predicate)),
					toOWLIndividual(arguments.get(0))
				));
		} else {
			assert arguments.size()==2;

			return Optional.of(factory.getOWLObjectPropertyAssertionAxiom(
				factory.getOWLObjectProperty(toIRI(predicate)),
				toOWLIndividual(arguments.get(0)),
				toOWLIndividual(arguments.get(1))));
		}
	}

	/* Can be used for debugging if necessary. */
	private Map<Term, OWLIndividual> termToIndividualMap = new HashMap<>();

	private OWLIndividual toOWLIndividual(Term term) {
		if (term.isConstant()) {
			return termToIndividualMap.computeIfAbsent(term, __ -> factory.getOWLNamedIndividual(toIRI(term)));
		} else if (term.isVariable() || term.getType().equals(TermType.NAMED_NULL)) {
			return termToIndividualMap.computeIfAbsent(term, __ -> factory.getOWLAnonymousIndividual());
		} else {
			throw new IllegalArgumentException("The term " + term + " is neither a constant nor a variable.");
		}
	}

	private static IRI toIRI(Predicate predicate) {
		return IRI.create(predicate.getName());
	}

	private static IRI toIRI(Term term) {
		return IRI.create(term.getName());
	}

	public OWLOntology getOntology() {
		return ontology;
	}
}
