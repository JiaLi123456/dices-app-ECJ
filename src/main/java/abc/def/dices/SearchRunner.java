package abc.def.dices;
import ec.EvolutionState;
import ec.Evolve;
import ec.Individual;
import ec.gp.GPIndividual;
import ec.simple.SimpleStatistics;
import ec.simple.SimpleEvolutionState;
import ec.util.DataPipe;
import ec.util.Output;
import ec.multiobjective.MultiObjectiveFitness;
import ec.util.ParameterDatabase;
import org.onosproject.net.Link;
import org.onosproject.net.Path;
import org.onosproject.net.host.HostService;
import org.onosproject.net.link.LinkService;
import org.onosproject.net.topology.TopologyService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;

public class SearchRunner {
    private final Logger log = LoggerFactory.getLogger(getClass());

    private TopologyService topologyService;
    private LinkService linkService;
    private MonitorUtil monitorUtil;
    private MonitorPacketLoss monitorPacketLoss;
    private HostService hostService;

    private Map<SrcDstPair,List<Link>> solutions=new HashMap<>();

    CongestionProblem congestionProblem;

    private EvolutionState state;
    private Individual solution;
    private Map<Link,Double> newWeight;
    private  boolean flag;

    public SearchRunner(TopologyService topologyService, LinkService linkService, HostService hostService, MonitorUtil monitorUtil, MonitorPacketLoss monitorPacketLoss,boolean firstOrNot) {
        this.topologyService = topologyService;
        this.linkService = linkService;
        this.hostService = hostService;
        this.monitorUtil = monitorUtil;
        this.monitorPacketLoss = monitorPacketLoss;
        this.flag=firstOrNot;
    }

    public void search() {
        log.info("Search runner-Search");
        ParameterDatabase child = new ParameterDatabase();
        if (!flag) {
            log.info("not the first time");
            File parameterFile = new File("./parameters.params");
            ParameterDatabase dbase = null;
            try {
                dbase = new ParameterDatabase(parameterFile,
                        new String[]{"-file", parameterFile.getCanonicalPath()});
            } catch (IOException e) {
                e.printStackTrace();
            }
            try {
                ParameterDatabase copy = (ParameterDatabase) (DataPipe.copy(dbase));
            } catch (IOException e) {
                e.printStackTrace();
            } catch (ClassNotFoundException e) {
                e.printStackTrace();
            }

            child.addParent(dbase);
        }else {
            log.info("the first time！");
            flag=false;
            try {
                File infile = new File("./start.in");
                infile.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
            File parameterFile = new File("./parameters2.params");
            ParameterDatabase dbase = null;
            try {
                dbase = new ParameterDatabase(parameterFile,
                        new String[]{"-file", parameterFile.getCanonicalPath()});
            } catch (IOException e) {
                e.printStackTrace();
            }
            try {
                ParameterDatabase copy = (ParameterDatabase) (DataPipe.copy(dbase));
            } catch (IOException e) {
                e.printStackTrace();
            } catch (ClassNotFoundException e) {
                e.printStackTrace();
            }
            child.addParent(dbase);
        }

            Output out = Evolve.buildOutput();
            long initTime = System.currentTimeMillis();
            log.warn(String.valueOf(initTime));
            Thread t = Thread.currentThread();

            SimpleEvolutionState evaluatedState = (SimpleEvolutionState) Evolve.initialize(child, (int) t.getId(), out);

            state = evaluatedState;
            evaluatedState.startFresh(this);
            int result = EvolutionState.R_NOTDONE;
            while (result == EvolutionState.R_NOTDONE) {
                result = evaluatedState.evolve();
            }

        ////////////////////////////////////////////
        try {
            //write the last generation to
            PrintWriter writer = new PrintWriter("./start.in");
            evaluatedState.population.subpops.get(0).printSubpopulation(evaluatedState,writer);
//            System.out.println(evaluatedState.population.subpops.get(0).individuals.size());
//            System.out.println(writer.toString());
            writer.close();
        } catch (FileNotFoundException e) {
            //e.printStackTrace();
            System.out.println(e.toString());
        }

        ////////////////////////////////////////////////////////////////////
        ///////////////////////////////////////////////////////////////////

        ArrayList<Individual> inds =MultiObjectiveFitness.getSortedParetoFront(state.population.subpops.get(0).individuals);

            //Individual[] inds = evaluatedState.population.subpops.get(0).species.fitness.;
       // System.out.println(inds);
       // System.out.println(inds.toString());

            log.info("number of pareto fronts is: "+ inds.size());
            System.out.println("number of pareto fronts is: "+ inds.size());

            Individual solutionTree=null;

            if (inds.size()<1){
                log.error("no answers yet as there are no pareto fronts");
                System.out.println("no answers yet as there are no pareto fronts");
                //inds=evaluatedState.population.subpops.get(0).individuals;
                return;
            }

//                int number=0;
//                for (Individual ind : inds){
//                    log.info("inds|||"+number+":"+((GPIndividual)ind).toGPString());
//                    number=number+1;
//                }

            ///////////////////////////////////////
            /////////////////////////////////////////
            /////////////////////////////////////////
            solutionTree=getKneeSolution(inds);
            if (solutionTree==null){
                long computingTime = System.currentTimeMillis() - initTime;
                log.error("no valid solution");
                log.info("Search time (ms): " + computingTime+"， one search finished.");
                System.out.println("no valid solution!");

            }
            else {
                solution = solutionTree;
                System.out.println(((GPIndividual) solutionTree).toGPString());
                System.out.println(solutionTree.fitness.fitnessToString());
                solutionTree.printIndividualForHumans(state, 0);
                ///////////////////////////////////////////
                ///////////////////////////////////////////
                //////////////////////////////////////////////
                congestionProblem = (CongestionProblem) evaluatedState.evaluator.p_problem;
                List<SrcDstPair> srcDstPairs = congestionProblem.getSrcDstPair();
                Map<SrcDstPair, Path> newMap = congestionProblem.computeLink(evaluatedState, solutionTree, 0);
                newWeight = congestionProblem.getNewWeight();
                for (SrcDstPair pair : srcDstPairs) {
                    solutions.put(pair, newMap.get(pair).links());
                }
                long computingTime = System.currentTimeMillis() - initTime;
                // System.out.println("pring all new links"+(linkService.getLinkCount()));
                for (Link l2 : linkService.getLinks()) {
                    System.out.println("newLinkWeight: " + l2.src().toString() + " : " + l2.dst().toString() + " : " + newWeight.get(l2));
                }

                log.info("Search time (ms): " + computingTime + "， one search finished.");
            }

    }
    public Map<Link,Double> getNewWeight(){
        return newWeight;
    }

    public Map<Link,Integer> getWeightUsingSolutionTree(Individual tree){
        return ((CongestionProblem)congestionProblem).getIndividualResultWeight(
                state,tree,0
        );
    }
    public Map<SrcDstPair, List<Link>> getCurrentLinkPath() {
        return ((CongestionProblem)congestionProblem).getCurrentLinkPath();
    }

    public Map<SrcDstPair, List<Link>> getSolutionLinkPath() {
        //System.out.println(solutions);
        return solutions;
    }

    public List<Link> findLCS(List<Link> x, List<Link> y) {
        return ((CongestionProblem)congestionProblem).findLCS(x, y);
    }

    public Individual getKneeSolution(ArrayList<Individual> results) {
        log.info("getKneeSolution");
        if (results.size() == 0) {
            log.error("No results!");
            return null;
        }
        List<Individual> validSolutions=new ArrayList<>();

        OUT: for (Individual s : results) {
            MultiObjectiveFitness f = ((MultiObjectiveFitness)s.fitness);
            double[] fitness=f.getObjectives();
            log.info("fitness: {},{},{}",fitness[0],fitness[1],fitness[2]);
            //log.info("fitness: {},{}",fitness[0],fitness[1]);
            for (int i = 0; i < fitness.length; i++) {
                if (fitness[i] == Config.LARGE_NUM) {
                    break OUT;
                }
            }
            validSolutions.add(s);
        }

        Individual kneeSolution = null;
        int nobj = 3;
       // int nobj = 2;
        double minValue[] = new double[nobj];
        double maxValue[] = new double[nobj];
        for (int i = 0; i < nobj; i++) {
            minValue[i] = Double.MAX_VALUE;
            maxValue[i] = 0;
        }

        double value[] = new double[nobj];
        for (int i = 0; i < validSolutions.size(); i++) {
            for (int oIdx = 0; oIdx < nobj; oIdx++) {
                value[oIdx] =( (MultiObjectiveFitness)validSolutions.get(i).fitness).getObjectives()[oIdx];
                if (minValue[oIdx] > value[oIdx]) {
                    minValue[oIdx] = value[oIdx];
                }
                if (maxValue[oIdx] < value[oIdx]) {
                    maxValue[oIdx] = value[oIdx];
                }
            }
        }

        double minDist = Double.MAX_VALUE;
        for (int i = 0; i < validSolutions.size(); i++) {
            double dist = 0D;
            for (int oIdx = 0; oIdx < nobj; oIdx++) {
                value[oIdx] = ( (MultiObjectiveFitness)validSolutions.get(i).fitness).getObjectives()[oIdx];
                double norm = 1;
                if (maxValue[oIdx] > minValue[oIdx]) {
                    norm = (value[oIdx] - minValue[oIdx]) / (maxValue[oIdx] - minValue[oIdx]);
                }
                dist += Math.pow(norm, 2);
            }
            dist = Math.sqrt(dist);
            if (minDist > dist) {
                minDist = dist;
                kneeSolution = validSolutions.get(i);
            }
        }

        if (kneeSolution == null) {
            log.error("Knee solution == null");
            return null;
        }
        return kneeSolution;
    }

    public boolean isSolvable() {
        if (solution == null) {
            return false;
        }
        return true;
    }

    public TopologyService getTopologyService(){
        return this.topologyService;
    }

    public LinkService getLinkService() {
        return linkService;
    }

    public HostService getHostService() {
        return hostService;
    }
    public MonitorUtil getMonitorUtil(){
        return monitorUtil;
    }
    public MonitorPacketLoss getMonitorPacketLoss(){
        return monitorPacketLoss;
    }
    public  Individual getSolution(){
        return solution;
    }
}
