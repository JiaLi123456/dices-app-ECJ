package abc.def.dices;

import org.onlab.graph.Weight;
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

import java.io.FileWriter;
import java.io.IOException;
import java.util.Iterator;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class CongestionProblem extends GPProblem implements SimpleProblemForm{
    private static final long serialVersionUID = 1L;

    private final Logger log = LoggerFactory.getLogger(getClass());

    private TopologyService topologyService;
    private LinkService linkService;
    private HostService hostService;
    private MonitorUtil monitorUtil;
    private MonitorPacketLoss monitorPacketLoss;
    private SearchRunner runner;

    private Map<SrcDstPair, Long> sdTxBitsMap;
    private Map<Link, Long> simLinkThroughputMap;
    private Map<Link,Long> currentThroughputMap;
    private Map<Link,Long>currentSimLinkThroughputMap;

    private List<SrcDstPair> curSDList;
    private Map<SrcDstPair, Path> sdAltPathListMap;
    private Map<SrcDstPair, List<Link>> sdCurrentPathMap;
    private Map<Link,Double> newWeight;
    private Map<Link, Double> packetLossRateMap;
    public double currentX;
    public double currentY;
    public double currentZ;
    private List<String> indsString;

    //public double currentW=Config.UTILIZATION_THRESHOLD;

    public void setup(final EvolutionState state,
                      final Parameter base, SearchRunner runner)
    {
        this.runner=runner;

        log.info("congestion problem setup!!");

        this.topologyService = runner.getTopologyService();
        this.linkService = runner.getLinkService();
        this.hostService = runner.getHostService();
        this.monitorUtil = runner.getMonitorUtil();
        this.monitorPacketLoss=runner.getMonitorPacketLoss();

        this.sdTxBitsMap = new HashMap<>();
        this.simLinkThroughputMap = new HashMap<>();
        this.sdAltPathListMap = new HashMap<>();
        this.sdCurrentPathMap = new HashMap<>();
        this.currentThroughputMap=new HashMap<>();
        this.currentSimLinkThroughputMap=new HashMap<>();
        this.newWeight=new HashMap<>();
        this.packetLossRateMap=new HashMap<>();
        this.indsString=runner.getIndsString();
        super.setup(state, base,runner);
        /////////////////////////////
        setCurSDPath();
        initSimLinkThroughputMap();
        updateSDTxBitsMap();
        initCurrentLinkThroughputMap();
        updateCurrentThroughputMap();
        initAndUpdatePacketLossRateMap();
        /////////////////////////////

        // verify our input is the right class (or subclasses from it)
        if (!(input instanceof DoubleData)) {
            state.output.fatal("GPData class must subclass from " + abc.def.dices.DoubleData.class,
                    base.push(P_DATA), null);
            log.error("GPData class must subclass from " + abc.def.dices.DoubleData.class);
        }
        //log.info("GP started, subclass from "+ abc.def.dices.DoubleData.class);
    }

    private void initAndUpdatePacketLossRateMap(){
        for (Link l:linkService.getLinks()){
            packetLossRateMap.put(l,monitorPacketLoss.getPacketLossRate(l));
        }
    }


    //set the inital value of throughput to 0
    private void initCurrentLinkThroughputMap() {
        for (Link l : linkService.getLinks()) {
            currentThroughputMap.put(l, new Long(0));
        }
    }
    public void updateCurrentThroughputMap(){
        for (Link l : linkService.getLinks()) {
            long newValue = monitorUtil.getDeltaTxBits(l);
            currentThroughputMap.put(l,newValue);
            currentSimLinkThroughputMap.put(l,newValue);
        }
    }

    public double getUtilization(Link l){
        long throughput=currentSimLinkThroughputMap.get(l);
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
    public  List<SrcDstPair> getSrcDstPair(){
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
            //System.out.println(deltaTxBits);
            int srcCnt = cntSameSrcInCurFlows(sd);
            deltaTxBits /= srcCnt;
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

    //3 fitnesses
    public void evaluate(final EvolutionState state,
                         final Individual ind,
                         final int subpopulation,
                         final int threadnum) {
        long time1 = System.currentTimeMillis();
        MultiObjectiveFitness f = ((MultiObjectiveFitness)ind.fitness);

        if (ind.evaluated==false) {

            ind.evaluated = true;

            Map<SrcDstPair, Path> newSolution = simLink(state, ind, threadnum);

            //log.info("evaluate");

            initSimLinkThroughputMap();
            //simulate, update the value of utilization
            //updateSDTxBitsMap();
            updateSimLinkThroughputMap(newSolution);


            //fitness 1
            double maxEstimateUtilization = estimateMaxLinkUtilization();

//            for (SrcDstPair sd : newSolution.keySet()){
//                log.info("sd pair: "+sd.src.toString()+" : "+sd.dst.toString());
//                for(Link l :newSolution.get(sd).links()){
//                    log.info(l.src().toString()+" : "+l.dst().toString());
//                }
//            }

            //fitness 2
            long costByDiff = calculateDiffFromOrig(newSolution);
//            if (costByDiff == 0) {
//                costByDiff = Config.LARGE_NUM;
//            }

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
            //log.info("fitness : {}, {}",maxEstimateUtilization,costByDiff);
            if (Config.collectFitness) {
//                try {
                    indsString.add(state.generation + "\t"+fn[0] + "\t"+fn[1] + "\t"+fn[2]);
//                    fw.append(state.generation + "\t");
//                    fw.append(fn[0] + "\t");
//                    fw.append(fn[1] + "\t");
//                    fw.append(fn[2] + "\t");
//
//                    fw.append("\r\n");
//                    fw.flush();
//                } catch (IOException e) {
//                    e.printStackTrace();
//                }
            }


            f.setObjectives(state, fn);
        }
        else {
            if (Config.test) {
                log.info("already evaluated, do nothing");
            }
        }
        if (Config.test) {
            long time2 = System.currentTimeMillis();
            log.info("evaluate for one individual 1: " + (time2 - time1));
        }
    }

    public Map<Link,Double> getNewWeight(){
        return newWeight;
    }

    public List<String> getIndString(){
        return indsString;
}
    //using the resulting tree of GP to calculate the new links

    public Map<SrcDstPair, Path> simLink(final EvolutionState state,
                                         final Individual ind,
                                         final int threadnum) {

       // DynamicLinkWeight linkWeight = (DynamicLinkWeight)DynamicLinkWeight.DYNAMIC_LINK_WEIGHT;
        TempLinkWeight tempWeight=new TempLinkWeight();
        DoubleData input = (DoubleData)(this.input);
        for (SrcDstPair sd : curSDList) {
            for (Link l : linkService.getLinks()) {
                long delay = monitorUtil.getDelay(l);
                double utilization=getUtilization(l);
                //Long txBits = sdTxBitsMap.get(sd);
               // long oldLinkWeight=linkWeight.getLinkWeight(l);
                //double packetLossRate=monitorPacketLoss.getPacketLossRate(l);
                //currentX=(double)oldLinkWeight;
                currentY=utilization;
                if (utilization<Config.UTILIZATION_THRESHOLD){
                    currentX=0;
                }else
                {
                    currentX=getPacketLossRate(l);
                }
                currentZ=(double)delay;

                ((GPIndividual)ind).trees[0].child.eval(state,threadnum,input,stack,((GPIndividual)ind),this);

                double newLinkWeight=input.x;
                newLinkWeight=(int)newLinkWeight;
                if (newLinkWeight<0){
                    newLinkWeight=newLinkWeight*(-1.0);
                }
                tempWeight.setLinkWeight(l,(int)newLinkWeight);
                if (Config.test) {
                    log.info("Link {} -> {}: util {}", l.src().deviceId(), l.dst().deviceId(), currentY);
                    log.info("Link {} -> {}: MaxPacketLossRate {}", l.src().deviceId(), l.dst().deviceId(), currentX);
                    log.info("Link {} -> {}: delay {}", l.src().deviceId(), l.dst().deviceId(), currentZ);
                    // log.info("Link {} -> {}: delay {}", l.src().deviceId(), l.dst().deviceId(), currentW);
                    log.info(((GPIndividual) ind).toGPString());
                    log.info("resulting new weight: " + newLinkWeight);
                }
            }

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

            sdAltPathListMap.put(sd, (Path)(allPathSet.toArray()[0]));

            List<Link> oldLinks=sdCurrentPathMap.get(sd);
            for (Link oL : oldLinks){
                currentSimLinkThroughputMap.put(oL,currentSimLinkThroughputMap.get(oL)-sdTxBitsMap.get(sd));
            }
            List<Link> newLinks=sdAltPathListMap.get(sd).links();
            for (Link nL:newLinks){
                currentSimLinkThroughputMap.put(nL,currentSimLinkThroughputMap.get(nL)+sdTxBitsMap.get(sd));
            }
            if (Config.test) {
                log.info("sd-pair:" + sd + "; srcDev" + srcDevId + "; dstDev" + dstDevId +
                        "; host src:" + srcHost + "; host dst:" + dstHost + "; Path" + allPathSet.toArray()[0]);
            }
           // System.out.println(sdAltPathListMap.size());
        }
        log.info("----------------------------------------");
        return sdAltPathListMap;
    }

    private long sumDelay(Map<SrcDstPair, Path> newLinksForPair) {
        long sum = 0;
        try {
            for (SrcDstPair key : curSDList) {
                Path sPath = newLinksForPair.get(key);
                for (Link l : sPath.links()) {
                    sum += monitorUtil.getDelay(l);
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
        for (SrcDstPair key : curSDList) {
            SrcDstPair sd = key;
            Path sPath = newLinksForPair.get(sd);
            List<Link> sLinkPath = sPath.links();
            /////////////////////////////////////////////////
            List<Link> oLinkPath = sdCurrentPathMap.get(sd);
//        log.info(String.valueOf(sdCurrentPathMap.keySet()));
//        log.info(String.valueOf(oLinkPath.size()));
//        log.info(String.valueOf(sLinkPath.size()));
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

    private void updateSimLinkThroughputMap(Map<SrcDstPair, Path> newLinksForPair) {
        //System.out.println("number of paths: "+newLinksForPair.keySet().size());
        for (SrcDstPair key : newLinksForPair.keySet()) {
           // System.out.println(key.toString());
            Path solutionPath = newLinksForPair.get(key);
            SrcDstPair sd = key;
            incSimLinkThroughput(solutionPath, sd);
        }
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
        long throughput = simLinkThroughputMap.get(l);
        return monitorUtil.calculateUtilization(l, throughput);
    }

    private void incSimLinkThroughput(Path path, SrcDstPair sd) {
        Long txBits = sdTxBitsMap.get(sd);

        for (Link l : path.links()) {
            Long linkThroughput = simLinkThroughputMap.get(l);
            simLinkThroughputMap.put(l, linkThroughput+txBits);
           // System.out.println("src: "+l.src()+", dst: "+l.dst()+" : currentTroughput = "+linkThroughput+txBits);
        }
    }



    public  Map<SrcDstPair,Path> computeLink(final EvolutionState state,
                                             final Individual ind,
                                             final int threadnum){
        Map<SrcDstPair,Path> result=new HashMap<>();
        //initCurrentLinkThroughputMap();
        //updateCurrentThroughputMap();
        //DynamicLinkWeight linkWeight = (DynamicLinkWeight)DynamicLinkWeight.DYNAMIC_LINK_WEIGHT;
        TempLinkWeight tempWeight=new TempLinkWeight();
        DoubleData input = (DoubleData)(this.input);
        for (Link l : linkService.getLinks()) {
            long delay = monitorUtil.getDelay(l);
            double utilization=getUtilization(l);
           // long oldLinkWeight=linkWeight.getLinkWeight(l);
           // currentX=(double)oldLinkWeight;
           // currentX=getPacketLossRate(l);
            currentY=utilization;
            if (utilization<Config.UTILIZATION_THRESHOLD){
                currentX=0;
            }else
            {
                currentX=getPacketLossRate(l);
            }
            currentZ=(double)delay;
            ((GPIndividual)ind).trees[0].child.eval(state,threadnum,input,stack,((GPIndividual)ind),this);
            double newLinkWeight=input.x;
            //System.out.println(newLinkWeight);
            newLinkWeight=(int)newLinkWeight;
            if (newLinkWeight<0){
                newLinkWeight=newLinkWeight*(-1.0);
            }
            tempWeight.setLinkWeight(l,(int)newLinkWeight);
            newWeight.put(l,newLinkWeight);
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


    public Map<Link, Integer> getIndividualResultWeight(EvolutionState state,Individual tree, int threadnum){
        Map<Link,Integer> result=new HashMap<>();
        //DynamicLinkWeight linkWeight = (DynamicLinkWeight)DynamicLinkWeight.DYNAMIC_LINK_WEIGHT;
        initCurrentLinkThroughputMap();
        updateCurrentThroughputMap();
        DoubleData input = (DoubleData)(this.input);
        for (Link l : linkService.getLinks()) {
            long delay = monitorUtil.getDelay(l);
            double utilization=getUtilization(l);
            //long oldLinkWeight=linkWeight.getLinkWeight(l);
            //currentX=monitorPacketLoss.getMaxPacketLossRate();
            currentY=utilization;
            if (utilization<Config.UTILIZATION_THRESHOLD){
                currentX=0;
            }else
            {
                currentX=getPacketLossRate(l);
            }
            currentZ=(double)delay;
            //currentW=Config.UTILIZATION_THRESHOLD;
            ((GPIndividual)tree).trees[0].child.eval(state,threadnum,input,stack,((GPIndividual)tree),this);
            //log.warn(((GPIndividual) tree).toGPString());
            double newLinkWeight=input.x;
            //log.warn("x"+currentX+"; y="+currentY+"; z="+currentZ+"...result="+newLinkWeight);
            int newIntLinkWeight=(int)newLinkWeight;
            if (newIntLinkWeight<0){
                newIntLinkWeight=newIntLinkWeight*(-1);
            }
            result.put(l,newIntLinkWeight);
        }
        return result;
    }
    private double getPacketLossRate(Link l){
        double result=0;
        result=packetLossRateMap.get(l);
        return result;
    }
}