package railway.g3;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import railway.sim.Simulator;
import java.util.HashSet;
import java.util.Arrays;

// To access data classes.
import railway.sim.utils.*;

// Thanks Sidhi!
class LinkInfo {
    public int town1;
    public int town2;

    public double distance;
}

public class Player implements railway.sim.Player {
    // Random seed of 42.
    //private int seed = 42;
    //private Random rand;

    private double budget; // amount to spend at auction

    private List<Coordinates> geo; // city locations
    private ArrayList<String> townLookup; // city names
    private List<List<Integer>> infra; // graph edges
    private int[][] transit; // travel along paths

    private double[][] revenue; // price to travel along paths
    private int[] connections; // number of connections for links
    private int[] ownedConnections; // number of connections owned--updated throughout the game
    private ArrayList<G3Bid> wonBids = new ArrayList<G3Bid>();

    private int[] ourConnections; // number of connections out of each town that we own
    private boolean[][] ownership; // which links we own
    private List<G3Bid> availableBids = new ArrayList<G3Bid>();

    private List<BidInfo> allBids;
    private boolean needsUpdate = false;
    private G3Bid best;
    private int bidsThisRound;

    private double avgRevenuePerLink;

    public Player() {
        //rand = new Random();
    }

    // Initialization function.
    // name: name of current player.
    // budget: maximum amount allocated for bidding.
    // geo: Town indices and their coordinates.
    // infra: Town indices and other towns they are connected to.
    // transit: Town indices and the number of passengers between them.
    // townLookup: Town indices and names of towns.
    // allBids: list of all available bids and their minimum amount.
    public void init(
        String name,
        double budget,
        List<Coordinates> geo,
        List<List<Integer>> infra,
        int[][] transit,
        List<String> townLookup,
        List<BidInfo> allBids) {

        this.budget = budget;
        this.townLookup = (ArrayList)townLookup;
        this.infra = infra;
        this.geo = geo;
        this.transit = transit;
        this.revenue = getRevenue();
        this.connections = getConnections();
        this.ourConnections = new int[connections.length];
        this.bidsThisRound = 0;
        this.ownedConnections = new int[connections.length];

        // create all single link bids
        double revenueSum = 0;
        for(BidInfo bi: allBids) {
        	int town_id1 = townLookup.indexOf(bi.town1);
        	int town_id2 = townLookup.indexOf(bi.town2);
        	G3Bid bid = new G3Bid(town_id1, town_id2, bi.id, bi.amount);
        	availableBids.add(bid);
        	revenueSum += revenue[town_id1][town_id2];
        	revenueSum += revenue[town_id2][town_id1];
        }
        this.avgRevenuePerLink = revenueSum/availableBids.size();

        // create all double link bids
        List<G3Bid> pairs = new ArrayList<G3Bid>();
        for(int i = 0; i < availableBids.size(); ++i) {
        	G3Bid b1 = availableBids.get(i);
        	for(int j = i+1; j < availableBids.size(); ++j) {
        		G3Bid b2 = availableBids.get(j);

        		if(isPair(b1, b2)) {
        			try {
        				G3Bid pair = new G3Bid(b1, b2);
        				pairs.add(pair);
        			} catch(Exception e) {
        				System.err.println(e);
        			}
        		}
        	}
        }
        availableBids.addAll(pairs);

        // for (double[] row: revenue) {
        // 	for (double d: row) {
        // 		System.err.print(d + " ");
        // 	}
        // 	System.err.println();
        // }

        /*System.out.println("Bids in availableBids:");
        for (G3Bid bid : availableBids) {
            printBid(bid);
        }
        System.out.println("number of links left = " + availableBids.size());*/

        // System.err.println("bids in availableBids");
        // for (G3Bid b : availableBids) {
        //     printBid(b);
        // }

        // System.err.println("testing error in number of bids");
        // for (G3Bid b : availableBids) {
        //     int t_1 = b.town_id1;
        //     int t_2 = b.town_id2;
        //     if (b.town_id3 > -1) {
        //         int t_3 = b.town_id3;
        //         if (isTricycle(t_1, t_2, t_3)) {
        //             System.out.println("CYCLE!!! : " + townLookup.get(t_1) + ", " + townLookup.get(t_2) + ", " + townLookup.get(t_3));
        //         }
        //     }
        // }
    }

    private boolean isPair(G3Bid b1, G3Bid b2) {
    	// false if one bid already represents a pair
    	if(b1.town_id3 > -1 || b2.town_id3 > -1) {
    		return false;
    	}

    	// check for exactly one town in common
    	int t1a, t1b, t2a, t2b;
  		t1a = b1.town_id1;
  		t1b = b1.town_id2;
  		t2a = b2.town_id1;
  		t2b = b2.town_id2;
    	return ((t1a==t2a) || (t1a==t2b) || (t1b==t2a) || (t1b==t2b))
    		&& (t1a!=t2a || t1b!=t2b) && (t1a!=t2b || t1b!=t2a);
    }

    // The bid placed for a round.
    // Returns null if they don't want to place a bid.
    // currentBids: bids being placed in this round in reverse order -
    //   most recent bid is first.
    // allBids: shows those bids available and bids owned by other players
    //   i.e. the results of previous rounds.
    public Bid getBid(List<Bid> currentBids, List<BidInfo> allBids, Bid lastRoundMaxBid) {
        this.allBids = allBids;
        //System.err.println(availableBids.size());

        // remove links now owned by opponent
        if(needsUpdate) {
        	wonBids = new ArrayList<G3Bid>();
            List<G3Bid> copy = new ArrayList<G3Bid>(availableBids.size());
        	for (G3Bid b: availableBids) {
	    		if(!sameLink(b, lastRoundMaxBid)) {
	    			copy.add(b);
	    		} else {
                    wonBids.add(b);
                }
	    	}
	    	availableBids = copy;
	    	needsUpdate = false;

            G3Bid wonBid = null;
    
            for (G3Bid b : wonBids) {
                if ((lastRoundMaxBid.id2 > -1) && (b.id2 > -1)) {
                    if ((lastRoundMaxBid.id1 == b.id1) && (lastRoundMaxBid.id2 == b.id2)) {
                        wonBid = b;
                        break;
                    }
                } else if ((lastRoundMaxBid.id2 < 0) && (b.id2 < 0)) {
                    if (lastRoundMaxBid.id1 == b.id1) {
                        wonBid = b;
                        break;
                    }
                }
            }
            
            ownedConnections[wonBid.town_id1] += 1;
            //System.out.println("ownedConnections[wonBid.town_id1] = " + ownedConnections[wonBid.town_id1]);
            ownedConnections[wonBid.town_id2] += 1;
            //System.out.println("ownedConnections[wonBid.town_id2] = " + ownedConnections[wonBid.town_id2]);
            if (wonBid.town_id3 > -1) {
                ownedConnections[wonBid.town_id3] += 1;
                //System.out.println("ownedConnections[wonBid.town_id3] = " + ownedConnections[wonBid.town_id3]);
            }
            //
            //for (int i=0; i<ownedConnections.length; i++) {
            //    System.out.println("town " + townLookup.get(i) + " has " + ownedConnections[i] + " connections");
            //}
        }

        // update possible bids according to max bids
        for (int i = 0; i < currentBids.size()-bidsThisRound; ++i) {
        	Bid b1 = currentBids.get(i);
        	for (int j = 0; j < availableBids.size(); ++j) {
        		Bid b2 = availableBids.get(j);
        		if(b1.id1==b2.id1 && b1.id2==b2.id2 || b1.id1==b2.id2 && b1.id2==b2.id1) {
        			if(b1.amount + 10000 > b2.amount) {
        				b2.amount = b1.amount + 10000;
        			}
        			break;
        		}
        	}
        }
        bidsThisRound = currentBids.size();

    	// get the price-per-distance of the maximum bid so far in this round
    	Bid maxBid;
    	double maxBidAmt;
    	if (currentBids.size() > 0) {
    		maxBid = Simulator.getMaxBid(currentBids, allBids);
    		maxBidAmt = maxBid.amount/Simulator.getDistance(maxBid);
    		//System.err.println("max bid: " + maxBid.id1 + ", " + maxBid.id2 + " -- amount = " + maxBidAmt);
    	} else {
    		maxBid = null;
    		maxBidAmt = 0;
    	}

    	if(maxBid==null) {
    		// evaluate bid scores
    		for(int i = 0; i < availableBids.size(); ++i) {
				G3Bid cur = availableBids.get(i);
				evaluateBid(cur);
				//printBid(cur);
			}

    		// sort by score and return the best we can afford
    		G3Bid ret = getBestAffordableBid(availableBids);
    		if(ret!=null && ret.score >= 0) {
    			best = ret;
    			return new G3Bid(ret);
    		}
    		return null;
    	}

    	if(maxBid.bidder.equals("g3")) {
    		// already own the winning bid
    		return null;
    	}

    	// past here means another player has the winning bid right now...

		// update all the possible bids to cost what is needed to secure them this round
		for(int i = 0; i < availableBids.size(); ++i) {
			G3Bid cur = availableBids.get(i);
			updateBid(cur, maxBid, maxBidAmt);
			//printBid(cur);
		}

		// evaluate the bid scores
		for(int i = 0; i < availableBids.size(); ++i) {
			G3Bid cur = availableBids.get(i);
			evaluateBid(cur);
			//printBid(cur);
		}

		// sort by score and return the best we can afford
		G3Bid ret = getBestAffordableBid(availableBids);
		if(ret!=null && ret.score >= 0) {
			best = ret;
			return new G3Bid(ret);
		}
		return null;
    }

    private void updateBid(G3Bid bid, Bid highest, double maxBidAmt) {
    	double a = bid.amount;
    	double dist = Simulator.getDistance(bid);
    	double b = Math.floor(1.0 + dist*maxBidAmt);
    	bid.amount = Math.max(a,b);
    }

    private void evaluateBid(G3Bid bid) {
    	// really basic right now -- just computes profit from direct traffic
    	double revenue = 0;

        double c1=0.15, c2=0.25, c3=0.6;
        double hScore_total = 0;


    	if (bid.id2 == -1) {
    		// single link
    		revenue += this.revenue[bid.town_id1][bid.town_id2];
    		revenue += this.revenue[bid.town_id2][bid.town_id1];

            int t1_totalC = connections[bid.town_id1];
            int t2_totalC = connections[bid.town_id2];
            int t1_ourC = ourConnections[bid.town_id1];
            int t2_ourC = ourConnections[bid.town_id2];
            int t1_ownedC = ownedConnections[bid.town_id1];
            int t2_ownedC = ownedConnections[bid.town_id2];
            int t1_remaining = t1_totalC - t1_ourC - t1_ownedC;
            int t2_remaining = t2_totalC - t2_ourC - t2_ownedC;

            double hScore_1 = 0, hScore_2 = 0;
            hScore_1 = c1*t1_remaining - c2*t1_ownedC + c3*t1_ourC;
            hScore_2 = c1*t2_remaining - c2*t2_ownedC + c3*t2_ourC;

            hScore_total = hScore_1 + hScore_2 - Math.abs(hScore_1-hScore_2);

    	} else {
    		// pair link
    		revenue += this.revenue[bid.town_id1][bid.town_id2];
    		revenue += this.revenue[bid.town_id2][bid.town_id1];
    		revenue += this.revenue[bid.town_id2][bid.town_id3];
    		revenue += this.revenue[bid.town_id3][bid.town_id2];
    		//revenue += this.revenue[bid.town_id1][bid.town_id3];
    		//revenue += this.revenue[bid.town_id3][bid.town_id1];

            int t1_totalC = connections[bid.town_id1];
            int t2_totalC = connections[bid.town_id2];
            int t3_totalC = connections[bid.town_id3];
            int t1_ourC = ourConnections[bid.town_id1];
            int t2_ourC = ourConnections[bid.town_id2];
            int t3_ourC = ourConnections[bid.town_id3];
            int t1_ownedC = ownedConnections[bid.town_id1];
            int t2_ownedC = ownedConnections[bid.town_id2];
            int t3_ownedC = ownedConnections[bid.town_id3];


            int t1_remaining = t1_totalC - t1_ourC - t1_ownedC;
            int t2_remaining = t2_totalC - t2_ourC - t2_ownedC;
            int t3_remaining = t3_totalC - t3_ourC - t3_ownedC;

            double hScore_1 = 0, hScore_2 = 0, hScore_3 = 0;
            hScore_1 = c1*t1_remaining - c2*t1_ownedC + c3*t1_ourC;
            hScore_2 = c1*t2_remaining - c2*t2_ownedC + c3*t2_ourC;
            hScore_3 = c1*t3_remaining - c2*t3_ownedC + c3*t3_ourC;

            hScore_total = hScore_1 + hScore_2 - Math.abs(hScore_1-hScore_2) + hScore_2 + hScore_3 - Math.abs(hScore_2-hScore_3);

            
    	}

        if (revenue - bid.amount >= 0){
            bid.score = (0.7)*(revenue-bid.amount) + (0.3)*avgRevenuePerLink*hScore_total;
        }
        else {
            bid.score = (0.95)*(revenue-bid.amount) + (0.05)*avgRevenuePerLink*hScore_total;
        }
    }

    private G3Bid getBestAffordableBid(List<G3Bid> availableBids) {
    	Collections.sort(availableBids);
    	for(G3Bid bid: availableBids) {
    		if(bid.amount < this.budget) {
    			return bid;
    		}
    	}
    	return null; // can't afford any
    }

    // Thanks Sidhi!
    private double[][] getRevenue() {
        final int n = geo.size();

        // Create the graph.
        WeightedGraph g = new WeightedGraph(n);

        for (int i=0; i<n; ++i) {
            g.setLabel(townLookup.get(i));
        }

        for (int i=0; i<infra.size(); ++i) {
            for (int j=0; j<infra.get(i).size(); ++j) {
                g.addEdge(i, infra.get(i).get(j), getDistance(i, infra.get(i).get(j)));
            }
        }

        //g.print();

        // Compute the revenue between any two nodes.
        double[][] revenue = new double[n][n];

        for (int i=0; i<n; ++i) {
            int[][] prev = Dijkstra.dijkstra(g, i);
            for (int j=i+1; j<n; ++j) {
                List<List<Integer>> allPaths = Dijkstra.getPaths(g, prev, j);

                double cost = 0;
                for (int k=0; k<allPaths.get(0).size()-1; ++k) {
                    cost += getDistance(allPaths.get(0).get(k), allPaths.get(0).get(k+1));
                }

                revenue[i][j] = cost * transit[i][j] * 10;
            }
        }

        return revenue;
    }

    // Thanks Sidhi!
    private double getDistance(int t1, int t2) {
        return Math.pow(
            Math.pow(geo.get(t1).x - geo.get(t2).x, 2) +
                Math.pow(geo.get(t1).y - geo.get(t2).y, 2),
            0.5);
    }

    private int[] getConnections() {
        int[] connections = new int[geo.size()];
        for(int i = 0; i < connections.length; ++i) {
            List<Integer> row = infra.get(i);
            connections[i] += row.size();
            for(int j = 0; j < row.size(); ++j) {
                ++connections[row.get(j)];
            }
        }

        return connections;
    }

    private void printBid(G3Bid b) {
    	int t_1 = b.town_id1;
        int t_2 = b.town_id2;
        if (b.town_id3 > -1) {
            int t_3 = b.town_id3;
            System.err.println(b.id1 + "," + b.id2 + ": " + townLookup.get(t_1) + " to " + townLookup.get(t_2) + " to " + townLookup.get(t_3) + " for " + b.amount);
        } else {
            System.err.println(b.id1 + "," + b.id2 + ": " + townLookup.get(t_1) + " to " + townLookup.get(t_2) + " for " + b.amount);
        }
    }

    // Indicates to the player whether they have won the previous bid of link/pair of links.
    // A null bid indicates that they did not win their bid.
    public void updateBudget(Bid bid) {
        if (bid != null) {
            budget -= bid.amount;

            System.out.println("We won.");

            System.out.print("ID1: ");
            System.out.println(bid.id1);

            System.out.print("ID2: ");
            System.out.println(bid.id2);

            // remove all owned links from possible bids
            List<G3Bid> copy = new ArrayList<G3Bid>(availableBids.size());
            for (G3Bid b: availableBids) {
	    		if(!sameLink(b, this.best) && !sameTowns(b, this.best)) {
	    			copy.add(b);
	    		}
                // This bid is what we just won
                else {
                    // Current bid is a single link and the one we just won is single
                    if (b.id2 == -1 && bid.id2 == -1) {
                        ourConnections[b.town_id1] +=1;
                        ourConnections[b.town_id2] +=1;
                    }
                    // Current bid is a double link and what we just won is double
                    else if (b.id2 == bid.id2 && b.id1==bid.id1) {
                        ourConnections[b.town_id1] += 1;
                        ourConnections[b.town_id2] += 2;
                        ourConnections[b.town_id3] +=1;
                        
                    }
                    
                }
	    	}

            for (int i = 0; i < ourConnections.length; i++) {
                System.out.println(ourConnections[i]);
            }

            System.out.println(ourConnections);
	    	availableBids = copy;

        } else {
        	// must wait to see another player's owned bid and update the possible bids
        	needsUpdate = true;
        }

        // reset all the bids at the end of the round
        for(int i = 0; i < availableBids.size(); ++i) {
        	G3Bid cur = availableBids.get(i);
        	cur.amount = cur.min_bid;
        }

        bidsThisRound = 0;
    }

    private boolean sameLink(Bid bid, Bid owned) {
    	int b1l1, b1l2, b2l1, b2l2;
    	b1l1 = bid.id1;
    	b1l2 = bid.id2;
    	b2l1 = owned.id1;
    	b2l2 = owned.id2;

    	if(b1l1==b2l1 || b1l1==b2l2 || b1l2==b2l1) {
    		return true;
    	}

    	if(b1l2==-1 || b2l2==-1) {
    		return false;
    	}

    	return (b1l2==b2l2);
    }

    private boolean sameTowns(G3Bid bid, G3Bid owned) {
    	int b1t1, b1t2, b1t3, b2t1, b2t2, b2t3;
    	b1t1 = bid.town_id1;
    	b1t2 = bid.town_id2;
    	b1t3 = bid.town_id3;
    	b2t1 = owned.town_id1;
    	b2t2 = owned.town_id2;
    	b2t3 = owned.town_id3;

    	if(b1t1==b2t1 && b1t2==b2t2 || b1t2==b2t1 && b1t1==b2t2) {
    		return true;
    	}

    	if(b1t1==b2t2 && b1t2==b2t3 || b1t1==b2t3 && b1t2==b2t2 ||
    	   b1t2==b2t1 && b1t3==b2t2 || b1t3==b2t1 && b1t2==b2t2) {
    		return true;
    	}

    	if(b1t3==-1 || b2t3==-1) {
    		return false;
    	}

    	return (b1t2==b2t2 && b1t3==b2t3 || b1t3==b2t2 && b1t2==b2t3);
    }
}
