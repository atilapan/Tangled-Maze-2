package me.gorgeousone.tangledmaze.generation.polar;

import me.gorgeousone.tangledmaze.generation.AreaType;
import me.gorgeousone.tangledmaze.generation.GridMap;
import me.gorgeousone.tangledmaze.generation.MazeMap;
import me.gorgeousone.tangledmaze.generation.paving.ExitSegment;
import me.gorgeousone.tangledmaze.generation.paving.PathType;
import me.gorgeousone.tangledmaze.maze.MazeProperty;
import me.gorgeousone.tangledmaze.maze.MazeSettings;
import me.gorgeousone.tangledmaze.util.Direction;
import me.gorgeousone.tangledmaze.util.Vec2;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds a polar (theta) maze into a MazeMap. The polar layout is derived from the clip's
 * bounding box (centre = bbox centre, max radius = inscribed-circle radius). A spanning
 * tree over the variable-sectors-per-ring graph guarantees a unique solution between any
 * two exits, and pathWidth/wallWidth are honoured uniformly:
 *
 * - Radially: each ring's path band is pathWidth blocks thick, walls between rings are
 *   wallWidth thick.
 * - Tangentially: spanning-tree openings between cells are pathWidth blocks of arc, sector
 *   boundaries within a ring are wallWidth blocks of arc — at every ring, including inner
 *   ones, because sectors-per-ring grows outward to keep the per-cell arc length bounded.
 *
 * Exit corridors snap to the outer cell's midangle (so they always land on the cell
 * interior even when the user clicks near a sector boundary) and sweep radially toward the
 * clicked outline, supporting both outer- and inner-outline exits.
 *
 * Downstream block generation is unchanged: this factory writes PATH/FREE on the MazeMap
 * directly and attaches a 1-block-per-cell flat GridMap so the existing wall/floor/roof
 * generators iterate it as-is.
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

		for (Vec2 exit : exits) {
			PolarCell cell = layout.findClosestCellAt(exit.getX() + 0.5, exit.getZ() + 0.5);
			cell.setExit(true);
			if (!exitCells.contains(cell)) {
				exitCells.add(cell);
			}
		}
		PolarPathGen.generate(layout, exitCells, seed);
		rasterize(mazeMap, layout);

		for (Vec2 exit : exits) {
			carveExitCorridor(mazeMap, layout, exit);
		}
		GridMap flatGrid = new GridMap(min, max, pathWidth, wallWidth);
		flatGrid.initFlat();
		populateFlatGrid(flatGrid, mazeMap, wallHeight, worldMinY);
		registerExits(flatGrid, exits);
		mazeMap.setGridMap(flatGrid);
	}

	/**
	 * Walks every FREE block in the clip and decides PATH vs FREE from its polar position
	 * + the spanning tree. Exit corridors are carved separately afterwards because they sit
	 * past the outermost ring.
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
		//past the outermost ring — only the exit-corridor pass paves blocks here
		if (r >= outerR) {
			return AreaType.FREE;
		}
		//ring 0 is the centre disc, always interior path
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
			double midTheta = layout.midAngleOf(outerRing, k);
			double angDiff = Math.abs(theta - midTheta);
			if (angDiff > Math.PI) {
				angDiff = TWO_PI - angDiff;
			}
			double pathHalfArc = pw / (2.0 * r);
			return angDiff < pathHalfArc ? AreaType.PATH : AreaType.FREE;
		}
		//path band of ring N
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
	 * Carves a pathWidth-wide radial corridor that snaps to the click cell's midangle.
	 * Sweep range is bounded by the cell's near edge so the corridor never tunnels past the
	 * cell into the rest of the maze:
	 *
	 * - Inner-outline click (clickR <= cell inner edge): sweep [clickR-pw, cellInnerEdge].
	 * - Outer-outline click (clickR >= cell outer edge): sweep [cellOuterEdge, clickR+pw].
	 * - Click inside the cell's path band: sweep clickR ± pw/2 clamped to [cellInnerEdge,
	 *   cellOuterEdge - 0.5] so the carve stays within the cell and doesn't bleed into the
	 *   wall band on the far side.
	 *
	 * 0.5-block radial steps keep adjacent slabs overlapping at any angle.
	 */
	private static void carveExitCorridor(MazeMap mazeMap, PolarLayout layout, Vec2 clickBlock) {
		double cx = layout.centerX();
		double cz = layout.centerZ();
		int pw = layout.pathWidth();
		int stride = layout.stride();

		PolarCell cell = layout.findClosestCellAt(clickBlock.getX() + 0.5, clickBlock.getZ() + 0.5);
		double midAngle = layout.midAngleOf(cell.getRing(), cell.getSector());
		double cellInnerEdge = cell.getRing() == 0 ? 0 : cell.getRing() * stride;
		double cellOuterEdge = cellInnerEdge + pw;

		double dirX = Math.cos(midAngle);
		double dirZ = Math.sin(midAngle);
		double tx = -dirZ;
		double tz = dirX;

		double bcx = clickBlock.getX() + 0.5 - cx;
		double bcz = clickBlock.getZ() + 0.5 - cz;
		double clickR = Math.sqrt(bcx * bcx + bcz * bcz);

		double startR;
		double stopR;

		if (clickR <= cellInnerEdge) {
			//inner-outline exit: sweep from past the click into the hole/inner area up to
			//the cell's inner edge — never crosses into the cell's far side
			startR = Math.max(0d, clickR - pw);
			stopR = cellInnerEdge;
		} else if (clickR >= cellOuterEdge) {
			//outer-outline exit: sweep from the cell's outer edge to past the click
			startR = cellOuterEdge;
			stopR = clickR + pw;
		} else {
			//click sits inside the cell's path band (e.g. on an outline that intersects
			//the path band radially). Sweep symmetrically around the click but clamp to
			//the cell so the corridor doesn't bleed past either wall band.
			startR = Math.max(cellInnerEdge, clickR - pw / 2.0);
			stopR = Math.min(cellOuterEdge - 0.5, clickR + pw / 2.0);
		}
		int half = pw / 2;

		for (double r = startR; r <= stopR; r += 0.5) {
			double px = cx + r * dirX;
			double pz = cz + r * dirZ;

			for (int i = -half; i < pw - half; i++) {
				int bx = (int) Math.floor(px + i * tx);
				int bz = (int) Math.floor(pz + i * tz);
				//skip blocks outside the clip — irregular clip shapes keep their shape
				if (mazeMap.contains(bx, bz) && mazeMap.getType(bx, bz) != null) {
					mazeMap.setType(bx, bz, AreaType.PATH);
				}
			}
		}
	}

	/**
	 * Populates a 1-block-per-cell flat grid so WallBlockGen / FloorGen / RoofBlockGen run
	 * unchanged. Each cell is PAVED iff its block is PATH on the maze map.
	 */
	private static void populateFlatGrid(GridMap flatGrid, MazeMap mazeMap, int wallHeight, int worldMinY) {
		int w = flatGrid.getWidth();
		int h = flatGrid.getHeight();
		Vec2 min = mazeMap.getMin();

		for (int x = 0; x < w; x++) {
			for (int z = 0; z < h; z++) {
				int worldX = min.getX() + x;
				int worldZ = min.getZ() + z;
				int floorY = mazeMap.contains(worldX, worldZ) && mazeMap.getType(worldX, worldZ) != null
						? mazeMap.getY(worldX, worldZ)
						: worldMinY;
				flatGrid.setFloorY(x, z, floorY);

				if (mazeMap.contains(worldX, worldZ) && mazeMap.getType(worldX, worldZ) == AreaType.PATH) {
					flatGrid.setPathType(x, z, PathType.PAVED);
				}
			}
		}
		//wallY = max(self floor + wallHeight, 4-neighbour floor + 2) — same rule the
		//Cartesian grid uses, so wall heights tie into terrain consistently
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

	/**
	 * Registers one ExitSegment per user click pointing at the click block itself. The click
	 * block sits inside the carved exit corridor, so it's PAVED on the flat grid and the
	 * MazeSolver lands on a valid endpoint when looking up each exit.
	 */
	private static void registerExits(GridMap flatGrid, List<Vec2> exits) {
		for (Vec2 exit : exits) {
			flatGrid.addExit(new ExitSegment(exit.clone(), Direction.EAST, 1));
		}
	}
}
