package de.tu_dresden.inf.lat.abox_repairs.repair_manager;

import de.tu_dresden.inf.lat.abox_repairs.generator.*;
import de.tu_dresden.inf.lat.abox_repairs.ontology_tools.ClassExpressionNormalizer;
import de.tu_dresden.inf.lat.abox_repairs.ontology_tools.OntologyPreparations;
import de.tu_dresden.inf.lat.abox_repairs.ontology_tools.RepairRequestNormalizer;
import de.tu_dresden.inf.lat.abox_repairs.reasoning.ReasonerFacade;
import de.tu_dresden.inf.lat.abox_repairs.repair_request.RepairRequest;
import de.tu_dresden.inf.lat.abox_repairs.saturation.ABoxSaturator;
import de.tu_dresden.inf.lat.abox_repairs.saturation.CanonicalModelGenerator;
import de.tu_dresden.inf.lat.abox_repairs.saturation.ChaseGenerator;
import de.tu_dresden.inf.lat.abox_repairs.saturation.DummySaturator;
import de.tu_dresden.inf.lat.abox_repairs.ontology_tools.CycleChecker;
import de.tu_dresden.inf.lat.abox_repairs.seed_function.SeedFunction;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.semanticweb.owlapi.model.*;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;

public class RepairManagerBuilder {

    private static Logger logger = LogManager.getLogger(RepairManagerBuilder.class);
    private boolean trustRepairRequests=false;

    /**
     * TODO
     *
     * What is the difference between IQ and IQ2?
     * Apparently for the CADE paper, we used IQ for the optimized iq repairs.
     * What is the purpose of the other one?
     */
    public enum RepairVariant {IQ, IQ2, CQ, CANONICAL_IQ, CANONICAL_CQ}

    //private final RepairVariant variant;

    public static EnumSet<RepairVariant> IQ_ANY = EnumSet.of(RepairVariant.IQ, RepairVariant.IQ2, RepairVariant.CANONICAL_IQ);
    public static EnumSet<RepairVariant> CQ_ANY = EnumSet.of(RepairVariant.CQ, RepairVariant.CANONICAL_CQ);
    public static EnumSet<RepairVariant> CANONICAL_ANY = EnumSet.of(RepairVariant.CANONICAL_IQ, RepairVariant.CANONICAL_CQ);

    private OWLOntology ontology, workingCopy;
    private ReasonerFacade reasonerWithoutTBox, reasonerWithTBox;
    private RepairGenerator repairGenerator;
    private ABoxSaturator saturator;
    private RepairRequest repairRequest, normalizedRepairRequest;
    private SeedFunction seedFunction=null;
    private boolean saturate = true;

    private RepairVariant variant;

    public RepairManagerBuilder trustRepairRequests(boolean value) {
        this.trustRepairRequests=value;
        return this;
    }

    public RepairManagerBuilder setVariant(RepairVariant variant) {
        this.variant = variant;
        return this;
    }

    public RepairManagerBuilder setNeedsSaturation(boolean saturate) {
        this.saturate = saturate;
        return this;
    }

    public RepairManagerBuilder setRepairRequest(RepairRequest repairRequest) {
        this.repairRequest = repairRequest;
        return this;
    }

    public RepairManagerBuilder setOntology(OWLOntology ontology) {
        this.ontology = ontology;
        return this;
    }

    public RepairManagerBuilder setSeedFunction(SeedFunction seedFunction) {
        this.seedFunction=seedFunction;
        return this;
    }

    public RepairManager build() throws OWLOntologyCreationException {

        System.out.println("Bulding...");

        this.workingCopy = OntologyPreparations.prepare(ontology, false, variant);


        try {
            workingCopy.getOWLOntologyManager().saveOntology(workingCopy, new FileOutputStream(new File("el-fragment.owl")));
        } catch (OWLOntologyStorageException e) {
            e.printStackTrace();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }

        System.out.println("Normalizing repair request...");

        if(repairRequest!=null)
            this.normalizedRepairRequest = RepairRequestNormalizer.normalizeRepairRequest(ontology.getOWLOntologyManager(), repairRequest);

        System.out.println("initialising reasoner facades...");
        initReasonerFacades();
        System.out.println("done initializing");

        if (!trustRepairRequests && repairRequest!=null && !RepairRequest.checkValid(normalizedRepairRequest, reasonerWithTBox)) {
            throw new IllegalArgumentException("Invalid repair request.");
        }

        System.out.println("fixing saturator...");
        fixSaturator();
        System.out.println("fixing repair generator...");
        fixRepairGenerator();

        if(seedFunction==null)
            return new RepairManager(ontology, workingCopy, reasonerWithoutTBox, reasonerWithTBox, repairGenerator, saturator, normalizedRepairRequest);
        else
            return new RepairManager(ontology, workingCopy, reasonerWithoutTBox, reasonerWithTBox, repairGenerator, saturator, seedFunction);

    }

    private void initReasonerFacades() throws OWLOntologyCreationException {
        long start = System.nanoTime();

        if (ontology == null || (repairRequest==null && seedFunction==null))
            throw new IllegalArgumentException("Ontology and repair request have to be set first!");

        Set<OWLClassExpression> additionalExpressions =
                repairRequest==null
                        ? seedFunction.getNestedClassExpression()
                        : normalizedRepairRequest.getNestedClassExpressions();

        logger.info("Init reasoner facade without TBox");
        additionalExpressions.addAll(workingCopy.getNestedClassExpressions());
        reasonerWithoutTBox = ReasonerFacade.newReasonerFacadeWithoutTBox(additionalExpressions, ontology.getOWLOntologyManager());

        logger.info("Init reasoner facade with TBox");
        reasonerWithTBox = ReasonerFacade.newReasonerFacadeWithTBox(workingCopy, additionalExpressions);

        logger.info("Initialising reasoner facades took " + ((double) System.nanoTime() - start) / 1_000_000_000);
    }

    private void fixSaturator() {
        if (CQ_ANY.contains(variant) && new CycleChecker(reasonerWithTBox).cyclic()) {
            throw new IllegalArgumentException("Ontology is cyclic - chase cannot be computed!");
        }

        if (!saturate)
            saturator = new DummySaturator();
        else if (IQ_ANY.contains(variant))
            saturator = new CanonicalModelGenerator(reasonerWithTBox);
        else {
            assert CQ_ANY.contains(variant);
            saturator = new ChaseGenerator();
        }
    }

    private void fixRepairGenerator() {
        if (CANONICAL_ANY.contains(variant))
            repairGenerator = new CanonicalRepairGenerator(workingCopy);
        else if (variant.equals(RepairVariant.IQ))
            repairGenerator = new IQRepairGenerator(workingCopy);
        else if (variant.equals(RepairVariant.IQ2))
            repairGenerator = new IQRepairGenerator2(workingCopy);
        else {
            assert variant.equals(RepairVariant.CQ);
            repairGenerator = new CQRepairGenerator(workingCopy);
        }

        repairGenerator.setReasoner(reasonerWithTBox,reasonerWithoutTBox);
    }
}
