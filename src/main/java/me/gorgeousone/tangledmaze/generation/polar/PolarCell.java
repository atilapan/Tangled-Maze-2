package me.gorgeousone.tangledmaze.generation.polar;

import java.util.HashSet;
import java.util.Set;

/**
 * One cell of the polar maze topology, identified by its ring index and its sector
 * index within that ring. Spanning-tree edges are stored as references to the open
 * neighbour cells.
 */
public class PolarCell {

	private final int ring;
	private final int sector;
	private final Set<PolarCell> openNeighbours = new HashSet<>();
	private boolean isExit;

	public PolarCell(int ring, int sector) {
		this.ring = ring;
		this.sector = sector;
	}

	public int getRing() {
		return ring;
	}

	public int getSector() {
		return sector;
	}

	public boolean isExit() {
		return isExit;
	}

	public void setExit(boolean exit) {
		this.isExit = exit;
	}

	/** Symmetric — both endpoints record the open edge. */
	public void openTo(PolarCell other) {
		openNeighbours.add(other);
		other.openNeighbours.add(this);
	}

	public boolean isOpenTo(PolarCell other) {
		return openNeighbours.contains(other);
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof PolarCell)) {
			return false;
		}
		PolarCell p = (PolarCell) o;
		return ring == p.ring && sector == p.sector;
	}

	@Override
	public int hashCode() {
		return 31 * ring + sector;
	}

	@Override
	public String toString() {
		return "PolarCell{r=" + ring + ",s=" + sector + '}';
	}
}
