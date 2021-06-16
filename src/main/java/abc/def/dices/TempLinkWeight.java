package abc.def.dices;

import org.onlab.graph.ScalarWeight;
import org.onlab.graph.Weight;
import org.onosproject.net.Link;
import org.onosproject.net.topology.LinkWeigher;
import org.onosproject.net.topology.TopologyEdge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class TempLinkWeight implements LinkWeigher{
    private final Lock weightLock = new ReentrantLock();
    private final Logger log = LoggerFactory.getLogger(getClass());

    private static final ScalarWeight ONE = new ScalarWeight(1D);

    private Map<Link, ScalarWeight> edgeCostMap;

    public TempLinkWeight() {
        edgeCostMap = new HashMap<>();
    }

    public Weight weight(TopologyEdge edge) {
        weightLock.lock();
        Weight weight = edgeCostMap.get(edge.link());
        if (weight == null) {
            edgeCostMap.put(edge.link(), ONE);
           // log.info("new temp link weight:"+1);
            // System.out.println("new temp link weight:"+1);
            weightLock.unlock();
            return edgeCostMap.get(edge.link());
        }
        weightLock.unlock();
        //System.out.println("temp weight:"+weight.toString());
       // log.info("temp weight:"+weight.toString());
        return weight;
    }

    public Weight getInitialWeight() {
        return ONE;
    }

    public Weight getNonViableWeight() {
        //System.out.println("getNoViableWeight:"+ScalarWeight.NON_VIABLE_WEIGHT.toString());
        //log.info("getNoViableWeight:"+ScalarWeight.NON_VIABLE_WEIGHT.toString());

        return ScalarWeight.NON_VIABLE_WEIGHT;
    }

    public double getLinkWeight(Link l) {
        ScalarWeight weight = edgeCostMap.get(l);
        return weight.value();
    }

    public void setLinkWeight(Link l, int weight) {
        edgeCostMap.put(l, new ScalarWeight(weight));
    }

    public void lock() {
        weightLock.lock();
    }

    public void unlock() {
        weightLock.unlock();
    }
}
