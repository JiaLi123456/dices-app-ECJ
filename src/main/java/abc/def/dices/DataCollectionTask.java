package abc.def.dices;

import ec.Individual;
import org.onosproject.core.ApplicationId;
import org.onosproject.net.DeviceId;
import org.onosproject.net.Link;
import org.onosproject.net.PortNumber;
import org.onosproject.net.device.DeviceService;
import org.onosproject.net.device.PortStatistics;
import org.onosproject.net.flow.FlowRuleService;
import org.onosproject.net.flowobjective.FlowObjectiveService;
import org.onosproject.net.host.HostService;
import org.onosproject.net.link.LinkService;
import org.onosproject.net.topology.TopologyService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.sql.Time;
import java.util.ArrayList;
import java.util.List;
import java.util.TimerTask;

public class DataCollectionTask extends TimerTask {

    private DeviceService deviceService;
    private LinkService linkService;
    private FlowRuleService flowRuleService;
    private HostService hostService;
    private TopologyService topologyService;
    private FlowObjectiveService flowObjectiveService;
    private MonitorUtil monitorUtil;

    private Boolean isExist;
    private FileWriter fw;
   // private FileWriter fw2;
    private int time;

    DataCollectionTask(){
        isExist=false;
        time=0;

        File file=new File("./dataCollection");

        try {
            fw=new FileWriter(file);
            //fw2=new FileWriter(file2);
        } catch (IOException e) {
            System.out.println(e);
            e.printStackTrace();
        }
    }


    @Override
    public void run() {
        double utilization=getMaxUtilization();

        long totalMilliSeconds = System.currentTimeMillis();
        long totalSeconds = totalMilliSeconds / 1000;

        long currentSecond = totalSeconds % 60;

        long totalMinutes = totalSeconds / 60;
        long currentMinute = totalMinutes % 60;

        try {
            fw.write(utilization+"\t"+time+"\t"+currentMinute+":"+currentSecond+"\r\n");
            time=time+1;
            //fw.write("\n");
        } catch (IOException e) {
            e.printStackTrace();
        }
        try {
            fw.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }

    }
    private double getMaxUtilization(){
        double result=0;
        for (Link l : linkService.getLinks()){
            long bits=getDeltaTxBits(l);
            double utilization=calculateUtilization(l,bits);
            if (result<utilization){
                result =utilization;
            }
        }
        return  result;
    }

    public void setLinkService(LinkService ls){
        this.linkService=ls;
    }
    public void setDeviceService(DeviceService ds){
        this.deviceService=ds;
    }
    public void setExist(){
        this.isExist=true;
    }
    public long getDeltaTxBits(Link l) {
        DeviceId deviceId = l.src().deviceId();
        PortNumber txPortNum = l.src().port();
        PortStatistics portStats = deviceService.getDeltaStatisticsForPort(deviceId, txPortNum);

        if (portStats == null) {
            return 0;
        }
//        if (Config.test) {
//            log.info("getDeltaTxBits  portStats.bytesSent() * 8   " + portStats.bytesSent() * 8);
//        }
        return portStats.bytesSent() * 8;
    }
    public double calculateUtilization(Link l, long throughputPerSec) {
        String annotateVal = l.annotations().value(Config.BANDWIDTH_KEY);
        if (annotateVal == null) {
            annotateVal = Config.DEFAULT_BANDWIDTH;
        }

        long bandwidth = stringToLong(annotateVal); //Mbps
        bandwidth = convertMbitToBit(bandwidth);

        // log.info("calculateUtilization(Link l, long throughputPerSec)=="+(double)throughputPerSec / (double)bandwidth);
        return (double)throughputPerSec / (double)bandwidth;
    }
    public long stringToLong(String value) {
        return Long.valueOf(value);
    }
    public long convertMbitToBit(long Mbps) {
        return Mbps * 1000 * 1000;
    }
}
