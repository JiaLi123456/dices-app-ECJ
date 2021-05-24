package abc.def.dices;

import ec.Individual;
import ec.gp.GPIndividual;
import org.onosproject.core.ApplicationId;
import org.onosproject.net.DeviceId;
import org.onosproject.net.Host;
import org.onosproject.net.HostId;
import org.onosproject.net.Link;
import org.onosproject.net.PortNumber;
import org.onosproject.net.device.DeviceService;
import org.onosproject.net.flow.DefaultTrafficSelector;
import org.onosproject.net.flow.DefaultTrafficTreatment;
import org.onosproject.net.flow.FlowEntry;
import org.onosproject.net.flow.FlowRule;
import org.onosproject.net.flow.FlowRuleService;
import org.onosproject.net.flow.TrafficSelector;
import org.onosproject.net.flow.TrafficTreatment;
import org.onosproject.net.flowobjective.DefaultForwardingObjective;
import org.onosproject.net.flowobjective.FlowObjectiveService;
import org.onosproject.net.flowobjective.ForwardingObjective;
import org.onosproject.net.host.HostService;
import org.onosproject.net.link.LinkService;
import org.onosproject.net.topology.LinkWeigher;
import org.onosproject.net.topology.TopologyService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;

public class DynamicAdaptiveControlTask extends TimerTask {
    private final Logger log = LoggerFactory.getLogger(getClass());

    private boolean isExit;
    private DeviceService deviceService;
    private LinkService linkService;
    private FlowRuleService flowRuleService;
    private HostService hostService;
    private TopologyService topologyService;
    private FlowObjectiveService flowObjectiveService;
    private ApplicationId appId;
    private int flowTimeout;
    private int flowPriority;
    private boolean isCongested;
    private MonitorUtil monitorUtil;
    private MonitorPacketLoss monitorPacketLoss;
    private Individual solutionTree=null;
    private TempLinkWeight linkWeights;
    private SearchRunner runner;
    private SearchRunner oldRunner;
    private boolean firstOrNot=true;


    private int nextMonitoringCnt;

    DynamicAdaptiveControlTask() {
        isExit = false;
        isCongested = false;
        monitorUtil = new MonitorUtil();
        monitorPacketLoss = new MonitorPacketLoss();
        nextMonitoringCnt = 0;

    }

    @Override
    public void run() {
//        for (Link l: linkService.getLinks()) {
//            if (monitorPacketLoss.getPacketLossRate(l)!=0.0) {
//            System.out.println("********************************");
//
//                System.out.println(l.toString() + " : " + monitorPacketLoss.getPacketLossRate(l));
//                System.out.println(monitorPacketLoss.getBytesSent(l)+" : "+monitorPacketLoss.getBytesReceive(l));
//
//            System.out.println("********************************");
//            }
//        }
       // System.out.println("DynamicAdaptiveControlTask run!");
        log.info("DynamicAdaptiveControlTask run!");
        Set<SrcDstPair> sdSet=monitorUtil.getAllSrcDstPairs( monitorUtil.getAllCurrentFlowEntries());
        if (Config.test) {
            for (SrcDstPair sd : sdSet) {
                log.info(sd.src + " | " + sd.dst + " : " + monitorUtil.getTxBitsPerSec(sd));
            }
        }

        try{
            long initTime = System.currentTimeMillis();
            monitorMaxUtil();
            if (nextMonitoringCnt > 0) {
                nextMonitoringCnt--;
                long computingTime = System.currentTimeMillis() - initTime;
                log.info("Control loop time (ms): " + computingTime);
                return;
            }

            isCongested = monitorLinks();
            if (isCongested) {
                nextMonitoringCnt = Config.NEXT_MONITORING_CNT;
                avoidCongestionBySearch();
            }
            long computingTime = System.currentTimeMillis() - initTime;
            log.info("Control loop time (ms): " + computingTime);
        } catch (Exception e) {
            StringWriter writer = new StringWriter();
            PrintWriter printer = new PrintWriter(writer);
            e.printStackTrace(printer);
            printer.flush();
            log.error("Error: " + writer.toString());
        }
    }

    private void monitorMaxUtil() {
        Iterable<Link> links = linkService.getLinks();
        double max = 0;
        for (Link l : links) {
            double lu = monitorUtil.monitorLinkUtilization(l);
            if (max < lu) {
                max = lu;
            }
        }
        //log.info("monitorMaxUtil     Max util: " + max);
       //System.out.println("monitorMaxUtil     Max util: " + max);
    }

    private boolean monitorLinks() {
        Iterable<Link> links = linkService.getLinks();
        for (Link l : links) {
            double lu = monitorUtil.monitorLinkUtilization(l);
            if (lu >= Config.UTILIZATION_THRESHOLD) {
                //System.out.println(lu);
                log.warn("Congested!!! " + lu);
                return true;
            }
        }
        log.warn("Not congested!");
        //System.out.println("Not congested");
        return false;
    }

    private void avoidCongestionBySearch() {
        runner = new SearchRunner(topologyService, linkService, hostService, monitorUtil,monitorPacketLoss,firstOrNot);
        runner.search();
        log.info("avoidCongestionBySearch");
        //ready to change or delete: whether the flow entry is updated automatically based on the weight?
        resolveCongestion(runner);
        adjustLinkWeight(runner);
        solutionTree=runner.getSolution();
        firstOrNot=false;
        oldRunner=runner;
    }
    public  LinkWeigher getLinkWeights(){
        TempLinkWeight linkWeights=new TempLinkWeight();
       // System.out.println(solutionTree==null);
        if (solutionTree==null){
            return DynamicLinkWeight.DYNAMIC_LINK_WEIGHT;
        }
        //System.out.println(runner.toString());
        Map<Link,Integer> linkWeight=runner.getWeightUsingSolutionTree(solutionTree);
        for (Link l:linkService.getLinks()){
            int weight=linkWeight.get(l);
            if ( weight > Config.LARGE_NUM) {
                weight = (int)Config.LARGE_NUM;
            }
            //System.out.println("for updates link weight: new weights is:"+weight);
            log.info("for updates link weight: new weights of "+l.src().toString()+" : "+l.dst().toString()+" is:"+weight);
            linkWeights.setLinkWeight(l, weight);
        }
        return linkWeights;
    }

    private void adjustLinkWeight(SearchRunner runner) {
        if (runner.isSolvable() == false) {
            log.error("can not be solved");
            return;
        }
        DynamicLinkWeight linkWeight = (DynamicLinkWeight)DynamicLinkWeight.DYNAMIC_LINK_WEIGHT;
        Map<Link,Double> linkWeightPair=runner.getNewWeight();
        linkWeight.lock();
        for (Link l : linkService.getLinks()) {
            double nWeight = linkWeightPair.get(l);
            if (nWeight < 0 || nWeight > Config.LARGE_NUM) {
                nWeight = Config.LARGE_NUM;
            }
            linkWeight.setLinkWeight(l, (int)Math.round(nWeight));
            log.info("adjustLinkWeight       for link: "+l.toString()+" new weight is: "+nWeight);
        }
        linkWeight.unlock();
//        for (Link l:linkService.getLinks()){
//            log.info("new link weight:"+((DynamicLinkWeight)DynamicLinkWeight.DYNAMIC_LINK_WEIGHT).getLinkWeight(l));
//        }
    }

    private void resolveCongestion(SearchRunner runner) {

        if (runner.isSolvable() == false) {
            return;
        }
        Map<SrcDstPair, List<Link>> curSDLinkPathMap = runner.getCurrentLinkPath();
        Map<SrcDstPair, List<Link>> solSDLinkPathMap = runner.getSolutionLinkPath();

        for (SrcDstPair sd : curSDLinkPathMap.keySet()) {
            List<Link> oLinkPath = curSDLinkPathMap.get(sd);
            List<Link> sLinkPath = solSDLinkPathMap.get(sd);
            if (Config.test) {
                log.info(String.valueOf(sLinkPath.size()));
                log.info(String.valueOf(oLinkPath.size()));
            }
            List<Link> lcsLinkPath = runner.findLCS(oLinkPath, sLinkPath);
            addFlowEntry(sd, sLinkPath, lcsLinkPath);
            //delFlowEntryAtSrc(sd, oLinkPath, lcsLinkPath);
        }
    }

    private void delFlowEntryAtSrc(SrcDstPair sd, List<Link> origPath, List<Link> lcs) {
        Host srcHost = hostService.getHost(HostId.hostId(sd.src));
        Host dstHost = hostService.getHost(HostId.hostId(sd.dst));
        DeviceId srcId = srcHost.location().deviceId();
        DeviceId dstId = dstHost.location().deviceId();

        Link oldLink = origPath.get(0);
        if (lcs.size() > 0 && oldLink.equals(lcs.get(0))) {
            return;
        }
        PortNumber inPort = srcHost.location().port();
        PortNumber outPort = oldLink.src().port();
        delFlowEntry(sd, oldLink.src().deviceId(), inPort, outPort);
    }

    private void delFlowEntry(SrcDstPair sd, List<Link> origPath, List<Link> lcs) {
        Host srcHost = hostService.getHost(HostId.hostId(sd.src));
        Host dstHost = hostService.getHost(HostId.hostId(sd.dst));
        DeviceId srcId = srcHost.location().deviceId();
        DeviceId dstId = dstHost.location().deviceId();

        for (int i = 0; i < origPath.size(); i++) {
            Link oldLink = origPath.get(i);
            if (lcs.contains(oldLink)) {
                continue;
            }
            PortNumber inPort = null;
            if (i == 0) {
                inPort = srcHost.location().port();
            } else {
                inPort = origPath.get(i-1).dst().port();
            }
            PortNumber outPort = oldLink.src().port();
            delFlowEntry(sd, oldLink.src().deviceId(), inPort, outPort);
            if (oldLink.dst().deviceId().equals(dstId)) {
                delFlowEntry(sd, oldLink.dst().deviceId(), oldLink.dst().port(), dstHost.location().port());
            }
        }
    }

    private void delFlowEntry(SrcDstPair sd, DeviceId dId, PortNumber inPort, PortNumber outPort) {
        Set<FlowEntry> feSet = monitorUtil.getFlowEntries(sd, dId, inPort, outPort);
        for (FlowEntry fe : feSet) {
            flowRuleService.removeFlowRules((FlowRule) fe);
        }
    }

    private void addFlowEntry(SrcDstPair sd, List<Link> newPath, List<Link> lcs) {
        Host srcHost = hostService.getHost(HostId.hostId(sd.src));
        Host dstHost = hostService.getHost(HostId.hostId(sd.dst));
        DeviceId srcId = srcHost.location().deviceId();
        DeviceId dstId = dstHost.location().deviceId();

        for (int i = 0; i < newPath.size(); i++) {
            Link newLink = newPath.get(i);
            if (lcs.contains(newLink)) {
                continue;
            }
            PortNumber inPort = null;
            if (i == 0) {
                inPort = srcHost.location().port();
            } else {
                inPort = newPath.get(i-1).dst().port();
            }
            PortNumber outPort = newLink.src().port();
            addFlowEntry(sd, newLink.src().deviceId(), inPort, outPort);
            if (newLink.dst().deviceId().equals(dstId)) {
                addFlowEntry(sd, newLink.dst().deviceId(), newLink.dst().port(), dstHost.location().port());
            }
        }
    }

    private void addFlowEntry(SrcDstPair sd, DeviceId dId, PortNumber inPort, PortNumber outPort) {
        TrafficTreatment treatment = DefaultTrafficTreatment.builder()
                .setOutput(outPort)
                .build();
        TrafficSelector selector = DefaultTrafficSelector.builder()
                .matchInPort(inPort)
                .matchEthSrc(sd.src)
                .matchEthDst(sd.dst)
                .build();

        ForwardingObjective forwardingObjective = DefaultForwardingObjective.builder()
                .withSelector(selector)
                .withTreatment(treatment)
                .withPriority(flowPriority)
                .withFlag(ForwardingObjective.Flag.VERSATILE)
                .fromApp(appId)
                .makeTemporary(flowTimeout)
                .add();

        flowObjectiveService.forward(dId,
                                     forwardingObjective);

        Host srcHost = hostService.getHost(HostId.hostId(sd.src));
        Host dstHost = hostService.getHost(HostId.hostId(sd.dst));
        DeviceId srcId = srcHost.location().deviceId();
        DeviceId dstId = dstHost.location().deviceId();
        log.info("addFlowEntry     Src: " + srcId + " " + sd.src + " Dst: " + dstId + " " + sd.dst + " Add dId: " + dId + " inPort: " + inPort + " outPort: " + outPort);
    }

    public void setExit(boolean isExit) {
        this.isExit = isExit;
    }

    public void setDeviceService(DeviceService service) {
        this.deviceService = service;
        monitorUtil.setDeviceService(service);
        monitorPacketLoss.setDeviceService(service);
    }

    public void setLinkService(LinkService service) {
        this.linkService = service;
        monitorUtil.setLinkService(service);
        monitorPacketLoss.setLinkService(service);
    }

    public void setFlowRuleService(FlowRuleService service) {
        this.flowRuleService = service;
        monitorUtil.setFlowRuleService(service);
    }

    public void setAppId(ApplicationId id) {
        this.appId = id;
        monitorUtil.setApplicationId(id);
    }

    public void setHostService(HostService service) {
        this.hostService = service;
        monitorUtil.setHostService(service);
    }

    public void setTopologyService(TopologyService service) {
        this.topologyService = service;
        monitorUtil.setTopologyService(service);
    }

    public void setFlowTimeout(int timeout) {
        this.flowTimeout = timeout;
    }

    public void setFlowPriority(int priority) {
        this.flowPriority = priority;
    }

    public void setFlowObjectiveService(FlowObjectiveService service) {
        this.flowObjectiveService = service;
    }
}
