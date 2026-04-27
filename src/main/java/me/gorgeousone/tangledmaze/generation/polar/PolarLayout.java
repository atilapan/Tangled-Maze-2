package me.gorgeousone.tangledmaze.generation.polar;

import java.util.ArrayList;
import java.util.List;

/**
 * Concentric rings subdivided into angular sectors. Ring 0 is a single centre disc
 * covering r in [0, pathWidth). Ring N (N >= 1) covers r in [N*stride, N*stride + pathWidth)
 * with a wallWidth-thick wall band before it. Sector counts grow outward following the
 * classic theta-maze doubling rule so each cell's arc length stays roughly one stride —
 * which keeps corridor openings honest at pathWidth blocks regardless of which ring they
 * sit on.
 */
public class PolarLayout {

	private static final double TWO_PI = 2.0 * Math.PI;

	private final double centerX;
	private final double centerZ;
	private final double maxRadius;
	private final int pathWidth;
	private final int wallWidth;
	private final int stride;

	private final List<Integer> sectorsPerRing;
	private final List<List<PolarCell>> cellsByRing;

	public PolarLayout(double centerX, double centerZ, double maxRadius,
	                   int pathWidth, int wallWidth) {
		this.centerX = centerX;
		this.centerZ = centerZ;
		this.maxRadius = maxRadius;
		this.pathWidth = pathWidth;
		this.wallWidth = wallWidth;
		this.stride = pathWidth + wallWidth;

		this.sectorsPerRing = computeSectorsPerRing();
		this.cellsByRing = buildCells();
	}

	private int computeRingCount() {
		if (maxRadius < pathWidth) {
			return 1;
		}
		int count = 1;
		while (count * stride + pathWidth <= maxRadius) {
			count++;
		}
		return count;
	}

	private List<Integer> computeSectorsPerRing() {
		List<Integer> sectors = new ArrayList<>();
		int ringCount = computeRingCount();
		sectors.add(1);

		if (ringCount <= 1) {
			return sectors;
		}
		double ring1Mid = stride + pathWidth / 2.0;
		//ring 1 sector count: pick so each cell's arc is roughly one stride at the midradius
		int s1 = (int) Math.max(4, Math.round(TWO_PI * ring1Mid / stride));
		if (s1 % 2 != 0) {
			s1++;
		}
		sectors.add(s1);

		for (int n = 2; n < ringCount; n++) {
			double mid = n * stride + pathWidth / 2.0;
			int prev = sectors.get(n - 1);
			double arcPerSector = TWO_PI * mid / prev;

			//double when keeping the previous sector count would push arc past 2 strides
			if (arcPerSector > 2.0 * stride) {
				sectors.add(prev * 2);
			} else {
				sectors.add(prev);
			}
		}
		return sectors;
	}

	private List<List<PolarCell>> buildCells() {
		List<List<PolarCell>> rings = new ArrayList<>();
		for (int n = 0; n < sectorsPerRing.size(); n++) {
			int s = sectorsPerRing.get(n);
			List<PolarCell> ring = new ArrayList<>(s);
			for (int k = 0; k < s; k++) {
				ring.add(new PolarCell(n, k));
			}
			rings.add(ring);
		}
		return rings;
	}

	public int ringCount() {
		return sectorsPerRing.size();
	}

	public int sectorsIn(int ring) {
		return sectorsPerRing.get(ring);
	}

	public PolarCell cell(int ring, int sector) {
		return cellsByRing.get(ring).get(sector);
	}

	public List<List<PolarCell>> allCells() {
		return cellsByRing;
	}

	public double centerX() {
		return centerX;
	}

	public double centerZ() {
		return centerZ;
	}

	public double maxRadius() {
		return maxRadius;
	}

	public int pathWidth() {
		return pathWidth;
	}

	public int wallWidth() {
		return wallWidth;
	}

	public int stride() {
		return stride;
	}

	/** Outer radius of the outermost ring's path band. Exit corridors sit beyond this. */
	public double outerRadius() {
		int last = ringCount() - 1;
		return last == 0 ? pathWidth : last * stride + pathWidth;
	}

	/** Inward neighbour for the spanning-tree graph, or null for ring 0. */
	public PolarCell inwardOf(PolarCell c) {
		int n = c.getRing();
		int k = c.getSector();
		if (n == 0) {
			return null;
		}
		if (n == 1) {
			return cell(0, 0);
		}
		int prevS = sectorsPerRing.get(n - 1);
		int ratio = sectorsPerRing.get(n) / prevS;
		return cell(n - 1, k / ratio);
	}

	/** Inward neighbour, outward children (1 or 2 with doubling), CW and CCW siblings. */
	public List<PolarCell> neighboursOf(PolarCell c) {
		List<PolarCell> result = new ArrayList<>();
		int n = c.getRing();
		int k = c.getSector();
		int s = sectorsPerRing.get(n);

		PolarCell inward = inwardOf(c);
		if (inward != null) {
			result.add(inward);
		}
		if (n + 1 < ringCount()) {
			int nextS = sectorsPerRing.get(n + 1);
			int ratio = nextS / s;
			int baseK = k * ratio;
			for (int i = 0; i < ratio; i++) {
				result.add(cell(n + 1, baseK + i));
			}
		}
		if (s > 1) {
			result.add(cell(n, (k + 1) % s));
			result.add(cell(n, (k - 1 + s) % s));
		}
		return result;
	}

	/** Outer-ring cell at the angle of the given world block centre. */
	public PolarCell findOuterCellAt(double blockCenterX, double blockCenterZ) {
		double theta = angleOf(blockCenterX, blockCenterZ);
		int n = ringCount() - 1;
		int s = sectorsPerRing.get(n);
		int k = (int) Math.floor(theta * s / TWO_PI);
		if (k >= s) {
			k = s - 1;
		}
		if (k < 0) {
			k = 0;
		}
		return cell(n, k);
	}

	/**
	 * Cell whose path band is closest to the given block — the cell actually adjacent to
	 * the clicked outline. Used for exit selection so an inner-outline click resolves to
	 * the inner-most ring, not the outermost one.
	 */
	public PolarCell findClosestCellAt(double blockCenterX, double blockCenterZ) {
		double dx = blockCenterX - centerX;
		double dz = blockCenterZ - centerZ;
		double r = Math.sqrt(dx * dx + dz * dz);
		double theta = angleOf(blockCenterX, blockCenterZ);

		//ring 0 is the centre disc — only consider it if the maze has no other rings
		int startRing = ringCount() > 1 ? 1 : 0;
		int bestRing = startRing;
		double bestDist = Double.MAX_VALUE;

		for (int n = startRing; n < ringCount(); n++) {
			double innerEdge = n == 0 ? 0 : n * stride;
			double outerEdge = innerEdge + pathWidth;
			double dist;

			if (r < innerEdge) {
				dist = innerEdge - r;
			} else if (r > outerEdge) {
				dist = r - outerEdge;
			} else {
				dist = 0;
			}
			if (dist < bestDist) {
				bestDist = dist;
				bestRing = n;
			}
		}
		int s = sectorsPerRing.get(bestRing);
		int k = (int) Math.floor(theta * s / TWO_PI);
		if (k >= s) {
			k = s - 1;
		}
		if (k < 0) {
			k = 0;
		}
		return cell(bestRing, k);
	}

	public double angleOf(double blockCenterX, double blockCenterZ) {
		double dx = blockCenterX - centerX;
		double dz = blockCenterZ - centerZ;
		double theta = Math.atan2(dz, dx);
		if (theta < 0) {
			theta += TWO_PI;
		}
		return theta;
	}

	/** Mid-angle of a sector at this ring, for rasterisation/carving alignment. */
	public double midAngleOf(int ring, int sector) {
		int s = sectorsPerRing.get(ring);
		return (sector + 0.5) * (TWO_PI / s);
	}
}
