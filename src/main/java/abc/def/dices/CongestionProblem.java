package abc.def.dices;

import ec.gp.koza.KozaFitness;
import org.apache.sshd.client.keyverifier.DelegatingServerKeyVerifier;
import org.onlab.graph.Weight;
import org.onlab.packet.MacAddress;
import org.onosproject.net.*;
import org.onosproject.net.flow.FlowEntry;
import org.onosproject.net.host.HostService;
import org.onosproject.net.link.LinkService;
import org.onosproject.net.topology.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ec.EvolutionState;
import ec.Individual;
import ec.gp.GPIndividual;
import ec.gp.GPProblem;
import ec.multiobjective.MultiObjectiveFitness;
import ec.simple.SimpleProblemForm;
import ec.util.Parameter;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Iterator;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class CongestionProblem extends GPProblem implements SimpleProblemForm {
    private static final long serialVersionUID = 1L;

    private final Logger log = LoggerFactory.getLogger(getClass());
    private boolean weightAboveZero = true;
    private TopologyService topologyService;
    private LinkService linkService;
    private HostService hostService;
    private MonitorUtil monitorUtil;

    private SearchRunner runner;

    private Map<SrcDstPair, Long> sdTxBitsMap;
    private Map<Link, Long> simLinkThroughputMap;
    private Map<Link, Long> currentThroughputMap;
    private Map<Link, Long> currentSimLinkThroughputMap;

    private List<SrcDstPair> curSDList;
    private Map<SrcDstPair, Path> sdAltPathListMap;
    private Map<SrcDstPair, List<Link>> sdCurrentPathMap;
    private Map<Link, Integer> newWeight;
    private Map<Link, Double> packetLossRateMap;
    private ArrayList<SrcDstPair> simPair;
    //public double currentX;
    public double currentY;
    public double currentZ;
    private List<String> indsString;
    // private List<String> paretoString;
    private FileWriter fw;

    private int lastGeneration = 0;
    private ArrayList<Individual> individualInOneGeneration;
    private ArrayList<Individual> individualResults;
    private Map<Link,Long> tempThroughputMap=new HashMap<>();

    //public double currentW=Config.UTILIZATION_THRESHOLD;

    public void setup(final EvolutionState state,
                      final Parameter base, SearchRunner runner) {
        this.runner = runner;

        log.info("congestion problem setup!!");

        this.topologyService = runner.getTopologyService();
        this.linkService = runner.getLinkService();
        this.hostService = runner.getHostService();
        this.monitorUtil = runner.getMonitorUtil();

        this.sdTxBitsMap = new HashMap<>();
        this.simLinkThroughputMap = new HashMap<>();
        this.sdAltPathListMap = new HashMap<>();
        this.sdCurrentPathMap = new HashMap<>();
        this.currentThroughputMap = new HashMap<>();
        this.currentSimLinkThroughputMap = new HashMap<>();
        this.newWeight = new HashMap<>();
        this.packetLossRateMap = new HashMap<>();
        this.indsString = runner.getIndsString();
        this.simPair=new ArrayList<>();
        // this.paretoString=runner.getParetoString();
        this.individualInOneGeneration = new ArrayList<>();
        //this.individualResults=new ArrayList<>();
        super.setup(state, base, runner);
        /////////////////////////////
        setCurSDPath();
        initSimLinkThroughputMap();
        updateSDTxBitsMap();
        initCurrentLinkThroughputMap();
        updateCurrentThroughputMap();
        if (!Config.oldVersion)
            simPair=initialSimLink();

        /////////////////////////////

        // verify our input is the right class (or subclasses from it)
        if (!(input instanceof DoubleData)) {
            state.output.fatal("GPData class must subclass from " + abc.def.dices.DoubleData.class,
                    base.push(P_DATA), null);
            log.error("GPData class must subclass from " + abc.def.dices.DoubleData.class);
        }
        //log.info("GP started, subclass from "+ abc.def.dices.DoubleData.class);
    }


    //set the inital value of throughput to 0
    private void initCurrentLinkThroughputMap() {
        for (Link l : linkService.getLinks()) {
            currentThroughputMap.put(l, new Long(0));
        }
    }

    public void updateCurrentThroughputMap() {
        for (Link l : linkService.getLinks()) {
            long newValue = monitorUtil.getDeltaTxBits(l);
            currentThroughputMap.put(l, newValue);
            // currentSimLinkThroughputMap.put(l,newValue);
        }
    }

    public double getUtilization(Link l) {
        long throughput = currentSimLinkThroughputMap.get(l);
        return monitorUtil.calculateUtilization(l, throughput);
    }


    //store the relation of all (src,dst) pair and links based on the flowEntries
    private void setCurSDPath() {
        Set<FlowEntry> flowEntrySet = monitorUtil.getAllCurrentFlowEntries();
        Set<SrcDstPair> sdSet = monitorUtil.getAllSrcDstPairs(flowEntrySet);
        if (Config.test) {
            for (SrcDstPair sdpair : sdSet) {
                log.info("src: " + sdpair.src + ", dst: " + sdpair.dst);
            }
        }
        curSDList = new ArrayList<>(sdSet);
        Iterator<SrcDstPair> it = sdSet.iterator();

        while (it.hasNext()) {
            SrcDstPair sd = it.next();
            List<Link> dIdPath = monitorUtil.getCurrentPath(sd);
            if (dIdPath == null) {
                it.remove();
                continue;
            }
            sdCurrentPathMap.put(sd, dIdPath);
        }
    }

    public List<SrcDstPair> getSrcDstPair() {
        return curSDList;
    }

    //set the inital value of throughput to 0
    private void initSimLinkThroughputMap() {
        //log.info("Init Simlink througput map");
        for (Link l : linkService.getLinks()) {
            simLinkThroughputMap.put(l, new Long(0));
        }
    }

    //bits number - update
    private void updateSDTxBitsMap() {
        for (SrcDstPair sd : curSDList) {
            long deltaTxBits = monitorUtil.getTxBitsPerSec(sd);
            log.info(sd.src.toString()+" - "+deltaTxBits);
            //System.out.println(deltaTxBits);
            int srcCnt = cntSameSrcInCurFlows(sd);
            deltaTxBits /= srcCnt;
            //System.out.println(deltaTxBits);
            sdTxBitsMap.put(sd, deltaTxBits);
//            Host srcHost = hostService.getHost(HostId.hostId(sd.src));
//            DeviceId srcId = srcHost.location().deviceId();
//            Host dstHost = hostService.getHost(HostId.hostId(sd.dst));
//            DeviceId dstId = dstHost.location().deviceId();

            // log.info("updateSDTxBitsMap.............Demand: {}/{} ->  {}/{} {}",
            //  srcHost.ipAddresses(), srcId, dstHost.ipAddresses(), dstId, deltaTxBits);
        }
    }

    //the number of paths of the same src
    private int cntSameSrcInCurFlows(SrcDstPair sd) {
        int cnt = 0;
        for (SrcDstPair cSD : curSDList) {
            if (cSD.src.equals(sd.src)) {
                cnt++;
            }
        }
        return cnt;
    }

    public void setSearchRunner(SearchRunner runnner) {
        this.runner = runnner;
        //log.info("setSearchRunner");
    }

    public ArrayList<SrcDstPair> simPair(){
        return this.simPair;
    }

    //3 fitnesses
    public void evaluate(final EvolutionState state,
                         Individual ind,
                         final int subpopulation,
                         final int threadnum) {
        ///////////////////////////////////////////////////////////////////////
        if (Config.singleObjective) {
            long time1 = System.currentTimeMillis();
            KozaFitness f = (KozaFitness) ind.fitness;
            if ((ind.evaluated == false) || (state.generation == 0)) {
                ind.evaluated = true;
                Map<SrcDstPair, Path> newSolution = simLink(state, ind, threadnum);
                long time2 = System.currentTimeMillis();
                //if ((time2-time1)>0)
                //System.out.println("simLink: "+(time2-time1));
                if (newSolution == null) {

                    if (Config.test) {
                        log.info("fitness : {}", 3);
                    }
                    if (Config.collectFitness) {
                        indsString.add(state.generation + "\t" + 3 + "\t" + ((GPIndividual) ind).toGPString());
                    }
                    f.setStandardizedFitness(state, 3);
                    return;
                }
                double fitness = 0;
                long time3 = System.currentTimeMillis();
                //if ((time3-time2)>0)
                // System.out.println("for invalid solution: "+(time3-time2));

                double maxEstimateUtilization = estimateMaxLinkUtilization();
                // System.out.println(maxEstimateUtilization);
                if (maxEstimateUtilization > Config.UTILIZATION_THRESHOLD) {
                    fitness = maxEstimateUtilization / (maxEstimateUtilization + 1) + 2;
                } else {
                    long costByDiff = calculateDiffFromOrig(newSolution);
                    long totalDelay = sumDelay(newSolution);
                    fitness = (maxEstimateUtilization / (maxEstimateUtilization + 1)) + (costByDiff / (1 + costByDiff)) + (totalDelay / (totalDelay + 1));
                }

                if (Config.test) {
                    log.info("fitness : {}", fitness);
                }

                f.setStandardizedFitness(state, fitness);
                if (Config.collectFitness) {
                    indsString.add(state.generation + "\t" + fitness + "\t" + ((GPIndividual) ind).toGPString() + "\t" + maxEstimateUtilization);
                }
                long time4 = System.currentTimeMillis();
                //if ((time4-time3)>0)
                //System.out.println("fitness: "+(time4-time3));

            } else {
                if (Config.test) {
                    log.info("already evaluated, do nothing");
                }
                if (Config.collectFitness) {
                    indsString.add(state.generation + "\t" + ((KozaFitness) ind.fitness).standardizedFitness() + "\t" + ((GPIndividual) ind).toGPString());
                }
            }
        } else {
            ///////////////////////////////////////////////////////////////////////
            MultiObjectiveFitness f = ((MultiObjectiveFitness) ind.fitness);
            if ((ind.evaluated == false) || (state.generation == 0)) {

                ind.evaluated = true;
                Map<SrcDstPair, Path> newSolution = simLink(state, ind, threadnum);
                if (newSolution == null) {
                    double[] fn = new double[3];
                    fn[0] = Config.LARGE_NUM;
                    fn[1] = Config.LARGE_NUM;
                    fn[2] = Config.LARGE_NUM;
                    if (Config.test) {
                        log.info("fitness : {}, {}, {}", Config.LARGE_NUM, Config.LARGE_NUM, Config.LARGE_NUM);
                    }
                    if (Config.collectFitness) {
                        indsString.add(state.generation + "\t" + fn[0] + "\t" + fn[1] + "\t" + fn[2] + "\t" + ((GPIndividual) ind).toGPString());
                    }
                    f.setObjectives(state, fn);
                    return;
                }
                //fitness 1
                double maxEstimateUtilization = estimateMaxLinkUtilization();

                //fitness 2
                long costByDiff;
                if (maxEstimateUtilization > Config.UTILIZATION_THRESHOLD) {
                    costByDiff = Config.LARGE_NUM;
                } else {
                    costByDiff = calculateDiffFromOrig(newSolution);
                    if (costByDiff == 0) {
                        costByDiff = Config.LARGE_NUM;
                    }
                }

                //fitness 3
                long totalDelay = sumDelay(newSolution);
                if (maxEstimateUtilization > Config.UTILIZATION_THRESHOLD) {
                    totalDelay = Config.LARGE_NUM;
                }
                double[] fn = new double[3];
                fn[0] = maxEstimateUtilization;
                fn[1] = costByDiff;
                fn[2] = totalDelay;

                if (Config.test) {
                    log.info("fitness : {}, {}, {}", maxEstimateUtilization, costByDiff, totalDelay);
                }
                if (Config.collectFitness) {
                    indsString.add(state.generation + "\t" + fn[0] + "\t" + fn[1] + "\t" + fn[2] + "\t" + ((GPIndividual) ind).toGPString() + "\t" + ind.size());
                }
                f.setObjectives(state, fn);
            } else {
                if (Config.test) {
                    log.info("already evaluated, do nothing");
                }
                if (Config.collectFitness) {
                    indsString.add(state.generation + "\t" + "\t" + "\t" + ((GPIndividual) ind).toGPString());
                }
            }
            /////////////////////////////////////////////////////////////////////
        }
    }


    //    public ArrayList<Individual>getIndividualResults(){
//        return individualResults;
//    }
    public List<String> getIndString() {
        return indsString;
    }
//    public List<String> getParetoString(){
//        return paretoString;
//    }
    //using the resulting tree of GP to calculate the new links

    public Map<SrcDstPair, Path> simLink(final EvolutionState state,
                                         final Individual ind,
                                         final int threadnum) {
        if (Config.oldVersion) {
            weightAboveZero = true;
            TempLinkWeight tempWeight = new TempLinkWeight();
            DoubleData input = (DoubleData) (this.input);

            for (Link l0 : linkService.getLinks()) {
                currentSimLinkThroughputMap.put(l0, (long) 0);
                //long delay = monitorUtil.getDelay(l0);
                currentY = 0.01;
                //currentZ=(double)delay;

                try {
                    ((GPIndividual) ind).trees[0].child.eval(state, threadnum, input, stack, ((GPIndividual) ind), this);
                } catch (Exception e) {
                    //System.out.println("divide by 0");
                    return null;
                }
                double newLinkWeight = input.x;
                if ((int) newLinkWeight <= 0) {
                    //System.out.println("new link weight <0");
                    weightAboveZero = false;
                    return null;
                }
                tempWeight.setLinkWeight(l0, (int) newLinkWeight);

            }
            for (SrcDstPair sd : curSDList) {

                Host srcHost = hostService.getHost(HostId.hostId(sd.src));
                DeviceId srcDevId = srcHost.location().deviceId();
                Host dstHost = hostService.getHost(HostId.hostId(sd.dst));
                DeviceId dstDevId = dstHost.location().deviceId();

                Set<Path> allPathSet =
                        topologyService.getKShortestPaths(
                                topologyService.currentTopology(),
                                srcDevId, dstDevId,
                                tempWeight,
                                Config.MAX_NUM_PATHS);
                sdAltPathListMap.put(sd, (Path) (allPathSet.toArray()[0]));

                List<Link> newLinks = sdAltPathListMap.get(sd).links();
                for (Link nL : newLinks) {
                    long throughputPerSec = sdTxBitsMap.get(sd);
                    long simThroughput = currentSimLinkThroughputMap.get(nL) + throughputPerSec;
                    double simUtilization = monitorUtil.calculateUtilization(nL, simThroughput);
                    currentY = simUtilization;
                    if (currentY < 0.01)
                        currentY = 0.01;
                    // currentY=currentY*100;
                    try {
                        ((GPIndividual) ind).trees[0].child.eval(state, threadnum, input, stack, ((GPIndividual) ind), this);
                    } catch (Exception e) {
                        return null;
                    }
                    double newLinkWeight = input.x;
                    if ((int) newLinkWeight <= 0) {
                        return null;
                    }
                    tempWeight.setLinkWeight(nL, (int) newLinkWeight);
                    currentSimLinkThroughputMap.put(nL, simThroughput);
                }
                if (Config.test) {
                    log.info("sd-pair:" + sd + "; srcDev" + srcDevId + "; dstDev" + dstDevId +
                            "; host src:" + srcHost + "; host dst:" + dstHost + "; Path" + allPathSet.toArray()[0]);
                }
            }
        } else {

            TempLinkWeight tempWeight = new TempLinkWeight();
            DoubleData input = (DoubleData) (this.input);

            for (Link l5 : linkService.getLinks()) {
                currentY = monitorUtil.calculateUtilization(l5, currentSimLinkThroughputMap.get(l5));
                if (currentY < 0.01)
                    currentY = 0.01;
                try {
                    ((GPIndividual) ind).trees[0].child.eval(state, threadnum, input, stack, ((GPIndividual) ind), this);
                } catch (Exception e) {
                    return null;
                }
                double newLinkWeight = input.x;
                if ((int) newLinkWeight <= 0) {
                    return null;
                }
                tempWeight.setLinkWeight(l5, (int) newLinkWeight);
            }

            for (Link l : currentSimLinkThroughputMap.keySet()){
                tempThroughputMap.put(l,currentSimLinkThroughputMap.get(l));
            }

            for (SrcDstPair sd : simPair()) {
                Host srcHost = hostService.getHost(HostId.hostId(sd.src));
                DeviceId srcDevId = srcHost.location().deviceId();
                Host dstHost = hostService.getHost(HostId.hostId(sd.dst));
                DeviceId dstDevId = dstHost.location().deviceId();

                Set<Path> allPathSet =
                        topologyService.getKShortestPaths(
                                topologyService.currentTopology(),
                                srcDevId, dstDevId,
                                tempWeight,
                                Config.MAX_NUM_PATHS);
                sdAltPathListMap.put(sd, (Path) (allPathSet.toArray()[0]));
                //System.out.println(allPathSet.toArray()[0]);

                List<Link> newLinks = sdAltPathListMap.get(sd).links();
                for (Link nL : newLinks) {
                    long throughputPerSec = sdTxBitsMap.get(sd);
                    long updateThroughput = tempThroughputMap.get(nL) + throughputPerSec;
                    double simUtilization = monitorUtil.calculateUtilization(nL, updateThroughput);
                    currentY = simUtilization;
                    if (currentY < 0.01)
                        currentY = 0.01;
                    try {
                        ((GPIndividual) ind).trees[0].child.eval(state, threadnum, input, stack, ((GPIndividual) ind), this);
                    } catch (Exception e) {
                        return null;
                    }
                    double newLinkWeight = input.x;
                    if ((int) newLinkWeight <= 0) {
                        return null;
                    }
                    tempWeight.setLinkWeight(nL, (int) newLinkWeight);
                    tempThroughputMap.put(nL, updateThroughput);
                }
                if (Config.test) {
                    log.info("sd-pair:" + sd + "; srcDev" + srcDevId + "; dstDev" + dstDevId +
                            "; host src:" + srcHost + "; host dst:" + dstHost + "; Path" + allPathSet.toArray()[0]);
                }
            }
        }
        return sdAltPathListMap;
    }
//////////////////////

    public ArrayList<SrcDstPair> initialSimLink() {

        ArrayList<Link> congestedLinks = new ArrayList<>();

        //initial currentSimLinkThroughputMap and find the congested links
        for(Link l0:linkService.getLinks())
        {
            currentSimLinkThroughputMap.put(l0, (long) 0);
            long throuputPerLink = currentThroughputMap.get(l0);
            double utilizationPerLink = monitorUtil.calculateUtilization(l0, throuputPerLink);
            if (utilizationPerLink >= Config.UTILIZATION_THRESHOLD) {
                congestedLinks.add(l0);
            }
        }

        //update the currentSimLinkThroughMap
        //updateSDTxBitsMap();
        for(SrcDstPair sdForThroughput:curSDList)
        {
            log.info(sdForThroughput.src.toString());
            List<Link> linksForThroughput = sdCurrentPathMap.get(sdForThroughput);
            log.info(linksForThroughput+" : "+sdTxBitsMap.get(sdForThroughput));
            for (Link linkForThroughput : linksForThroughput) {
                Long newThroughput = currentSimLinkThroughputMap.get(linkForThroughput) + sdTxBitsMap.get(sdForThroughput);
                currentSimLinkThroughputMap.put(linkForThroughput, newThroughput);
            }
        }
        for (Link lll:currentSimLinkThroughputMap.keySet()) {
            Long n=currentSimLinkThroughputMap.get(lll);
            if (n>1)
            log.info(String.valueOf(n));
        }


        // find the flow whose links match the congested links most, delete its utilization
        // from the original utilization of the congested link. If congestion is still there
        // find the second flow whose links match the congested links most...
        boolean congested = true;
        ArrayList<SrcDstPair> sds = new ArrayList<>();
        ArrayList<SrcDstPair> tempCurSDList = new ArrayList<>();
        for (SrcDstPair s0:curSDList){
            tempCurSDList.add(s0);
        }
       // getSrcDstPair();
        while(congested)
        {
            congested = false;
            SrcDstPair readyToDelete = null;
            int maxCount = 0;
            for (SrcDstPair sd : tempCurSDList) {
                int count = 0;
                List<Link> sdLinks = sdCurrentPathMap.get(sd);
                for (Link l1 : sdLinks) {
                    if (congestedLinks.contains(l1)) {
                        count++;
                    }
                }
                if (count > maxCount) {
                    maxCount = count;
                    readyToDelete = sd;
                }
            }
            sds.add(readyToDelete);
            List<Link> readytoDeleteLinks = sdCurrentPathMap.get(readyToDelete);
            tempCurSDList.remove(readyToDelete);
            for (Link l3 : readytoDeleteLinks) {
                Long newThroughput = currentSimLinkThroughputMap.get(l3) - sdTxBitsMap.get(readyToDelete);
                currentSimLinkThroughputMap.put(l3, newThroughput);
            }

            for (Link l4 : congestedLinks) {
                double newUtil = monitorUtil.calculateUtilization(l4, currentSimLinkThroughputMap.get(l4));
                if (newUtil >= 0.8) {
                    congested = true;
                    log.info(congested+" : "+newUtil);
                   // break;
                }

            }
        }
        return  sds;
    }
    /////////////////
    ///////////////////

    private long sumDelay(Map<SrcDstPair, Path> newLinksForPair) {
        long sum = 0;
        try {
            for (SrcDstPair key : curSDList){
                if (newLinksForPair.keySet().contains(key))
                {
                    Path sPath = newLinksForPair.get(key);
                    for (Link l : sPath.links()) {
                        sum += monitorUtil.getDelay(l);
                    }
                }else {
                    List<Link> sLinks=sdCurrentPathMap.get(key);
                    for (Link l : sLinks){
                        sum+=monitorUtil.getDelay(l);
                    }
                }

            }
        }catch (Exception e){
            System.out.println(e);
        }
        return sum;
    }

    private long calculateDiffFromOrig(Map<SrcDstPair, Path> newLinksForPair) {
    int distSum = 0;
    try {
        for (SrcDstPair key : newLinksForPair.keySet()) {
            SrcDstPair sd = key;
            Path sPath = newLinksForPair.get(sd);
            List<Link> sLinkPath = sPath.links();
            List<Link> oLinkPath = sdCurrentPathMap.get(sd);
            int dist = editLCSDistance(oLinkPath, sLinkPath);
            distSum += dist;
        }
    }catch (Exception e){
        System.out.println(e);
    }
    return distSum;
}
    private int editLCSDistance(List<Link> x, List<Link> y) {
            int m = x.size(), n = y.size();
            int l[][] = new int[m+1][n+1];
            for (int i = 0; i <= m; i++) {
                for (int j = 0; j <= n; j++) {
                    if (i == 0 || j == 0) {
                        l[i][j] = 0;
                    } else if (x.get(i-1).equals(y.get(j-1))) {
                        l[i][j] = l[i-1][j-1] + 1;
                    } else {
                        l[i][j] = Math.max(l[i-1][j], l[i][j-1]);
                    }
                }
            }
            int lcs = l[m][n];
            return (m - lcs) + (n - lcs);
        }

    public List<Link> findLCS(List<Link> x, List<Link> y) {
        int m = x.size(), n = y.size();
        int l[][] = new int[m+1][n+1];
        for (int i = 0; i <= m; i++) {
            for (int j = 0; j <= n; j++) {
                if (i == 0 || j == 0) {
                    l[i][j] = 0;
                } else if (x.get(i-1).equals(y.get(j-1))) {
                    l[i][j] = l[i-1][j-1] + 1;
                } else {
                    l[i][j] = Math.max(l[i-1][j], l[i][j-1]);
                }
            }
        }
        List<Link> lcs = new ArrayList<>();
        int i = m, j = n;
        while (i > 0 && j > 0) {
            if (x.get(i-1).equals(y.get(j-1))) {
                lcs.add(x.get(i-1));
                i--; j--;
            } else if (l[i-1][j] > l[i][j-1]) {
                i--;
            } else {
                j--;
            }
        }
        Collections.reverse(lcs);
        return lcs;
    }

    public Map<SrcDstPair, List<Link>> getCurrentLinkPath() {
        return sdCurrentPathMap;
    }

    private double estimateMaxLinkUtilization() {
        double max = 0D;
        for (Link l : linkService.getLinks()) {
            double u = estimateUtilization(l);
            if (max < u) {
                max = u;
            }
        }
        return max;
    }

    public double estimateUtilization(Link l) {
        long throughput;
        if (Config.oldVersion)
            throughput = currentSimLinkThroughputMap.get(l);
        else
            throughput = tempThroughputMap.get(l);
        return monitorUtil.calculateUtilization(l, throughput);
    }

    public Map<Link, Integer> getIndividualResultWeight(EvolutionState state, Individual tree, int threadnum, LinkService linkService) {
        Map<Link, Integer> result = new HashMap<>();
        DoubleData input = (DoubleData) (this.input);
        for (Link l : linkService.getLinks()) {
            //long delay = monitorUtil.getDelay(l);
            currentY = monitorUtil.monitorLinkUtilization(l);
            if (currentY<0.01)
                currentY=0.01;
            //currentY=100*currentY;

           // currentZ = (double) delay;
            //currentZ=100*currentZ;
            try {
            ((GPIndividual) tree).trees[0].child.eval(state, threadnum, input, stack, ((GPIndividual) tree), this);
            //System.out.println("new coming: util "+currentY+"   "+l.src().toString()+" : "+l.dst().toString()+" : "+input.x);
                }
            catch (Exception e){
                return null;
            }
            double newLinkWeight = input.x;
            if ((int)newLinkWeight <= 0) {
                return null;
            }
            result.put(l, (int)newLinkWeight);
        }
        return  result;
    }


    private double getPacketLossRate(Link l){
        double result=0;
        result=packetLossRateMap.get(l);
        return result;
    }
/*
    public  Map<SrcDstPair,Path> computeLink(final EvolutionState state,
                                             final Individual ind,
                                             final int threadnum){
        Map<SrcDstPair,Path> result=new HashMap<>();

        TempLinkWeight tempWeight=new TempLinkWeight();
        DoubleData input = (DoubleData)(this.input);

        for (Link l : linkService.getLinks()) {
            long delay = monitorUtil.getDelay(l);
            double utilization=getUtilization(l);
            // long oldLinkWeight=linkWeight.getLinkWeight(l);
            // currentX=(double)oldLinkWeight;
            // currentX=getPacketLossRate(l);
            currentY=utilization;
//            if (utilization<Config.UTILIZATION_THRESHOLD){
//                currentX=0;
//            }else
//            {
//                currentX=getPacketLossRate(l);
//            }
            currentZ=(double)delay;
            ((GPIndividual)ind).trees[0].child.eval(state,threadnum,input,stack,((GPIndividual)ind),this);
            double newLinkWeight=input.x;
            //System.out.println(newLinkWeight);
            newLinkWeight=(int)newLinkWeight;
            if (newLinkWeight<0){
                newLinkWeight=newLinkWeight*(-1.0);
            }
            tempWeight.setLinkWeight(l,(int)newLinkWeight);
            newWeight.put(l,(int)newLinkWeight);
        }
        //System.out.println("curSD:   "+curSDList);
        for (SrcDstPair sd : curSDList) {

            Host srcHost = hostService.getHost(HostId.hostId(sd.src));
            DeviceId srcDevId = srcHost.location().deviceId();
            Host dstHost = hostService.getHost(HostId.hostId(sd.dst));
            DeviceId dstDevId = dstHost.location().deviceId();
            //System.out.println(srcDevId+" ; "+dstDevId);
            //System.out.println(sd.src+" : "+sd.dst);
            Set<Path> allPathSet =
                    topologyService.getKShortestPaths(
                            topologyService.currentTopology(),
                            srcDevId, dstDevId,
                            tempWeight,
                            Config.MAX_NUM_PATHS);

            result.put(sd, (Path)(allPathSet.toArray()[0]));
        }
        return result;
    }
    */

}