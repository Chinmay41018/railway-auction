package railway.g8;

import railway.sim.utils.BidInfo;
import railway.sim.utils.Coordinates;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class GraphUtility {

    public List<Coordinates> geo;
    public List<List<Integer>> infra;
    public int[][] transit;
    public List<String> townLookup;

    public double[][] adj; //adjacency matrix: direct connection between towns and their distance
    public double[][] dist; //shortest distance between all pairs
    public List[][] path; //shortest paths between all pairs
    public int[][] transfer; //shortest transfer times
    public SwitchRoute[][] switches; //times to switch between different company
    public HashMap<Coordinates, BidInfo> segmentColorMap; //map from id to owner
    public HashMap<String, Integer> lookUpTown; //from town back to id, i.e. index in geo
    public int[][] edgeWeight; //traffic on each segment

    public GraphUtility(List<Coordinates> geo, List<List<Integer>> infra, int[][] transit, List<String> townLookup) {
        this.geo = geo;
        this.infra = infra;
        this.transit = transit;
        this.townLookup = townLookup;

        lookUpTown = new HashMap<>();
        int idx = 0;
        for (String s : this.townLookup) {
            lookUpTown.put(s, idx++);
        }
        segmentColorMap = new HashMap<>();

        initAdj();
        try {
            floydWarshall();
        } catch (Exception e) {
//            System.out.println("Exception G8");
        }

        initEdgeWeight();
        //now adj, dist, path are available to uses
//        System.out.println("DEBUG: FloydWarshall done");
    }

    private void initAdj() {
        int townSize = geo.size(); //get total size of towns
        adj = new double[townSize][townSize]; //init 2D adjacency matrix
        transfer = new int[townSize][townSize]; //init 2D transfer matrix (A->B if connected, default transfer is set to 1, if not connected then inf)
        switches = new SwitchRoute[townSize][townSize]; //init switches
        for (int i = 0; i < townSize; i++) { //init values, if no link return INF
            for (int j = 0; j < townSize; j++) {
                if (infra.get(i).contains(j) || infra.get(j).contains(i)) { //connected
                    adj[i][j] = Euclidean(geo.get(i), geo.get(j));
                    transfer[i][j] = 1;
                    switches[i][j] = new SwitchRoute(new Coordinates(i, j), new Coordinates(i, j), 0);
                } else { //set to infinity
                    adj[i][j] = Double.POSITIVE_INFINITY;
                    transfer[i][j] = -1;
                }
            }
        }
    }

    //call this after initAdj() and floydWarshall()
    private void initEdgeWeight() {
        int townsize = geo.size();
        edgeWeight = new int[townsize][townsize];
        for (int i = 0; i < townsize; i++) {
            for (int j = i + 1; j < townsize; j++) {
                if (adj[i][j] == Double.POSITIVE_INFINITY) continue;
                edgeWeight[i][j] = edgeWeight[j][i] = transit[i][j];
            }
        }
        for (int i = 0; i < townsize; i++) {
            for (int j = i + 1; j < townsize; j++) {
                List<Integer> townPath = path[i][j];
                if (townPath.size() < 3)
                    continue;
                for (int k = 0; k < townPath.size() - 1; k++) {
                    edgeWeight[townPath.get(k)][townPath.get(k + 1)] += transit[i][j];
                }
            }
        }
    }

    //call this after initAdj()
    private void floydWarshall() { //run algorithm and modify adj, dist, path
        int townSize = geo.size();
        dist = new double[townSize][townSize]; //distance matrix
        int[][] next = new int[townSize][townSize]; //for path reconstruction

        //init dist and pathTrace
        for (int i = 0; i < townSize; i++) {
            for (int j = 0; j < townSize; j++) {
                dist[i][j] = adj[i][j];
                if (infra.get(i).contains(j) || infra.get(j).contains(i)) { //connected
                    next[i][j] = j;
                } else next[i][j] = -1;
            }
        }

        //dp with path
        for (int k = 0; k < townSize; k++) {
            for (int i = 0; i < townSize; i++) {
                for (int j = 0; j < townSize; j++) {
                    double pij = getDistanceWithPenalty(i, j);
                    double pik = getDistanceWithPenalty(i, k);
                    double pkj = getDistanceWithPenalty(k, j);
                    if (pik != Double.POSITIVE_INFINITY && pkj != Double.POSITIVE_INFINITY) {
                        if (pij == pik + pkj) { //if equal then choose better transfer
                            if (transfer[i][j] > transfer[i][k] + transfer[k][j]) { //less transfer
                                transfer[i][j] = transfer[i][k] + transfer[k][j];
                                dist[i][j] = dist[i][k] + dist[k][j]; //update dist
                                next[i][j] = next[i][k];
                                switches[i][j] = mergeSwitchRoute(switches[i][k], switches[k][j]);
                            }
                        } else if (pij > pik + pkj) {
                            dist[i][j] = dist[i][k] + dist[k][j]; //update dist
                            transfer[i][j] = transfer[i][k] + transfer[k][j]; //update transfer
                            next[i][j] = next[i][k]; //update path
                            switches[i][j] = mergeSwitchRoute(switches[i][k], switches[k][j]);
                        }
                    }
                }
            }
        }

        path = new List[townSize][townSize];
        //get back all paths
        for (int i = 0; i < townSize; i++) {
            for (int j = 0; j < townSize; j++) {
                path[i][j] = pathTrace(i, j, next);
            }
        }
    }

    private List<Integer> pathTrace(int i, int j, int[][] next) {
        List<Integer> ret = new ArrayList<>();
        if (next[i][j] == -1) return ret;
        ret.add(i);
        while (i != j) {
            i = next[i][j];
            ret.add(i);
        }
        return ret;
    }

    public double Euclidean(Coordinates a, Coordinates b) {
        return Math.sqrt((a.x - b.x) * (a.x - b.x) + (a.y - b.y) * (a.y - b.y));
    }

    public double getDistanceWithPenalty(int i, int j) {
        if (switches[i][j] == null) return Double.POSITIVE_INFINITY;
        double result = 0.0d;
        result += dist[i][j];
        result += switches[i][j].switchCount * 200; //200 is penalty, fixed
        return result;
    }

    private SwitchRoute mergeSwitchRoute(SwitchRoute sr1, SwitchRoute sr2) {
        int sc = sr1.switchCount + sr2.switchCount;
//        if((sr1.endSeg.x == sr2.startSeg.x && sr1.endSeg.y == sr2.startSeg.y) || (sr1.endSeg.y == sr2.startSeg.x && sr1.endSeg.x == sr2.startSeg.y)){ //test same segment
        if (segmentColorMap.get(sr1.endSeg) == null && segmentColorMap.get(sr2.startSeg) == null) {
            return new SwitchRoute(sr1.startSeg, sr2.endSeg, sc);
        } else if (segmentColorMap.get(sr1.endSeg) != null && segmentColorMap.get(sr2.startSeg) != null) {
            if (segmentColorMap.get(sr1.endSeg).owner.compareTo(segmentColorMap.get(sr2.startSeg).owner) == 0) {
                return new SwitchRoute(sr1.startSeg, sr2.endSeg, sc);
            }
        }
        return new SwitchRoute(sr1.startSeg, sr2.endSeg, sc + 1);
    }

    public void update(List<BidInfo> allBids) {
        //update edgeWeight and eval
        //use gu.eval[][] after call this function

        //update colorMap
        for(BidInfo bidInfo: allBids){
            segmentColorMap.put(new Coordinates(lookUpTown.get(bidInfo.town1), lookUpTown.get(bidInfo.town2)), bidInfo);
            segmentColorMap.put(new Coordinates(lookUpTown.get(bidInfo.town2), lookUpTown.get(bidInfo.town1)), bidInfo);
        }

        floydWarshall();
        initEdgeWeight();
    }
}
