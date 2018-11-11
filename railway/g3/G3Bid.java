package railway.g3;

// To access data classes.
import railway.sim.utils.*;

import java.util.Arrays;

public class G3Bid extends Bid implements Comparable<G3Bid> {
	// id of first town
	public int town_id1;

	// id of second town
	public int town_id2;

	// id of third town -- if applicable
	public int town_id3;

	// minimum bid that could ever win the link
	public double min_bid;

	// expected amount of profit from winning the bid
	public double score;

	public G3Bid() {
		super();
		town_id1 = -1;
		town_id2 = -1;
		town_id3 = -1;
	}

	// single link bid
	public G3Bid(int town_id1, int town_id2, int link, double amount) {
		this.town_id1 = town_id1;
		this.town_id2 = town_id2;
		this.town_id3 = -1;
		this.id1 = link;
		this.id2 = -1;
		this.min_bid = this.amount = amount;
		//this.score = -Double.MAX_VALUE;
		this.score = 0;
	}

	// double link bid
	public G3Bid(G3Bid b1, G3Bid b2) throws Exception {
		int town_id1, town_id2, town_id3;
		if (b1.town_id1==b2.town_id1) {
			town_id1 = b1.town_id2;
			town_id2 = b1.town_id1;
			town_id3 = b2.town_id2;
		} else if(b1.town_id1==b2.town_id2) {
			town_id1 = b1.town_id2;
			town_id2 = b1.town_id1;
			town_id3 = b2.town_id1;
		} else if(b1.town_id2==b2.town_id1) {
			town_id1 = b1.town_id1;
			town_id2 = b1.town_id2;
			town_id3 = b2.town_id2;
		} else if(b1.town_id2==b2.town_id2) {
			town_id1 = b1.town_id1;
			town_id2 = b1.town_id2;
			town_id3 = b2.town_id1;
		} else {
			throw new Exception("must pass valid pair of bids");
		}

		this.town_id1 = town_id1;
		this.town_id2 = town_id2;
		this.town_id3 = town_id3;
		this.id1 = b1.id1;
		this.id2 = b2.id1;
		this.min_bid = this.amount = b1.amount+b2.amount;
		//this.score = -Double.MAX_VALUE;
		this.score = 0;
	}

	// copy constructor
	public G3Bid(G3Bid toCopy) {
		this.town_id1 = toCopy.town_id1;
		this.town_id2 = toCopy.town_id2;
		this.town_id3 = toCopy.town_id3;
		this.id1 = toCopy.id1;
		this.id2 = toCopy.id2;
		this.min_bid = this.amount = toCopy.amount;
		this.score = toCopy.score;
	}

	@Override
	public int compareTo(G3Bid other) {
		return (int)Math.signum(other.score - this.score);
	}
}