package me.gorgeousone.tangledmaze.generation.polar;

import me.gorgeousone.tangledmaze.generation.AreaType;
import me.gorgeousone.tangledmaze.generation.GridMap;
import me.gorgeousone.tangledmaze.generation.MazeMap;
import me.gorgeousone.tangledmaze.generation.paving.PathType;
import me.gorgeousone.tangledmaze.maze.MazeProperty;
import me.gorgeousone.tangledmaze.maze.MazeSettings;
import me.gorgeousone.tangledmaze.util.Vec2;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds a polar (theta) maze into a MazeMap. The polar layout is derived from the clip's
 * bounding box (center = bbox center, max radius = inscribed circle of the bbox). A spanning
 * tree over the ring/sector graph guarantees a unique solution path, and pathWidth/wallWidth
 * are honoured radially (ring/wall band thickness) and tangentially (sector boundary / ring
 * opening widths).
 *
 * After this pass the MazeMap's AreaType grid has PATH/FREE set for the polar structure, and
 * a 1-block-per-cell flat GridMap is attached so the existing wall/floor/roof block
 * generators work unchanged.
 */
public class PolarMazeFactory {

	private static final double TWO_PI = 2.0 * Math.PI;

	public static void createPaths(MazeMap mazeMap, List<Vec2> exits,
	                               MazeSettings settings, int worldMinY) {
		int pathWidth = settings.getValue(MazeProperty.PATH_WIDTH);
		int wallWidth = settings.getValue(MazeProperty.WALL_WIDTH);
		int wallHeight = settings.getValue(MazeProperty.WALL_HEIGHT);
		int seed = settings.getValue(MazeProperty.SEED);

		Vec2 min = mazeMap.getMin();
		Vec2 max = mazeMap.getMax();
		double centerX = (min.getX() + max.getX()) / 2.0;
		double centerZ = (min.getZ() + max.getZ()) / 2.0;
		double halfW = (max.getX() - min.getX()) / 2.0;
		double halfH = (max.getZ() - min.getZ()) / 2.0;
		double maxRadius = Math.min(halfW, halfH);

		PolarLayout layout = new PolarLayout(centerX, centerZ, maxRadius, pathWidth, wallWidth);

		List<PolarCell> exitCells = new ArrayList<>();
		List<Double> exitAngles = new ArrayList<>();

		//snap each exit to the midangle of the outer-ring sector the click falls in so the
		//corridor aligns with the cell interior (robust when wallWidth >= pathWidth)
		for (Vec2 exit : exits) {
			double bcx = exit.getX() + 0.5;
			double bcz = exit.getZ() + 0.5;
			PolarCell cell = layout.findOuterCellAt(bcx, bcz);
			cell.setExit(true);
			if (!exitCells.contains(cell)) {
				exitCells.add(cell);
			}
			int s = layout.sectorsIn(cell.getRing());
			double midAngle = (cell.getSector() + 0.5) * (TWO_PI / s);
			exitAngles.add(midAngle);
		}

		PolarPathGen.generate(layout, exitCells, seed);
		rasterize(mazeMap, layout);
		carveExitCorridors(mazeMap, layout, exitAngles);

		GridMap flatGrid = new GridMap(min, max, pathWidth, wallWidth);
		flatGrid.initFlat();
		populateFlatGrid(flatGrid, mazeMap, wallHeight, worldMinY);
		mazeMap.setGridMap(flatGrid);
	}

	/**
	 * Walks every FREE block in the map and decides whether it belongs to a polar path
	 * (interior of a ring cell, sector boundary with an open neighbour, or radial wall
	 * opening). Exit corridors are carved separately in {@link #carveExitCorridors}.
	 */
	private static void rasterize(MazeMap mazeMap, PolarLayout layout) {
		Vec2 min = mazeMap.getMin();
		Vec2 max = mazeMap.getMax();
		double cx = layout.centerX();
		double cz = layout.centerZ();
		double outerR = layout.outerRadius();
		int pw = layout.pathWidth();
		int ww = layout.wallWidth();
		int stride = layout.stride();

		for (int x = min.getX(); x < max.getX(); x++) {
			for (int z = min.getZ(); z < max.getZ(); z++) {
				if (mazeMap.getType(x, z) != AreaType.FREE) {
					continue;
				}
				double dx = x + 0.5 - cx;
				double dz = z + 0.5 - cz;
				double r = Math.sqrt(dx * dx + dz * dz);
				double theta = Math.atan2(dz, dx);
				if (theta < 0) {
					theta += TWO_PI;
				}
				if (classify(layout, r, theta, pw, ww, stride, outerR) == AreaType.PATH) {
					mazeMap.setType(x, z, AreaType.PATH);
				}
			}
		}
	}

	private static AreaType classify(PolarLayout layout, double r, double theta,
	                                 int pw, int ww, int stride, double outerR) {
		//outside the outermost ring - will be paved only by the exit corridor pass
		if (r >= outerR) {
			return AreaType.FREE;
		}
		//ring 0 (center disc)
		if (r < pw) {
			return AreaType.PATH;
		}
		double t = r - pw;
		int bandIdx = (int) Math.floor(t / stride);
		double bandPos = t - bandIdx * stride;

		//wall band W_bandIdx separating ring bandIdx from ring bandIdx+1
		if (bandPos < ww) {
			int outerRing = bandIdx + 1;
			if (outerRing >= layout.ringCount()) {
				return AreaType.FREE;
			}
			int s = layout.sectorsIn(outerRing);
			int k = clampSector((int) Math.floor(theta * s / TWO_PI), s);
			PolarCell outerCell = layout.cell(outerRing, k);
			PolarCell inner = layout.inwardOf(outerCell);

			if (inner == null || !outerCell.isOpenTo(inner)) {
				return AreaType.FREE;
			}
			double midTheta = (k + 0.5) * (TWO_PI / s);
			double angDiff = Math.abs(theta - midTheta);
			if (angDiff > Math.PI) {
				angDiff = TWO_PI - angDiff;
			}
			double pathHalfArc = pw / (2.0 * r);
			return angDiff < pathHalfArc ? AreaType.PATH : AreaType.FREE;
		}
		//inside the path band of ring N
		int n = bandIdx + 1;
		int s = layout.sectorsIn(n);
		int k = clampSector((int) Math.floor(theta * s / TWO_PI), s);
		PolarCell cell = layout.cell(n, k);
		double sectorArc = TWO_PI / s;
		double angFromCCW = theta - k * sectorArc;
		double angFromCW = (k + 1) * sectorArc - theta;
		double wallHalfArc = ww / (2.0 * r);

		if (s > 1 && angFromCCW < wallHalfArc) {
			PolarCell ccw = layout.cell(n, (k - 1 + s) % s);
			return cell.isOpenTo(ccw) ? AreaType.PATH : AreaType.FREE;
		}
		if (s > 1 && angFromCW < wallHalfArc) {
			PolarCell cw = layout.cell(n, (k + 1) % s);
			return cell.isOpenTo(cw) ? AreaType.PATH : AreaType.FREE;
		}
		return AreaType.PATH;
	}

	private static int clampSector(int k, int s) {
		if (k >= s) {
			return s - 1;
		}
		if (k < 0) {
			return 0;
		}
		return k;
	}

	/**
	 * Carves a pathWidth-wide radial corridor from the outermost ring to beyond the clip
	 * border for each exit. The corridor is centred on the exit cell's midangle so it
	 * always aligns with the cell interior, regardless of where inside the cell the user
	 * clicked.
	 */
	private static void carveExitCorridors(MazeMap mazeMap, PolarLayout layout, List<Double> exitAngles) {
		double cx = layout.centerX();
		double cz = layout.centerZ();
		double outerR = layout.outerRadius();
		double maxR = layout.maxRadius();
		int pw = layout.pathWidth();
		int half = pw / 2;
		//2 blocks of slack so the corridor reliably punches through the clip border
		double stopR = maxR + Math.max(2.0, pw);

		for (double midAngle : exitAngles) {
			double dirX = Math.cos(midAngle);
			double dirZ = Math.sin(midAngle);
			double tx = -dirZ;
			double tz = dirX;
			//radially step out, carving pathWidth blocks tangentially at every step
			for (double r = outerR; r <= stopR; r += 0.5) {
				double px = cx + r * dirX;
				double pz = cz + r * dirZ;
				for (int i = -half; i <= pw - 1 - half; i++) {
					int bx = (int) Math.floor(px + i * tx);
					int bz = (int) Math.floor(pz + i * tz);
					//skip blocks outside the clip entirely (null type) so irregular clip
					//shapes keep their shape beyond the exit border
					if (mazeMap.contains(bx, bz) && mazeMap.getType(bx, bz) != null) {
						mazeMap.setType(bx, bz, AreaType.PATH);
					}
				}
			}
		}
	}

	private static void populateFlatGrid(GridMap flatGrid, MazeMap mazeMap, int wallHeight, int worldMinY) {
		int w = flatGrid.getWidth();
		int h = flatGrid.getHeight();
		Vec2 min = mazeMap.getMin();

		for (int x = 0; x < w; x++) {
			for (int z = 0; z < h; z++) {
				int worldX = min.getX() + x;
				int worldZ = min.getZ() + z;
				int floorY = mazeMap.contains(worldX, worldZ) ? mazeMap.getY(worldX, worldZ) : worldMinY;
				flatGrid.setFloorY(x, z, floorY);
				if (mazeMap.getType(worldX, worldZ) == AreaType.PATH) {
					flatGrid.setPathType(x, z, PathType.PAVED);
				}
			}
		}
		//wall Y = max(self floor + wallHeight, 4-neighbour floor + 2), matching grid behaviour
		for (int x = 0; x < w; x++) {
			for (int z = 0; z < h; z++) {
				int selfFloor = flatGrid.getFloorY(new Vec2(x, z));
				int wallY = selfFloor + wallHeight;
				for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
					int nx = x + d[0];
					int nz = z + d[1];
					if (flatGrid.contains(nx, nz)) {
						wallY = Math.max(wallY, flatGrid.getFloorY(new Vec2(nx, nz)) + 2);
					}
				}
				flatGrid.setWallY(x, z, wallY);
			}
		}
	}
}
