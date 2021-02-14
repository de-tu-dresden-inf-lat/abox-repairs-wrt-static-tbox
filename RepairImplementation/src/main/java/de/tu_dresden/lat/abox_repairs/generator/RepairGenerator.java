package de.tu_dresden.lat.abox_repairs.generator;

import java.io.File;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.lang3.tuple.Pair;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLAxiom;
import org.semanticweb.owlapi.model.OWLClassAssertionAxiom;
import org.semanticweb.owlapi.model.OWLClassExpression;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLNamedIndividual;
import org.semanticweb.owlapi.model.OWLObjectProperty;
import org.semanticweb.owlapi.model.OWLObjectPropertyAssertionAxiom;
import org.semanticweb.owlapi.model.OWLObjectSomeValuesFrom;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.semanticweb.owlapi.model.parameters.Imports;

import com.google.common.base.Objects;

import de.tu_dresden.lat.abox_repairs.reasoning.ReasonerFacade;
import de.tu_dresden.lat.abox_repairs.repair_types.RepairType;
import de.tu_dresden.lat.abox_repairs.repair_types.RepairTypeHandler;

abstract public class RepairGenerator {
	protected OWLOntology ontology;
	protected OWLDataFactory factory;
	protected IRI iri;
	protected Map<OWLNamedIndividual, RepairType> seedFunction;
	protected Map<OWLNamedIndividual, Integer> individualCounter;
	protected Map<OWLNamedIndividual, OWLNamedIndividual> copyToOriginal;
	protected Map<OWLNamedIndividual, Set<OWLNamedIndividual>> originalToCopy;
	
	protected Set<OWLNamedIndividual> setOfOriginalIndividuals;
	
	protected Set<OWLNamedIndividual> setOfCollectedIndividuals;

	
	protected ReasonerFacade reasonerWithTBox;
	protected ReasonerFacade reasonerWithoutTBox;
	
	protected RepairTypeHandler typeHandler;
	
	protected OWLOntology newOntology;
	
	public RepairGenerator(OWLOntology inputOntology,
			Map<OWLNamedIndividual, RepairType> inputSeedFunction) {
		
		this.ontology = inputOntology;
		this.factory = ontology.getOWLOntologyManager().getOWLDataFactory();
		this.seedFunction = inputSeedFunction;
		this.setOfOriginalIndividuals  = ontology.getIndividualsInSignature();
		this.setOfCollectedIndividuals = new HashSet<>(setOfOriginalIndividuals);
		this.copyToOriginal = new HashMap<>();
		this.originalToCopy = new HashMap<>();
		this.individualCounter = new HashMap<>();
		
		// Initializing originalToCopy 
		for(OWLNamedIndividual originalIndividual : setOfOriginalIndividuals) {
			Set<OWLNamedIndividual> initSet = new HashSet<OWLNamedIndividual>();
			initSet.add(originalIndividual);
			individualCounter.put(originalIndividual, 0);
			originalToCopy.put(originalIndividual, initSet);
			copyToOriginal.put(originalIndividual, originalIndividual);
		}

		Optional<IRI> opt = ontology.getOntologyID().getOntologyIRI();
		this.iri = opt.get();
	}
	
	public void setReasoner(ReasonerFacade reasonerWithTBox, ReasonerFacade reasonerWithoutTBox) {
		this.reasonerWithTBox = reasonerWithTBox;
		this.reasonerWithoutTBox = reasonerWithoutTBox;
		this.typeHandler = new RepairTypeHandler(reasonerWithTBox, reasonerWithoutTBox);
	}
	
	protected abstract void generatingVariables();
	
	public abstract void repair() throws OWLOntologyCreationException;
	
	protected void generatingMatrix() throws OWLOntologyCreationException {
		OWLOntologyManager man = OWLManager.createOWLOntologyManager();
		
		newOntology = man.createOntology();
		for(OWLAxiom ax : ontology.getTBoxAxioms(Imports.INCLUDED)) {
			System.out.println("axiom " + ax);
		}
		
		newOntology.add(ontology.getTBoxAxioms(Imports.INCLUDED));
		System.out.println("\nWhen building the matrix of the repair");
		for(OWLNamedIndividual ind : setOfCollectedIndividuals) {
			OWLNamedIndividual originalInd = copyToOriginal.get(ind);
			for(OWLClassAssertionAxiom ax : ontology.getClassAssertionAxioms(originalInd)) {
				if(seedFunction.get(ind) == null || !seedFunction.get(ind).getClassExpressions().contains(ax.getClassExpression())) {
					
					OWLClassAssertionAxiom newAxiom = factory.getOWLClassAssertionAxiom(ax.getClassExpression(), ind);
					newOntology.add(newAxiom);
					System.out.println("New Class Assertion " + newAxiom);
				}
			}
		}
		
		for(OWLNamedIndividual ind1 : setOfCollectedIndividuals) {
			OWLNamedIndividual originalInd1 = copyToOriginal.get(ind1);
			
			for(OWLNamedIndividual ind2 : setOfCollectedIndividuals) {
				OWLNamedIndividual originalInd2 = copyToOriginal.get(ind2);
				
				for(OWLObjectProperty role : ontology.getObjectPropertiesInSignature()) {
					OWLObjectPropertyAssertionAxiom roleAssertion = factory
							.getOWLObjectPropertyAssertionAxiom(role, originalInd1, originalInd2);
					if(ontology.containsAxiom(roleAssertion)) {
						if(seedFunction.get(ind1) == null || seedFunction.get(ind1).getClassExpressions().isEmpty()) {
							OWLObjectPropertyAssertionAxiom newAxiom = factory.getOWLObjectPropertyAssertionAxiom(role, ind1, ind2);
							newOntology.add(newAxiom);
							
							System.out.println("New Role Assertion" + newAxiom);
						}
						else {
							RepairType type1 = seedFunction.get(ind1);
							Set<OWLClassExpression> successorSet = computeSuccessorSet(
									type1,role,originalInd2);
							
							if(successorSet.isEmpty()) {
								OWLObjectPropertyAssertionAxiom newAxiom = factory.getOWLObjectPropertyAssertionAxiom(role, ind1, ind2);
								newOntology.add(newAxiom);
								
								System.out.println("New Role Assertion " + newAxiom);
							} 
							else {
								RepairType type2 = seedFunction.get(ind2);
								if(type2 != null && reasonerWithoutTBox.isCovered(successorSet, type2.getClassExpressions())) {
									OWLObjectPropertyAssertionAxiom newAxiom = factory.getOWLObjectPropertyAssertionAxiom(role, ind1, ind2);
									newOntology.add(newAxiom);
									
									System.out.println("New Role Assertion " + newAxiom);
								}
							}
							
						}
					}
				}
			}
		}
	}
	
	protected Set<OWLClassExpression> computeSuccessorSet(RepairType inputType, OWLObjectProperty inputRole, OWLNamedIndividual ind) {
		Set<OWLClassExpression> set = new HashSet<>();
		for(OWLClassExpression concept : inputType.getClassExpressions()) {
			if(concept instanceof OWLObjectSomeValuesFrom &&
				((OWLObjectSomeValuesFrom) concept).getProperty().equals(inputRole) && 
				reasonerWithTBox.instanceOf(ind, ((OWLObjectSomeValuesFrom) concept).getFiller())) {
					set.add(((OWLObjectSomeValuesFrom) concept).getFiller());
			}
		}
		
		return set;
	}
	
	
	protected abstract void makeCopy(OWLNamedIndividual ind, RepairType typ);
	
	
	public OWLOntology getRepair() {
		
		return newOntology;
	}
}