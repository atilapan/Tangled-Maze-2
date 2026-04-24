package me.gorgeousone.tangledmaze.generation.polar;

import java.util.HashSet;
import java.util.Set;

/**
 * A single cell of the polar maze topology. Each cell is identified by its ring index and
 * sector index within that ring. Open edges in the spanning tree are stored as references
 * to neighbour cells in {@link #openNeighbours}.
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

	/**
	 * Opens an edge in the spanning tree between this cell and the given neighbour.
	 * The edge is symmetric - both cells are updated.
	 */
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
