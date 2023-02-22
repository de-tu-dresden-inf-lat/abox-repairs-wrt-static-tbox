package de.tu_dresden.inf.lat.abox_repairs.experiments.generation;

import de.tu_dresden.inf.lat.abox_repairs.repair_request.RepairRequest;
import org.semanticweb.owlapi.model.*;

import java.util.*;
import java.util.stream.Collectors;

public class ComplexRepairRequestGenerator {

    private final Random random;
    private final OWLOntology ontology;
    private final List<OWLNamedIndividual> individuals;
    private final IQGenerator iqGenerator;

    public ComplexRepairRequestGenerator(OWLOntology ontology){
        this(ontology, new Random());
    }

    public ComplexRepairRequestGenerator(OWLOntology ontology, Random random){
        this.random=random;
        this.ontology=ontology;
        this.individuals = new ArrayList(ontology.getIndividualsInSignature());
        this.iqGenerator = new IQGenerator(ontology, random);
    }

    /**
     * access internal iq generator to adapt parameters
     */
    public IQGenerator getIqGenerator(){
        return this.iqGenerator;
    }

    /**
     * Create a random repair request that assigns to the given proportion of individuals a complex EL concept,
     * generated by the interal IQ generator
     * @param proportionIndividuals
     * @return
     */
    public RepairRequest generateRepairRequest(double proportionIndividuals, int conceptsPerIndividual)
            throws IQGenerationException {
        RepairRequest request = new RepairRequest();

        List<OWLClass> classList = ontology.classesInSignature().collect(Collectors.toList());

        Set<OWLNamedIndividual> individuals = randomIndividuals(ontology, proportionIndividuals);

        for(OWLNamedIndividual individual: individuals) {
            Set<OWLClassExpression> expressions = new HashSet<>();
            for(int i=0; i< conceptsPerIndividual; i++){
                expressions.add(iqGenerator.generateIQ());
            }
            request.put(individual, expressions);
        }

        return request;
    }


    private Set<OWLNamedIndividual> randomIndividuals(OWLOntology ontology, double proportion) {
        Set<OWLNamedIndividual> result = new HashSet<>();

        System.out.println("Requests for "+((int)(proportion*individuals.size()))+" individual names.");

        for(int i=0; i<proportion*individuals.size(); i++) {
            OWLNamedIndividual ind = individuals.get(random.nextInt(individuals.size()));
            while(result.contains(ind)){
                ind = individuals.get(random.nextInt(individuals.size()));
            }
            result.add(ind);
        }
        return result;
    }
}
