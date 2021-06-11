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
    //private List<String>paretoString;
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
        if (!flag) {
            log.info("start from file");
            System.out.println("start from file");
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
            log.info("start from random");
            System.out.println("start from random");
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
////////////////////////////////////////////////////
        ///////////////////////////////////////////////
        ///////////////////////////////////////////////
        long time1 = System.currentTimeMillis();

        System.out.println("time1： "+(time1-initTime)+", "+getCurrentTime());

            Output out = Evolve.buildOutput();

            Thread t = Thread.currentThread();

            SimpleEvolutionState evaluatedState = (SimpleEvolutionState) Evolve.initialize(child, (int) t.getId(), out);

            state = evaluatedState;
        long time2 = System.currentTimeMillis();
        System.out.println("time2： "+(time2-time1)+", "+getCurrentTime());
            evaluatedState.startFresh(this);
            int result = EvolutionState.R_NOTDONE;
            //////////////////////////////////////////////
       // String filename=String.valueOf(time1);

            while (result == EvolutionState.R_NOTDONE) {
                //result = evaluatedState.evolve();
                ArrayList<Individual> ti=evaluatedState.population.subpops.get(0).individuals;
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
                   // System.out.println(count1);
                }
                result=evaluatedState.evolve();
               // System.out.println(".........................");

               }
            //////////////////////////////////////////////
        long time3 = System.currentTimeMillis();
        System.out.println("time3: "+(time3-time2)+", "+getCurrentTime());

        //System.out.println(((CongestionProblem)evaluatedState.evaluator.p_problem).getIndividualResults().size());
        //ArrayList<Individual>tempinds=
//        int i=0;
//        ArrayList<Individual>tempinds2=new ArrayList<>();
//        for (Individual id : tempinds){
//            MultiObjectiveFitness f = ((MultiObjectiveFitness)id.fitness);
//            double[] fitness=f.getObjectives();
//            if ((fitness[0]!=Config.LARGE_NUM)&&(fitness[1]!=Config.LARGE_NUM) &&(fitness[2]!=Config.LARGE_NUM))
//                tempinds2.add(id);
//        }
//            //System.out.println(tempinds2.size());
            ArrayList<Individual> inds = MultiObjectiveFitness.getSortedParetoFront(state.population.subpops.get(0).individuals);


        //System.out.println(inds);
        //
//        for (Individual temp:inds)
//            System.out.println(((GPIndividual)temp).toGPString()+ " : "+temp.fitness.fitnessToString());
        if (Config.collectFitness) {
            try {
                FileWriter fw = new FileWriter(Config.ConfigFile, true);
                List<String> indString =((CongestionProblem) evaluatedState.evaluator.p_problem).getIndString();
                //List<String> paretoString=((CongestionProblem)evaluatedState.evaluator.p_problem).getParetoString();
                for (String s:indString){
                    fw.append(GPRound+"\t"+s+"\r\n");
                }
                fw.append("////////////////////////////////////" + "\r\n");
                fw.flush();

//                File file1 = new File("./outputIndividualFile1");
//                FileWriter fw1 = new FileWriter(file1,true);
//                for (String  s1: paretoString){
//                    fw1.append(GPRound+"\t"+s1+"\r\n");
//                }
//                fw1.append("////////////////////////////////////" + "\r\n");
//                fw1.flush();


            log.info("number of pareto fronts is: "+ inds.size());

            Individual solutionTree=null;

            if (inds.size()<1){
                log.error("no answers yet as there are no pareto fronts");
                return;
            }

            solutionTree=getKneeSolution(inds);
           // System.out.println(solutionTree.fitness.fitnessToString());
            if (solutionTree==null) {
                flag = true;

                long computingTime = System.currentTimeMillis() - initTime;
                log.error("no valid solution");
                log.info("Search time (ms): " + computingTime + "， one search finished.");

            }
            else {
                //System.out.println(((GPIndividual)solutionTree).toGPString());
                flag=false;

                try {
                    //write the last generation to
                    PrintWriter writer = new PrintWriter("./start.in");
                    evaluatedState.population.subpops.get(0).printSubpopulation(evaluatedState,writer);
                    writer.close();
                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                }


                fw.append("knee solution: "+"\t"+((GPIndividual)solutionTree).toGPString()+"\t"+solutionTree.fitness.fitnessToString()+"\r\n");
                fw.flush();
                solution = solutionTree;
               // System.out.println(((GPIndividual) solutionTree).toGPString());
               // System.out.println(solutionTree.fitness.fitnessToString());
               // solutionTree.printIndividualForHumans(state, 0);
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
                ////////////////////////////////////////////
                //System.out.println("242"+flag);
            }

            } catch (IOException e) {
                e.printStackTrace();
            }
        }
       // System.out.println(flag);
        long time4 = System.currentTimeMillis();
        System.out.println("time4: "+(time4-time3)+", "+getCurrentTime());
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
