package railway.g1;

import java.io.Serializable;
import java.nio.channels.FileChannel.MapMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Arrays;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import railway.sim.utils.*;
// To access data classes.


public class Player implements railway.sim.Player {
    // Random seed of 42.
    private int seed = 42;
    private Random rand = new Random(seed);
    private double START_BUDGET;
    
    public String name;
    
    private double[][] min_price; //minimum price to buy  link i,j
    private double[][] revenue; //revenue of link i,j
    private int[][] min_path; //record whether link i-j is a minimum path from i to j
    private Map<String, Integer> map; // this map is used to query index of town from townLookup

    private List<BidInfo> availableBids = new ArrayList<>();
    private boolean newTournament = true;
    
    // The coordinates of stations, infrastructure and raw transit files are stored for future reference.
    List<Coordinates> geo;
    List<List<Integer>> infra;
    int[][] transit;

    Map<Pair, Double> heatmap;
    
    List<BidInfo> ourlinks = new ArrayList<BidInfo>();
    
    // Keep track of player owned links. It maps a link infra.get(i).get(j), denoted by an integer 
    // pair [i, j], to all player indexes who own that link (null if it is not sold yet). 
    // allLinks provide the same BidInfos but indexed by the bid index.
    Map<Pair, List<BidInfo>> playerOwnedLinks;
    //Map<String, List<BidInfo>> playerLinks;
    List<BidInfo> allLinks;
    
    private Map<String, Double> budgets = new HashMap<String, Double>();
	private Object Pair;
	private int num_players;

    // Use sour_dest_paths.get(i,j).retainAll(contain_paths.get(a,b)) to get paths satisfying both conditions.
    // This can be done in O(n).If you want the paths to contain to links(etc. (a,b), (c,d)), you can just
    // use sour_dest_paths.get(i,j).retainAll(contain_paths.get(a,b)).retainAll(contain-paths.get(c,d)) which can also be done in O(n)
	// With the paths you get, you can easily change the heatmap  weight in O(n).
    // Also you can use contain_paths.get(a,b).retainAll(sour_dest_paths.get(i,j)) to get paths contain (a,b) and start i, end j;
    private Map<Pair, List<List<Integer>>> sour_dest_paths; //paths start from Pair.i1 and end in Pair.i2
    private Map<Pair, List<List<Integer>>> contain_paths; 
    //paths that contains link (Pair.i1, Pair.i2)

    public final class Pair implements Serializable, Comparable<Pair>{
		private static final long serialVersionUID = 3520054221183875559L;
		
		int i1;
    	int i2;
    	Pair(int i1, int i2){
    		this.i1 = i1;
    		this.i2 = i2;
    	}
    	
    	//implement compareTo to make sure the hashmap can work well because JAVA are Object-Orientied
        @Override
        public int compareTo(Pair other){
    	    return Integer.compare(hashCode(), other.hashCode());
        }
        
    	@Override
        public boolean equals(Object o) {
    		try {
    			return (i1 == ((Pair)o).i1 && i2 == ((Pair)o).i2);
    		}
    		catch (Exception e) {
    			return false;
    		}
        }
        
        @Override
        public int hashCode() {
        	return Integer.valueOf(i1 * geo.size() + i2).hashCode();
        }
        
        @Override
        public String toString() {
        	return (i1 + " " + i2);
        }
    }

    private double getDistance(int t1, int t2) {
        return Math.pow(
                Math.pow(geo.get(t1).x - geo.get(t2).x, 2) +
                        Math.pow(geo.get(t1).y - geo.get(t2).y, 2),
                0.5);
    }

    private double pathDistance(List<Integer> path) {
        double distance = 0.0;
        for(int i=1;i<=path.size();i++) {
            distance += getDistance(i-1,i);
        }
        return distance;
    }

    //return links along ksp
    private List<List<Integer>> yenKSPaths(WeightedGraph g, int source, int sink, int k) {
        System.out.println("source"+source+" sink"+sink);
        int[][] prev = Dijkstra.dijkstra(g, source);
        List<List<Integer>> allP = Dijkstra.getPaths(g,prev,sink);
        List<List<Integer>> kpaths = new ArrayList<>(allP);
        if(allP.get(0).size()==2) {
            return allP;
        }

        /*for(int a = 0;a<allP.size();a++) {
            for (int b = 0;b<allP.get(a).size();b++) {
                System.out.println("a: "+a+", b: "+b+" path: "+allP.get(a).get(b));
            }
        }*/

        if (kpaths.size() < k) {
            List<List<Integer>> potential = new ArrayList<>();
            //rest of Yen's algo
            for (int i=kpaths.size()-1;i<k-1;i++) { // index 3 - 9 (k=10)
                //System.out.println("I: "+i);
                for(int j = 1;j<kpaths.get(i).size()-2;j++) { //size of path = 5, (1,2)
                    WeightedGraph gtemp = new WeightedGraph(geo.size());
                    for (int a=0; a<infra.size(); ++a) {
                        for (int b=0; b<infra.get(a).size(); ++b) {
                            gtemp.addEdge(a, infra.get(a).get(b), getDistance(a, infra.get(a).get(b)));
                        }
                    }

                    //get spur node
                    int spur = kpaths.get(i).get(j);
                    //System.out.println("spur:"+spur);
                    List<Integer> rootpath = new ArrayList<Integer>();
                    for(int a=0;a<j+1;a++) {
                        rootpath.add(kpaths.get(i).get(a));
                    } // 0, 8
                    for (List<Integer> path: kpaths) {
                        if(path.size() > j) {
                        
                            List<Integer> temp = new ArrayList<Integer>(path.subList(0,j+1));
                            //System.out.println(temp.equals(rootpath));
                            if (temp.equals(rootpath)) {
                                //System.out.println("j"+j);
                                System.out.println(path.size());
                                if(j!=path.size()-1) {
                                    System.out.println("removed "+path.get(j)+" "+path.get(j+1));

                                    gtemp.removeEdge(path.get(j),path.get(j+1));

                                }
                            }
                        }
                        else {
                            System.out.println("kpaths:");
                            for(List<Integer> p:kpaths) {
                                System.out.println("line:");
                                for (int m=0;m<p.size();m++) {
                                    System.out.println(m+":"+p.get(m));
                                }
                            }
                        }
                    }

                    //TODO remove notes on rootpath except spurnode
                    for(int a=0;a<rootpath.size()-1;a++) {
                        int[] neighbors = g.neighbors(rootpath.get(a));
                        for(int b=0;b<neighbors.length;b++) {

                            g.removeEdge(rootpath.get(a),neighbors[b]);
                            g.removeEdge(neighbors[b],rootpath.get(a));
                        }
                    }
                    
                    int[][] spurprev = Dijkstra.dijkstra(gtemp, spur);
                    List<List<Integer>> spurPaths = Dijkstra.getPaths(gtemp,spurprev,sink);
                    for(int a = 0;a<spurPaths.size();a++) {
                        List<Integer> totalPath = new ArrayList<>(rootpath);

                        totalPath.addAll(spurPaths.get(a).subList(1,spurPaths.get(a).size()));
                        potential.add(totalPath);
                    }
                }

                //System.out.println("potential:"+potential.size());
                if (potential.size() == 0) {
                    break;
                }

                //sort potentials by cost, //get shortest out of all paths
                double min = Double.MAX_VALUE;
                List<Integer> minindxs = new ArrayList<Integer>();
                for(int a=0;a<potential.size();a++) {
                    double temp = pathDistance(potential.get(a));
                    //System.out.println("distance:"+temp);
                    if(temp < min) {
                        min = temp;
                        if(minindxs.size() > 0) {
                            minindxs.clear();
                        }
                        minindxs.add(a);
                    } 
                    else if (temp == min) {
                        minindxs.add(a);
                    }
                }
                //System.out.println(minindxs.size());
                //System.out.println(potential.size());
                Collections.sort(minindxs,Collections.reverseOrder());

                for(int a=0;a<minindxs.size();a++) {
                    //System.out.println("hi");
                    kpaths.add(potential.get((int)minindxs.get(a)));
                    //System.out.println(minindxs.get(a));
                    potential.remove((int)minindxs.get(a));
                }
                //System.out.println(potential.size());
                //System.out.println("current size: "+kpaths.size());
                if (kpaths.size() >= k) {
                    break;
                }
            }

        }

        //System.out.println("done: "+kpaths.size());
        /*for(int a=0;a<kpaths.size();a++) {
            for(int b=0;b<kpaths.get(a).size();b++) {
                System.out.println("a: "+a+", b: "+b+", path:"+kpaths.get(a).get(b));
            }
        }*/
        return kpaths;
    }



    private void yenKSP(int k) {
        // a list of integers is a path, list of a list of integers 

        System.out.println("YENKSP");
        WeightedGraph g = new WeightedGraph(geo.size());
        for (int i=0; i<infra.size(); ++i) {
            for (int j=0; j<infra.get(i).size(); ++j) {
                g.addEdge(i, infra.get(i).get(j), getDistance(i, infra.get(i).get(j)));
            }
        }
        for (int i=0;i<transit.length;i++) {
            for (int j=0;j<transit[i].length;j++) { //int j=0;j<transit[i].length;j++
                if(transit[i][j]==0) {
                    continue;
                }
                yenKSPaths(g,i,j,k);
            }
            //break;
        }

    }

    /**
     * This function will generate a revenue matrix for the problem
     * It will use dijkstra method to find the shortest path for i th node to j th node
     * And store the result in revenue[i][j], also this will give us the minimum price to buy links
     * @return the revenur matrix
     */
    private double[][] getRevenue() {
        int n = geo.size();
        // Create the graph.
        WeightedGraph g = new WeightedGraph(n);
        for (int i=0; i<infra.size(); ++i) {
            for (int j=0; j<infra.get(i).size(); ++j) {
                g.addEdge(i, infra.get(i).get(j), getDistance(i, infra.get(i).get(j)));
            }
        }
       // System.out.println("tag2");
        // Compute the revenue between any two nodes.
        revenue = new double[n][n];
        min_price = new double[n][n];
        min_path =  new int[n][n];
        for (int i=0; i<n; ++i) {
            int[][] prev = Dijkstra.dijkstra(g, i);
            for (int j=i+1; j<n; ++j) {
                List<List<Integer>> allPaths = Dijkstra.getPaths(g, prev, j);
                //System.out.println("tag3"+ j +i + n);
                double cost = 0;
                for (int k=0; k<allPaths.get(0).size()-1; ++k) {
                    cost += getDistance(allPaths.get(0).get(k), allPaths.get(0).get(k+1));
                }

                for (int k=0; k<allPaths.size(); ++k) {
                    //System.out.println("tag4"+" "+allPaths.get(k).get(0)+" "+allPaths.get(k).get(allPaths.get(0).size()-1));
                    min_path[allPaths.get(k).get(0)][allPaths.get(k).get(allPaths.get(0).size()-1)] = 1;            //record minimum path here
                    //System.out.println("tag6");
                    min_path[allPaths.get(k).get(allPaths.get(0).size()-1)][allPaths.get(k).get(0)] = 1;            //record minimum path here
                }
                //System.out.println("tag5");
                revenue[i][j] = cost * transit[i][j] * 10;
                min_price[i][j] = revenue[i][j]/allPaths.size();
            }
        }
        return revenue;
    }


    public Player() {
        rand = new Random();
        /*try {
			TimeUnit.SECONDS.sleep(10);
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}*/
    }

    public void init(String name, double budget, List<Coordinates> geo, List<List<Integer>> infra, 
    					int[][] transit, List<String> townLookup, List<BidInfo> allBids) {
    	this.geo = geo;
    	this.infra = infra;
    	this.transit = transit;
    	this.name = name;
    	START_BUDGET = budget;
        this.budgets.put(name, START_BUDGET);
        //System.out.println("Player name: " + name);
        this.revenue = getRevenue();
        //System.out.println("tag2");
        map = new HashMap<String, Integer>();
        //System.out.println("tag3");
        for (int i = 0; i < townLookup.size(); i++) {
            map.put(townLookup.get(i),i);
        }
        // Initialize playerOwnedLinks
        allLinks = allBids;
        playerOwnedLinks = new HashMap<Pair, List<BidInfo>>();
        
        Pair link;
        for (int i = 0; i < allLinks.size(); i++) {
			link = new Pair(townLookup.indexOf(allLinks.get(i).town1), townLookup.indexOf(allLinks.get(i).town2));
        	playerOwnedLinks.putIfAbsent(link, new LinkedList<BidInfo>());
        	List<BidInfo> entries = playerOwnedLinks.get(link);
        	entries.add(allLinks.get(i));
        }
        
        /*for (Pair p : playerOwnedLinks.keySet()) {
        	System.out.print(p.i1 + " " + p.i2 + ": ");
        	for (String s : playerOwnedLinks.get(p))
        		System.out.print((s==null));
        	System.out.println();
        }*/
        num_players = 3;
        heatmap = createHeatMap();

        yenKSP(10);

    }

    
    /**
     * Update ownerships and remaining budgets for all players.
     */
    public void updateStatus(Bid lastRoundMaxBid) {
    	if (lastRoundMaxBid != null) {
    		BidInfo b1 = allLinks.get(lastRoundMaxBid.id1);
    		double budget = budgets.getOrDefault(lastRoundMaxBid.bidder, START_BUDGET);
    		budgets.put(lastRoundMaxBid.bidder, budget - lastRoundMaxBid.amount);
    		b1.owner = lastRoundMaxBid.bidder;
    		b1.amount = lastRoundMaxBid.amount;
    		if (lastRoundMaxBid.id2 != -1) {
    			BidInfo b2 = allLinks.get(lastRoundMaxBid.id2);
        		b2.owner = lastRoundMaxBid.bidder;
        		b2.amount = lastRoundMaxBid.amount;
    		}
    	}
    	/*
    	for (int i = 0; i < currentState.size(); i++) {
    		if ((currentState.get(i).owner != null) && !currentState.get(i).owner.equals(lastState.get(i).owner)) {
    			List<Integer> ownerList = playerOwnedLinks.get(new Pair(map.get(currentState.get(i).town1),
    														map.get(currentState.get(i).town2)));
    			System.out.println(new Pair(map.get(currentState.get(i).town1),
    														map.get(currentState.get(i).town2)));
    			int index = players.indexOf(currentState.get(i).owner);
    			if (index == -1) {
    				index = players.size();
    				players.add(currentState.get(i).owner);
    				budgets.add(START_BUDGET);
    			}
    			ownerList.remove(Integer.valueOf(-1));
    			ownerList.add(index);
    	        budgets.set(index, budgets.get(index) - currentState.get(i).amount);
    			}
    		}
		lastState = currentState;
		*/
    }
    
    /**
     * Calculate the expected amount of traffic for each link in the infrastructure, for one specific player.
     * If an indirect route involves a link not owned by any player yet, the expectation assumes no 
     * switching penalty, but the revenue is divided by all possible routes proportional to distance 
     * of each route. Note that we will get fraction population number.
     * @param playerOwnedLinks2 a current or hypothetical map keeping track of ownership of each link
     * @param player the name of player
     * @return a map from each link denoted by an integer pair [i, j] to its corresponding traffic.
     * @see playerOwnedLinks
     */


    public Map<Pair, Double> createHeatMap() {
        sour_dest_paths = new HashMap<Pair, List<List<Integer>>>();
        contain_paths =  new HashMap<Pair, List<List<Integer>>>();
    	Map<Pair, Double> heatmap = new HashMap<Pair, Double>();
        for(int i=0;i<infra.size();i++) {
            for(int j=0;j<infra.get(i).size();j++) {
                heatmap.put(new Pair(i,infra.get(i).get(j)),0.0);
            }
        }
    	// init - nothing is owned, info only from transit and infra
        int n=geo.size();
        WeightedGraph g = new WeightedGraph(n);
        for (int i=0; i<infra.size(); ++i) {
            for (int j=0; j<infra.get(i).size(); ++j) {
                g.addEdge(i, infra.get(i).get(j), getDistance(i, infra.get(i).get(j)));
            }
        }

        for (int i=0;i<transit.length;i++) {
            for (int j=0;j<transit[i].length;j++) { //int j=0;j<transit[i].length;j++
                if(transit[i][j]==0) {
                    continue;
                }
                double totaltransit = transit[i][j];
                int[][] prev = Dijkstra.dijkstra(g, i);
                List<List<Integer>> allP = Dijkstra.getPaths(g,prev,j);
                sour_dest_paths.put(new Pair(i,j), allP);
                int pathnum = allP.size();
                double transitpp = totaltransit / pathnum; 
                //System.out.println("transitpp: "+transitpp);

                for(int a=0;a<allP.size();a++) {
                    for(int b=0;b<allP.get(a).size();b++) {
                        //divide up transitpp based on distance
                        if(b>0) {
                            int t1 = allP.get(a).get(b-1); //0
                            int t2 = allP.get(a).get(b); //1
                            Pair con = new Pair(b-1,b);
                            if(contain_paths.get(con)==null){
                                List<List<Integer>> pa = new ArrayList<>();
                                pa.add(allP.get(a));
                                contain_paths.put(con, pa);
                            }else{
                                List<List<Integer>> pa = contain_paths.get(con);
                                pa.add(allP.get(a));
                                contain_paths.put(con, pa);
                            }

                            //find heatmap key
                            Pair link = new Pair(0,0);
                            for(Pair p:heatmap.keySet()) {
                                if (p.i1==t1) {
                                    if(p.i2==t2) {
                                        link = p;
                                    }
                                }
                            }
                            if (link.i1==0 && link.i2 == 0) {
                                for(Pair p:heatmap.keySet()) {
                                    if (p.i1==t2) {
                                        if(p.i2==t1) {
                                            link = p;
                                        }
                                    }
                                }
                            }
                            heatmap.put(link,heatmap.get(link)+transitpp/num_players);
                        }
                    }
                }
            }
        }

        for (Pair p:heatmap.keySet()) {
            System.out.println("t1: "+p.i1+", t2: "+p.i2+", traffic: "+heatmap.get(p));
        }

    	return heatmap;
    }

    public Map<Integer, Double> predictHeatMap(List<BidInfo> hypotheticalState, String player) {
        // map for each bid info an expected traffic
        Map<Integer, Double> newmap = new HashMap<Integer, Double>();


        return newmap;
    }



    // This is to Query the info of according bid
    private BidInfo QBidInfo(Bid bid, List<BidInfo> allBids){
        for(BidInfo bi : allBids){
            if(bi.id == bid.id1){
                return bi;
            }
        }
        return null;
    }
    
    public Bid getBid(List<Bid> currentBids, List<BidInfo> allBids, Bid lastRoundMaxBid) {
    	// TODO Find a way such that we bid on a link that gives us 0 benefit externally (?)
    	// while giving other links that we owned higher traffics. I am not sure how to do this right now.
    	
    	// Update status & heat map
    	if (newTournament) {
    		updateStatus(lastRoundMaxBid);
    		newTournament = false;
    	}
    	
    	
    	/*
    	Map<Integer, Double> ourMap = predictHeatMap(null, name);
    	
    	List<Map<Integer, Double>> heatmaps = new ArrayList<Map<Integer, Double>>();
    	for (String p : budgets.keySet())
    		if (p != name)
    			heatmaps.add(predictHeatMap(null, p));
    	
    	// Runtime O(cn)
    	// Find the difference between our expected traffic and maximum traffic of other players
    	Map<Integer, Double> mapDiff = new HashMap<Integer, Double>();
    	for (int i = 0; i < allLinks.size(); ++i) {
    		double max = -Double.MAX_VALUE;
    		for (int pindex = 0; pindex < heatmaps.size(); ++pindex) {
    			double temp;
    			if ((temp = heatmaps.get(pindex).get(Integer.valueOf(i))) > max)
    				max = temp;
    		}
    		mapDiff.put(Integer.valueOf(i), ourMap.get(Integer.valueOf(i)) - max);
    	}
    	
    	
    	// Sort the heatmap difference
	List<Map.Entry<Integer, Double>> sortedDiff = new ArrayList<Map.Entry<Integer, Double>>(mapDiff.entrySet());
    	Collections.sort(sortedDiff, Collections.reverseOrder((x, y) -> {
    		if (allLinks.get(x.getKey()).owner == null)
    			return 1;
    		if (allLinks.get(y.getKey()).owner == null)
    			return -1;
    		return Double.compare(x.getValue(), y.getValue());
    	}));
    	
	Bid makeBid = new Bid();
    	for (Map.Entry<Integer, Double> link : sortedDiff) {
		if (link.getKey() != null)
    			continue;
    		if (ourMap.get(link.getKey()) < lastRoundMaxBid.amount)
    			continue;
		// Make a bid
		double historyMax = -1.0D;
	    	for (Bid b: currentBids) {
            		if ((b.id1 == link.getKey()) || (b.id2 == link.getKey()))
            			historyMax = Double.max(historyMax, b.amount);
            	}

		makeBid.id1 = link.getKey();
		makeBid.amount = Double.max(ourMap.get(link.getKey()), historyMax + 10000.0D);
    		
    		if (link.getValue() < 0)
    			return null;
    	}
    	
        if (budgets.get(name) - makeBid.amount < 0) {
            return null;
        }
    	
    	*/
    	 
    	
    	
    	
    	
    	
        double amount = -1;
        int id = -1;
        double maxamount = amount;
        int maxid = id;
        if (availableBids.size() != 0) {
            return null;
        }
        for (BidInfo bi : allBids) {
            if (bi.owner == null) {
                if(ourlinks.size()==0){
                	//if (predictHeatMap(null, name).get(new Pair(map.get(bi.town1), map.get(bi.town2))) == 10) {
                    if(min_path[map.get(bi.town1)][map.get(bi.town2)]!=1){
                        continue;
                    }
                }      //we will start with a shortest link
                availableBids.add(bi);
                amount = revenue[map.get(bi.town1)][map.get(bi.town2)];
                for(BidInfo bi1 : ourlinks){
                    if(bi1.town1.equals(bi.town1)){
                        if(min_path[map.get(bi1.town2)][map.get(bi.town2)]==1){
                            amount = amount + min_price[map.get(bi1.town2)][map.get(bi.town2)];
                            continue;
                        }
                    }
                    if(bi1.town2.equals(bi.town1)){
                        if(min_path[map.get(bi1.town1)][map.get(bi.town2)]==1){
                            amount = amount + min_price[map.get(bi1.town1)][map.get(bi.town2)];
                            continue;
                        }

                    }
                    if(bi1.town1.equals(bi.town2)){
                        if(min_path[map.get(bi1.town2)][map.get(bi.town1)]==1){
                            amount = amount + min_price[map.get(bi1.town2)][map.get(bi.town1)];
                            continue;
                        }

                    }
                    if(bi1.town2.equals(bi.town2)){
                        if(min_path[map.get(bi1.town1)][map.get(bi.town1)]==1){
                            amount = amount + min_price[map.get(bi1.town1)][map.get(bi.town1)];
                            continue;
                        }

                    }
                }
                if(amount > maxamount){
                    maxamount = amount;
                    maxid = bi.id;
                }

            }


        }

        if (availableBids.size() == 0) {
            return null;
        }



//        BidInfo randomBid = availableBids.get(rand.nextInt(availableBids.size()));



        // Check if another player has made a bid for this link.
//
//        for (BidInfo bi : currentBids) {
//
//        }
        
        double historyMax = -1;
        for (Bid b: currentBids) {
        	if ((b.id1 == maxid) || (b.id2 == maxid))
        		historyMax = Double.max(historyMax, b.amount);
        }
        
        
        Bid bid = new Bid();
        bid.amount = Double.max(maxamount, historyMax + 10000.0D);
        bid.id1 = maxid;
        
        // Don't bid if the random bid turns out to be beyond our budget.
        System.err.println(budgets.get(name));
        System.err.println(bid.amount+" "+maxid);
        if (budgets.get(name) - bid.amount < 0) {
            return null;
        }
        
        return bid;
    }

    public void updateBudget(Bid bid) {
    	// TODO
        if (bid != null) {
        	
        	ourlinks.add(allLinks.get(bid.id1));
        	if (bid.id2 != -1)
        		ourlinks.add(allLinks.get(bid.id2));
            //budget.set(0, budget.get(0) - bid.amount);
        }
        newTournament = true;
        availableBids = new ArrayList<>();
    }
}
