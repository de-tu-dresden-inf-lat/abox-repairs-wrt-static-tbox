package de.tu_dresden.inf.lat.abox_repairs.experiments.comparison;

import de.tu_dresden.inf.lat.abox_repairs.experiments.cade21.RunExperiment1;
import de.tu_dresden.inf.lat.abox_repairs.experiments.generation.IQGenerationException;
import de.tu_dresden.inf.lat.abox_repairs.experiments.generation.IQGenerator;
import de.tu_dresden.inf.lat.abox_repairs.ontology_tools.ABoxFlattener;
import de.tu_dresden.inf.lat.abox_repairs.ontology_tools.ELRestrictor;
import de.tu_dresden.inf.lat.abox_repairs.repair_manager.RepairManager;
import de.tu_dresden.inf.lat.abox_repairs.repair_manager.RepairManagerBuilder;
import de.tu_dresden.inf.lat.abox_repairs.repair_request.RepairRequest;
import de.tu_dresden.inf.lat.abox_repairs.saturation.SaturationException;
import de.tu_dresden.inf.lat.abox_repairs.seed_function.SeedFunction;
import de.tu_dresden.inf.lat.abox_repairs.tools.Timer;
import de.tu_dresden.inf.lat.abox_repairs.virtual_iq_repairs.FullOntologyIQView;
import de.tu_dresden.inf.lat.abox_repairs.virtual_iq_repairs.IQBlackbox;
import de.tu_dresden.inf.lat.abox_repairs.virtual_iq_repairs.VirtualIQRepair;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.*;

import java.io.File;

public class ComparisonAllInOne {
    public static void main(String[] args) throws OWLOntologyCreationException, SaturationException, IQGenerationException {

        System.out.println("File name: \""+args[0]+"\"");

        OWLOntology ontology = OWLManager.createOWLOntologyManager().loadOntologyFromOntologyDocument(new File(args[0].trim()));

        Timer timer = Timer.newTimer();

        timer.startTimer();
        OWLDataFactory factory = ontology.getOWLOntologyManager().getOWLDataFactory();
        new ELRestrictor(factory).restrict(ontology);
        new ABoxFlattener(factory).flatten(ontology);
        ontology.removeAxioms(ontology.getAxioms(AxiomType.DECLARATION));

        System.out.println("Preparing everything: "+timer.getTime()+" seconds.");


        double t1 = timer.getTime();

        RepairRequest repairRequest = getRepairRequest(ontology);

        RepairManager repairManager = new RepairManagerBuilder()
                .setNeedsSaturation(true)
                .setVariant(RepairManagerBuilder.RepairVariant.IQ)
                .setRepairRequest(repairRequest)
                .setOntology(ontology)
                .build();

        repairManager.initForRepairing();

        ontology = repairManager.getOntology();

        SeedFunction seedFunction = repairManager.getSeedFunction();

        timer.startTimer();
        OWLOntology repair = repairManager.performRepair();
        System.out.println("Computing repair: "+timer.getTime()+" seconds.");

        double t2 = timer.getTime();
        IQBlackbox explicitRepair = new FullOntologyIQView(repair);

        IQGenerator iqGenerator = new IQGenerator(repairManager.getWorkingCopy());
        iqGenerator.setMinDepth(1);

        OWLClassExpression iq = iqGenerator.generateIQ();

        System.out.println("QUERY: "+iq);

        timer.startTimer();
        explicitRepair.query(iq);
        System.out.println("Querying explicit repair: "+timer.getTime());
        double t3 = timer.getTime();

        IQBlackbox iqRepair = new VirtualIQRepair(ontology,seedFunction);

        timer.startTimer();
        iqRepair.query(iq);
        System.out.println("Querying virtual repair: "+timer.getTime());

        double t4 = timer.getTime();

        System.out.println("STATS: "+t1+" "+t2+" "+t3+" "+t4);

    }

    private static RepairRequest getRepairRequest(OWLOntology ontology) {
        RunExperiment1 runExperiment1 = new RunExperiment1();
        runExperiment1.initAnonymousVariableDetector(false, RepairManagerBuilder.RepairVariant.IQ);
        return runExperiment1.generateRepairRequest(ontology,0.1,0.1);
    }
}
