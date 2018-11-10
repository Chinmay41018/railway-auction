package railway.g8;

import railway.sim.utils.Coordinates;

public class SwitchRoute {
    public Coordinates startSeg; //starting segment of route
    public Coordinates endSeg; //ending segment of route
    public Integer switchCount; //switches count between different companies

    public SwitchRoute(){
        startSeg = null;
        endSeg = null;
        switchCount = null;
    }

    public SwitchRoute(Coordinates startSeg, Coordinates endSeg){
        this.startSeg = startSeg;
        this.endSeg = endSeg;
        this.switchCount = 0;
    }

    public SwitchRoute(Coordinates startSeg, Coordinates endSeg, int switchCount){
        this.startSeg = startSeg;
        this.endSeg = endSeg;
        this.switchCount = switchCount;
    }
}
