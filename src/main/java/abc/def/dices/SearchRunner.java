package abc.def.dices;
import ec.EvolutionState;
import ec.Evolve;
import ec.Individual;
import ec.Subpopulation;
import ec.gp.GPIndividual;
import ec.gp.koza.KozaFitness;
import ec.gp.koza.KozaShortStatistics;
import ec.simple.SimpleStatistics;
import ec.simple.SimpleEvolutionState;
import ec.util.DataPipe;
import ec.util.Output;
import ec.multiobjective.MultiObjectiveFitness;
import ec.util.ParameterDatabase;
import org.onlab.packet.MacAddress;
import org.onosproject.net.DeviceId;
import org.onosproject.net.Link;
import org.onosproject.net.Path;
import org.onosproject.net.flow.FlowEntry;
import org.onosproject.net.host.HostService;
import org.onosproject.net.link.LinkService;
import org.onosproject.net.topology.TopologyService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.*;

public class SearchRunner {
    private final Logger log = LoggerFactory.getLogger(getClass());

    private TopologyService topologyService;
    private LinkService linkService;
    private MonitorUtil monitorUtil;
    private HostService hostService;

    private Map<SrcDstPair,List<Link>> solutions=new HashMap<>();

    CongestionProblem congestionProblem;

    private EvolutionState state;
    private Individual solution;
    private Map<Link,Integer> newWeight;
    private  boolean flag=true;
    private  List<String>indsString;
    private Map<SrcDstPair,Path>solutionPath=null;
    private int GPRound;


    public SearchRunner(TopologyService topologyService, LinkService linkService, HostService hostService, MonitorUtil monitorUtil, int GPRound,boolean dflag) {
        this.topologyService = topologyService;
        this.linkService = linkService;
        this.hostService = hostService;
        this.monitorUtil = monitorUtil;
        this.flag=dflag;
        this.indsString=new ArrayList<>();
        //this.paretoString=new ArrayList<>();
        this.GPRound=GPRound;

    }

    public void search() {
        Map<SrcDstPair,List<Link>> oldPath=getCurSDPath();
        log.info("Search runner-Search");
        long initTime = System.currentTimeMillis();
        log.warn(String.valueOf(initTime));
        ParameterDatabase child = new ParameterDatabase();
        //////////////////////
       // flag=true;
        //////////////////////
        if (Config.randomSearchFlag==true){
            //System.out.println("random search");
            File parameterFile = new File("./parameters3.params");
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
        }else if (Config.singleObjective){
            if (!flag) {
                log.info("start from file");
                //System.out.println("half start from file");
                File parameterFile = new File("./parameters5.params");
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
            } else {
                log.info("start from random");
               // System.out.println("start from random");
                try {
                    File infile = new File("./start.in");
                    infile.createNewFile();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                File parameterFile = new File("./parameters4.params");
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
        }else {
            if (!flag) {
                log.info("start from file");
                // System.out.println("start from file");
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
            } else {
                log.info("start from random");
                // System.out.println("start from random");
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
        }

        ////////////////////////////////////////////
        ///////////////////////////////////////////////
        ///////////////////////////////////////////////
        long time1 = System.currentTimeMillis();

        //System.out.println("time1： "+(time1-initTime)+", "+getCurrentTime());

            Output out = Evolve.buildOutput();

            Thread t = Thread.currentThread();

            SimpleEvolutionState evaluatedState = (SimpleEvolutionState) Evolve.initialize(child, (int) t.getId(), out);

            state = evaluatedState;
        long time2 = System.currentTimeMillis();
       // System.out.println("time2： "+(time2-time1)+", "+getCurrentTime());
            evaluatedState.startFresh(this);
            int result = EvolutionState.R_NOTDONE;
            //////////////////////////////////////////////
            while (result == EvolutionState.R_NOTDONE) {
                /////////////////////////////////////////////////////////////
                ////////////////////////////////////////////////////////////
                //for duplicate replacement
               /* ArrayList<Individual> ti=evaluatedState.population.subpops.get(0).individuals;
                LinkedHashSet<Individual> hashSet = new LinkedHashSet<>(ti);
                boolean tflag=false;
                if (hashSet.size()!=ti.size())
                    tflag=true;

                if (tflag==true) {

                    ArrayList<Individual> tii=new ArrayList<>();
                    int idSize=ti.size();
                    int index=0;
                    while ( index<idSize){
                        Individual i = evaluatedState.population.subpops.get(0).individuals.get(index);
                        if (tii.contains(i)){
                            int count1=0;
                            while ((count1<Config.duplicateRetry)){
                                evaluatedState.population.subpops.get(0).individuals.set(index,evaluatedState.population.subpops.get(0).species.newIndividual(evaluatedState,0));
                                count1++;
                            }

                        }
                        index++;
                        tii.add(i);
                    }
                }*/
                /////////////////////////////////////////////////////////////
              /*  ArrayList<Individual> t_inds=evaluatedState.population.subpops.get(0).individuals;
                int index=0;
                int t_size=t_inds.size();
                while (index<t_size){
                    Individual t_ind=evaluatedState.population.subpops.get(0).individuals.get(index);
                    if (t_ind.size()<=4){
                        int count1=0;
                        while ((count1<Config.duplicateRetry)){
                            evaluatedState.population.subpops.get(0).individuals.set(index,evaluatedState.population.subpops.get(0).species.newIndividual(evaluatedState,0));
                            count1++;
                        }

                    }

                }*/
                ////////////////////////////////////////////////////////////
                result=evaluatedState.evolve();
            }
            //////////////////////////////////////////////
        //long time3 = System.currentTimeMillis();
        //System.out.println("time3: "+(time3-time2)+", "+getCurrentTime());
        Individual solutionTree=null;
            ArrayList<Individual>pops=new ArrayList<>();
        if(!Config.singleObjective) {
            ArrayList<Individual> inds = MultiObjectiveFitness.getSortedParetoFront(state.population.subpops.get(0).individuals);
            log.info("number of pareto fronts is: "+ inds.size());

            if (inds.size()<1){
                log.error("no answers yet as there are no pareto fronts");
                return;
            }
            solutionTree=getKneeSolution(inds);
        }else {
            ArrayList<Individual> subpop0=evaluatedState.population.subpops.get(0).individuals;

            for (Individual i1 : subpop0){
                if (!pops.contains(i1)){
                    pops.add(i1);
                }
            }
            if (!flag) {
                ArrayList<Individual> subpop1 = evaluatedState.population.subpops.get(1).individuals;
                for (Individual i2 : subpop1) {
                    if (!pops.contains(i2)) {
                        pops.add(i2);
                    }
                }
            }
            double tempFitness=((KozaFitness)pops.get(0).fitness).standardizedFitness();
            solutionTree=pops.get(0);
            for (Individual id :pops){
                if (((KozaFitness)(id.fitness)).standardizedFitness()<tempFitness) {
                    tempFitness = ((KozaFitness)(id.fitness)).standardizedFitness();
                    solutionTree=id;
                }
            }
            if (((KozaFitness)(solutionTree.fitness)).standardizedFitness()>=2.44) {
                solutionTree = null;
            }
        }
        if (Config.collectFitness) {
            try {
                FileWriter fw = new FileWriter(Config.ConfigFile, true);
                List<String> indString =((CongestionProblem) evaluatedState.evaluator.p_problem).getIndString();
                for (String s:indString){
                    fw.append(GPRound+"\t"+s+"\r\n");
                }
                fw.append("////////////////////////////////////" + "\r\n");
                fw.flush();

            if (solutionTree==null) {
                long computingTime = System.currentTimeMillis() - initTime;
                log.error("no valid solution");
                log.info("Search time (ms): " + computingTime + "， one search finished.");

            }
            else {
                try {
                    //write the last generation to
                    PrintWriter writer = new PrintWriter("./start.in");
                    if (!Config.singleObjective) {
                        evaluatedState.population.subpops.get(0).printSubpopulation(evaluatedState, writer);
                    }else {
                        while (pops.size()>5){
                            int biggestFitnessIndex=0;
                            int popsSize=pops.size();
                            int popsIndex=0;
                            while(popsIndex<popsSize){
                                if (((KozaFitness)(pops.get(biggestFitnessIndex).fitness)).standardizedFitness()<((KozaFitness)(pops.get(popsIndex).fitness)).standardizedFitness()){
                                    biggestFitnessIndex=popsIndex;
                                }
                                popsIndex++;
                            }
                            pops.remove(biggestFitnessIndex);
                        }
                        evaluatedState.population.subpops.get(0).individuals.clear();
                        evaluatedState.population.subpops.get(0).individuals.addAll(pops);
                        evaluatedState.population.subpops.get(0).printSubpopulation(evaluatedState, writer);
                    }
                    writer.close();
                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                }
                if (!Config.singleObjective) {
                    fw.append("knee solution: " + "\t" + ((GPIndividual) solutionTree).toGPString() + "\t" + solutionTree.fitness.fitnessToString() + "\r\n");
                }else {
                    fw.append("solution: " + "\t" + ((GPIndividual) solutionTree).toGPString() + "\t" + (((KozaFitness)solutionTree.fitness)).standardizedFitness() + "\r\n");
                }
                fw.flush();
                solution = solutionTree;
                ///////////////////////////////////////////
                ///////////////////////////////////////////
                //////////////////////////////////////////////

                congestionProblem = (CongestionProblem) evaluatedState.evaluator.p_problem;
                List<SrcDstPair> srcDstPairs = congestionProblem.getSrcDstPair();

                Map<SrcDstPair, Path> newMap = congestionProblem.simLink(evaluatedState, solutionTree, 0);
                if (newMap!=null)
                    solutionPath=new HashMap<>(newMap);
                else {
                    System.out.println(((GPIndividual) solutionTree).toGPString());
                    return;
                }
                for (SrcDstPair pair : srcDstPairs) {
                    solutions.put(pair, newMap.get(pair).links());
                }

                long computingTime = System.currentTimeMillis() - initTime;

                log.info("Search time (ms): " + computingTime + "， one search finished.");

                for (SrcDstPair sd : oldPath.keySet()){
                    String oldPathString="";
                    for (Link ol : oldPath.get(sd)) {
                        oldPathString=oldPathString+ol.src().toString()+"_"+ol.dst().toString()+" | ";
                    }

                    log.info("oldPath: "+sd.src.toString()+" : "+sd.dst.toString()+" : "+oldPathString);
                }

                for (SrcDstPair sd:solutions.keySet()){
                    String newPathString="";
                    for (Link nl : solutions.get(sd)){
                        newPathString=newPathString+nl.src().toString()+"_"+nl.dst().toString()+" | ";
                    }
                    log.info("newPath: "+sd.src.toString()+" : "+sd.dst.toString()+" : "+newPathString);
                }
            }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        long time4 = System.currentTimeMillis();
       // System.out.println("time4: "+(time4-time3)+", "+getCurrentTime());
    }

    public void setFlag(boolean value){
        this.flag=value;
    }
    public boolean getFlag(){
        return this.flag;
    }
    private Map<SrcDstPair,List<Link>> getCurSDPath() {
        Set<FlowEntry> flowEntrySet = monitorUtil.getAllCurrentFlowEntries();
        Set<SrcDstPair> sdSet = monitorUtil.getAllSrcDstPairs(flowEntrySet);
        Iterator<SrcDstPair> it = sdSet.iterator();
        Map<SrcDstPair,List<Link>> result=new HashMap<>();
        while (it.hasNext()) {
            SrcDstPair sd = it.next();
            List<Link> dIdPath = monitorUtil.getCurrentPath(sd);
            if (dIdPath == null) {
                it.remove();
                continue;
            }
            result.put(sd, dIdPath);
        }
        return  result;
    }

    public  String getCurrentTime(){
        long totalMilliSeconds = System.currentTimeMillis();
        long totalSeconds = totalMilliSeconds / 1000;

        long currentSecond = totalSeconds % 60;

        long totalMinutes = totalSeconds / 60;
        long currentMinute = totalMinutes % 60;
        return (currentMinute+":"+currentSecond);
    }
    public List<String>getIndsString(){
        return indsString;
 }
//    public List<String>getParetoString(){
//        return paretoString;
//    }

    public CongestionProblem getCongestionProblem(){return congestionProblem;}

    public Map<Link,Integer> getWeightUsingSolutionTree(Individual tree,LinkService linkService,CongestionProblem congestionProblem){
        //System.out.println(((GPIndividual)tree).toGPString());
        return congestionProblem.getIndividualResultWeight(
                state,tree,0, linkService
        );
    }
    public Map<SrcDstPair, List<Link>> getCurrentLinkPath() {
        return ((CongestionProblem)congestionProblem).getCurrentLinkPath();
    }

    public Map<SrcDstPair, List<Link>> getSolutionLinkPath() {
        return solutions;
    }
    public Map<SrcDstPair,Path>getSolutionPath(){
        return solutionPath;
    }

    public List<Link> findLCS(List<Link> x, List<Link> y) {
        return ((CongestionProblem)congestionProblem).findLCS(x, y);
    }

    public Individual getKneeSolution(ArrayList<Individual> results) {
        if (results.size() == 0) {
            log.error("No results!");
            return null;
        }
        List<Individual> validSolutions=new ArrayList<>(results);

        //boolean flagV=true;
        for (Individual s : results) {
            MultiObjectiveFitness f = ((MultiObjectiveFitness)s.fitness);
            double[] fitness=f.getObjectives();
            for (int i = 0; i < fitness.length; i++) {
                if (fitness[i] == Config.LARGE_NUM) {
                    validSolutions.remove(s);
                }
            }
        }
        Individual kneeSolution = null;
        int nobj = 3;
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
    public  Individual getSolution(){
        return solution;
    }

}
