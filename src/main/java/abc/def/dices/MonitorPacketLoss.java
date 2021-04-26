package abc.def.dices;

import org.onosproject.core.ApplicationId;
import org.onosproject.net.Link;
import org.onosproject.net.device.DeviceService;
import org.onosproject.net.flow.FlowRuleService;
import org.onosproject.net.host.HostService;
import org.onosproject.net.link.LinkService;
import org.onosproject.net.topology.TopologyService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MonitorPacketLoss {
    private final Logger log = LoggerFactory.getLogger(getClass());

    private DeviceService deviceService;
    private LinkService linkService;

    public MonitorPacketLoss(){

    }

    public void setDeviceService(DeviceService service) {
        this.deviceService = service;
    }

    public void setLinkService(LinkService service){
        this.linkService=service;
    }

    public double getPacketLossRate(Link l){
        double send=deviceService.getDeltaStatisticsForPort(l.src().deviceId(),l.src().port()).bytesSent();
        double receive=deviceService.getDeltaStatisticsForPort(l.dst().deviceId(),l.dst().port()).bytesReceived();
        double result=(send-receive)/send;
        if (result<0){
            log.warn("packet loss below 0 : "+(send-receive));
            result=0;
        }
        return result;
    }
    public double getMaxPacketLossRate(){
        double result=0;
        for (Link l: linkService.getLinks()){
            double rate=getPacketLossRate(l);
            if (rate>result){
                result=rate;
            }

        }
        return result;
    }





}
