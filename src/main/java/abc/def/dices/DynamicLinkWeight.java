package abc.def.dices;

import org.onlab.graph.ScalarWeight;
import org.onlab.graph.Weight;
import org.onosproject.net.Link;
import org.onosproject.net.topology.LinkWeigher;
import org.onosproject.net.topology.TopologyEdge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;


public class DynamicLinkWeight  implements LinkWeigher {
    public static final LinkWeigher DYNAMIC_LINK_WEIGHT = new DynamicLinkWeight();
    private final Lock weightLock = new ReentrantLock();
    private final Logger log = LoggerFactory.getLogger(getClass());
    private static final ScalarWeight ZERO = new ScalarWeight(0D);
    private static final ScalarWeight ONE = new ScalarWeight(1D);
    private Map<Link, ScalarWeight> edgeCostMap;
    public DynamicLinkWeight() {
        edgeCostMap = new HashMap<>();
    }

    public Weight weight(TopologyEdge edge) {
        weightLock.lock();
        Weight weight = edgeCostMap.get(edge.link());
        if (weight == null) {
                edgeCostMap.put(edge.link(), ONE);
                 weightLock.unlock();
                 return edgeCostMap.get(edge.link());
        }
        weightLock.unlock();
        return weight;
    }

    public Weight getInitialWeight() {
        return ONE;
    }

    public Weight getNonViableWeight() {
        return ScalarWeight.NON_VIABLE_WEIGHT;
    }

    public int getLinkWeight(Link l) {
        try{
            ScalarWeight weight = edgeCostMap.get(l);
            //log.info("get link weigth: "+weight.toString());
            return (int)weight.value();
        }catch (Exception e) {
            log.error(l.src().toString());
            log.error(l.dst().toString());
            edgeCostMap.put(l, ScalarWeight.toWeight(1));
            return 1;

        }


    }

    public void setLinkWeight(Link l, int weight) {
        //log.info("set link weight, link "+l.toString()+" weight "+weight);
        edgeCostMap.put(l, new ScalarWeight(weight));
    }

    public void lock() {
        weightLock.lock();
    }

    public void unlock() {
        weightLock.unlock();
    }
}
