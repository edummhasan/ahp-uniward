package thesis.ahp.alg;

public final class Candidate implements Comparable<Candidate> {
    public final int component; // 1=Cb, 2=Cr
    public final int blockIndex;
    public final int blockX;
    public final int blockY;
    public final int u;
    public final int v;
    public final int k;
    public final int qRef;
    public final double rhoPlus;
    public final double rhoMinus;
    public final double cost; // minimum feasible cost to flip coefficient parity

    public Candidate(int component, int blockIndex, int blockX, int blockY,
                     int u, int v, int k, int qRef,
                     double rhoPlus, double rhoMinus) {
        this.component = component;
        this.blockIndex = blockIndex;
        this.blockX = blockX;
        this.blockY = blockY;
        this.u = u;
        this.v = v;
        this.k = k;
        this.qRef = qRef;
        this.rhoPlus = sanitize(rhoPlus);
        this.rhoMinus = sanitize(rhoMinus);
        this.cost = Math.min(this.rhoPlus, this.rhoMinus);
    }

    public int bestParityFlipValue() {
        if (qRef == 0) return 1;
        boolean canMinus = (qRef - 1) != 0;
        boolean canPlus = (qRef + 1) != 0;
        if (canMinus && canPlus) return rhoMinus <= rhoPlus ? qRef - 1 : qRef + 1;
        if (canMinus) return qRef - 1;
        return qRef + 1;
    }

    private static double sanitize(double v) {
        if (!Double.isFinite(v) || v <= 0) return 1e13;
        return Math.min(v, 1e13);
    }

    @Override
    public int compareTo(Candidate o) {
        return Double.compare(this.cost, o.cost);
    }
}
