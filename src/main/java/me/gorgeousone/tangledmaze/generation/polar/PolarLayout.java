package me.gorgeousone.tangledmaze.generation.polar;

import java.util.ArrayList;
import java.util.List;

/**
 * Geometric layout of a polar maze: concentric rings subdivided into angular sectors.
 *
 * Ring 0 is a single center disc covering r in [0, pathWidth). Subsequent ring N covers
 * r in [N*stride, N*stride + pathWidth) with a wall band of width wallWidth in between.
 * Sector count per ring starts from a heuristic at ring 1 and doubles when the arc per
 * sector would otherwise exceed twice the stride (classic theta-maze split rule).
 *
 * The neighbour graph used by the path generator is exposed via {@link #neighboursOf}.
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
		//pick a sector count for ring 1 such that a cell's arc length is roughly one stride
		int s1 = (int) Math.max(4, Math.round(TWO_PI * ring1Mid / stride));
		if (s1 % 2 != 0) {
			s1++;
		}
		sectors.add(s1);

		for (int n = 2; n < ringCount; n++) {
			double mid = n * stride + pathWidth / 2.0;
			int prev = sectors.get(n - 1);
			double arcPerSector = TWO_PI * mid / prev;

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

	/**
	 * Outer radius of the outermost ring's path band. Exit corridors live beyond this.
	 */
	public double outerRadius() {
		int last = ringCount() - 1;
		return last == 0 ? pathWidth : last * stride + pathWidth;
	}

	/**
	 * Returns the cell's inward neighbour, or null for ring 0.
	 */
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

	/**
	 * Returns the full neighbour set for a cell (inward, outward 0-2, CW, CCW).
	 */
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

	/**
	 * Resolves the outer-ring cell at the angle of the given world block center.
	 */
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

	public double angleOf(double blockCenterX, double blockCenterZ) {
		double dx = blockCenterX - centerX;
		double dz = blockCenterZ - centerZ;
		double theta = Math.atan2(dz, dx);
		if (theta < 0) {
			theta += TWO_PI;
		}
		return theta;
	}
}
