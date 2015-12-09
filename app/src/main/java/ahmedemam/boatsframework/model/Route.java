package ahmedemam.boatsframework.model;

/**
 * Created by aemam on 12/8/15.
 */
public class Route implements Comparable{
    private int nextHop;
    private int cost;

    /**
     *
     * @param nextHop   The nextHop to send through to reach the destination
     * @param cost      cost of getting to that destination
     */
    public Route(int nextHop, int cost){
        this.nextHop = nextHop;
        this.cost = cost;
    }

    public int getCost() {
        return cost;
    }
    public int getNextHop() {
        return nextHop;
    }

    @Override
    public int compareTo(Object o) {
        Route otherRoute = (Route) o;
        return ((Integer)this.cost).compareTo(otherRoute.getCost());
    }

    @Override
    public String toString() {
        return this.nextHop+" "+this.cost;
    }
}
