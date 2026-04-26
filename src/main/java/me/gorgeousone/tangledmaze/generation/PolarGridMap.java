package me.gorgeousone.tangledmaze.generation;

import me.gorgeousone.tangledmaze.generation.paving.ExitSegment;
import me.gorgeousone.tangledmaze.generation.paving.PathType;
import me.gorgeousone.tangledmaze.util.Direction;
import me.gorgeousone.tangledmaze.util.Vec2;

import java.util.HashSet;
import java.util.Set;

/**
 * A grid map whose logical axes are radius and angle instead of x and z.
 */
public class PolarGridMap extends GridMap {

	private static final double TWO_PI = Math.PI * 2d;

	private final double centerX;
	private final double centerZ;
	private final double maxRadius;
	private final int sectorCount;
	private final double sectorAngle;
	private final double pathAngle;

	public PolarGridMap(MazeMap mazeMap, int pathWidth, int wallWidth) {
		super(mazeMap.getMin(), mazeMap.getMax(), pathWidth, wallWidth);

		centerX = (mapMin.getX() + mapMax.getX()) / 2d;
		centerZ = (mapMin.getZ() + mapMax.getZ()) / 2d;
		maxRadius = Math.max(gridMeshSize, calculateMaxRadius(mazeMap));

		int radialMeshCount = Math.max(2, (int) Math.ceil(maxRadius / gridMeshSize) + 1);
		sectorCount = Math.max(4, (int) Math.round(TWO_PI * Math.max(maxRadius, gridMeshSize * 2) / gridMeshSize));
		sectorAngle = TWO_PI / sectorCount;
		pathAngle = sectorAngle * pathWidth / gridMeshSize;

		createPolarCells(radialMeshCount * 2, sectorCount * 2);
	}

	@Override
	public Vec2 getGridPos(Vec2 loc) {
		double dx = loc.getX() + 0.5d - centerX;
		double dz = loc.getZ() + 0.5d - centerZ;
		double radius = Math.sqrt(dx * dx + dz * dz);
		int gridX = toBandIndex(radius);

		if (gridCells != null) {
			gridX = Math.min(gridX, getWidth() - 1);
		}
		double angle = Math.atan2(dz, dx);

		if (angle < 0) {
			angle += TWO_PI;
		}
		int sector = (int) Math.floor(angle / sectorAngle);
		double angleOffset = angle - sector * sectorAngle;
		int gridZ = sector * 2 + (angleOffset < pathAngle ? 0 : 1);

		return new Vec2(gridX, gridCells == null ? gridZ : normalizeGridZ(gridZ));
	}

	@Override
	public GridCell getCell(int gridX, int gridZ) {
		if (!contains(gridX, gridZ)) {
			return null;
		}
		return gridCells[gridX][normalizeGridZ(gridZ)];
	}

	@Override
	public PathType getPathType(int gridX, int gridZ) {
		if (!contains(gridX, gridZ)) {
			return null;
		}
		return pathTypes[gridX][normalizeGridZ(gridZ)];
	}

	@Override
	public void setPathType(int gridX, int gridZ, PathType type) {
		if (contains(gridX, gridZ)) {
			pathTypes[gridX][normalizeGridZ(gridZ)] = type;
		}
	}

	@Override
	public int getFloorY(Vec2 gridPos) {
		if (!contains(gridPos)) {
			throw new IllegalArgumentException("Floor " + gridPos.getX() + ", " + gridPos.getZ() + " out of grid map.");
		}
		return floorYs[gridPos.getX()][normalizeGridZ(gridPos.getZ())];
	}

	@Override
	public void setFloorY(int gridX, int gridZ, int y) {
		if (contains(gridX, gridZ)) {
			floorYs[gridX][normalizeGridZ(gridZ)] = y;
		}
	}

	@Override
	public int getWallY(Vec2 gridPos) {
		if (!contains(gridPos)) {
			throw new IllegalArgumentException("Wall " + gridPos.getX() + ", " + gridPos.getZ() + " out of grid map.");
		}
		return wallYs[gridPos.getX()][normalizeGridZ(gridPos.getZ())];
	}

	@Override
	public void setWallY(int gridX, int gridZ, int y) {
		if (contains(gridX, gridZ)) {
			wallYs[gridX][normalizeGridZ(gridZ)] = y;
		}
	}

	@Override
	public boolean contains(int gridX, int gridZ) {
		return gridCells != null &&
		       gridX >= 0 && gridX < getWidth() &&
		       getHeight() > 0;
	}

	@Override
	public GridCell getCellBetween(GridCell first, GridCell second) {
		Vec2 firstPos = first.getGridPos();
		Vec2 secondPos = second.getGridPos();
		int gridX = (firstPos.getX() + secondPos.getX()) / 2;
		int firstZ = firstPos.getZ();
		int secondZ = secondPos.getZ();
		int gridZ = Math.abs(firstZ - secondZ) == getHeight() - 2
				? getHeight() - 1
				: (firstZ + secondZ) / 2;

		return getCell(gridX, gridZ);
	}

	@Override
	public boolean hasCustomCells() {
		return true;
	}

	public void setPolarExit(Vec2 exitBlockLoc, MazeMap mazeMap) {
		GridCell exitCell = findNearestPathCell(exitBlockLoc, mazeMap);

		if (exitCell == null) {
			throw new IllegalArgumentException("Exit " + exitBlockLoc + " does not touch a polar path.");
		}
		Vec2 exitTarget = findClosestColumn(exitCell, exitBlockLoc, mazeMap);
		setPathType(exitCell, PathType.EXIT);
		pathStarts.add(exitCell);
		exits.add(new ExitSegment(exitTarget, Direction.EAST, 1));
		carveExit(exitCell, exitBlockLoc, mazeMap);
	}

	@SuppressWarnings("unchecked")
	private void createPolarCells(int gridWidth, int gridHeight) {
		gridCells = new GridCell[gridWidth][gridHeight];
		pathTypes = new PathType[gridWidth][gridHeight];
		floorYs = new int[gridWidth][gridHeight];
		wallYs = new int[gridWidth][gridHeight];

		Set<Vec2>[][] cellColumns = new Set[gridWidth][gridHeight];

		for (int gridX = 0; gridX < gridWidth; ++gridX) {
			for (int gridZ = 0; gridZ < gridHeight; ++gridZ) {
				cellColumns[gridX][gridZ] = new HashSet<>();
			}
		}
		for (int x = mapMin.getX(); x < mapMax.getX(); ++x) {
			for (int z = mapMin.getZ(); z < mapMax.getZ(); ++z) {
				Vec2 gridPos = getGridPos(new Vec2(x, z));

				if (contains(gridPos)) {
					cellColumns[gridPos.getX()][normalizeGridZ(gridPos.getZ())].add(new Vec2(x, z));
				}
			}
		}
		for (int gridX = 0; gridX < gridWidth; ++gridX) {
			for (int gridZ = 0; gridZ < gridHeight; ++gridZ) {
				gridCells[gridX][gridZ] = new GridCell(cellColumns[gridX][gridZ], new Vec2(gridX, gridZ));
				pathTypes[gridX][gridZ] = PathType.FREE;
			}
		}
	}

	private double calculateMaxRadius(MazeMap mazeMap) {
		double result = 0;

		for (int x = mapMin.getX(); x < mapMax.getX(); ++x) {
			for (int z = mapMin.getZ(); z < mapMax.getZ(); ++z) {
				if (mazeMap.getType(x, z) == null) {
					continue;
				}
				double dx = x + 0.5d - centerX;
				double dz = z + 0.5d - centerZ;
				result = Math.max(result, Math.sqrt(dx * dx + dz * dz));
			}
		}
		return result;
	}

	private int toBandIndex(double distance) {
		int mesh = (int) Math.floor(distance / gridMeshSize);
		double offset = distance - mesh * gridMeshSize;
		return mesh * 2 + (offset < pathWidth ? 0 : 1);
	}

	private int normalizeGridZ(int gridZ) {
		return Math.floorMod(gridZ, getHeight());
	}

	private GridCell findNearestPathCell(Vec2 exitBlockLoc, MazeMap mazeMap) {
		Vec2 start = getGridPos(exitBlockLoc);
		int maxSearchDist = getWidth() + getHeight();

		for (int dist = 0; dist <= maxSearchDist; ++dist) {
			for (int dx = -dist; dx <= dist; ++dx) {
				int dz = dist - Math.abs(dx);
				GridCell cell = findPathCell(start.getX() + dx, start.getZ() + dz, mazeMap);

				if (cell != null) {
					return cell;
				}
				if (dz != 0) {
					cell = findPathCell(start.getX() + dx, start.getZ() - dz, mazeMap);

					if (cell != null) {
						return cell;
					}
				}
			}
		}
		return null;
	}

	private GridCell findPathCell(int gridX, int gridZ, MazeMap mazeMap) {
		if (!contains(gridX, gridZ)) {
			return null;
		}
		GridCell cell = getCell(gridX, gridZ);
		Vec2 gridPos = cell.getGridPos();

		if (gridPos.getX() % 2 != 0 || gridPos.getZ() % 2 != 0) {
			return null;
		}
		PathType pathType = getPathType(cell);

		if (pathType != PathType.FREE && pathType != PathType.PAVED && pathType != PathType.EXIT && pathType != PathType.ROOM) {
			return null;
		}
		for (Vec2 column : cell.getColumns()) {
			if (mazeMap.getType(column) == AreaType.FREE) {
				return cell;
			}
		}
		return null;
	}

	private Vec2 findClosestColumn(GridCell cell, Vec2 loc, MazeMap mazeMap) {
		Vec2 result = null;
		int bestDist = Integer.MAX_VALUE;

		for (Vec2 column : cell.getColumns()) {
			if (mazeMap.getType(column) == null) {
				continue;
			}
			int dist = column.sqrDist(loc);

			if (result == null || dist < bestDist) {
				result = column;
				bestDist = dist;
			}
		}
		return result == null ? loc.clone() : result;
	}

	private void carveExit(GridCell exitCell, Vec2 exitBlockLoc, MazeMap mazeMap) {
		Vec2 cellPos = exitCell.getGridPos();
		int sector = cellPos.getZ() / 2;
		int meshIdx = cellPos.getX() / 2;
		//align corridor with the cell's path-band midangle so it snaps to the path grid
		double midAngle = sector * sectorAngle + pathAngle / 2;
		double midRadius = meshIdx * gridMeshSize + pathWidth / 2.0;

		double dirX = Math.cos(midAngle);
		double dirZ = Math.sin(midAngle);
		double perpX = -dirZ;
		double perpZ = dirX;

		//carve toward the click: outward for outer-outline exits, inward for inner-outline
		//exits. Extending pathWidth past the click guarantees the corridor punches through
		//the outline instead of stopping inside the maze.
		double bcx = exitBlockLoc.getX() + 0.5d - centerX;
		double bcz = exitBlockLoc.getZ() + 0.5d - centerZ;
		double clickR = Math.sqrt(bcx * bcx + bcz * bcz);
		double startR;
		double stopR;

		if (clickR >= midRadius) {
			startR = midRadius;
			stopR = clickR + pathWidth;
		} else {
			startR = Math.max(0d, clickR - pathWidth);
			stopR = midRadius;
		}
		int halfBack = pathWidth / 2;

		//step radially in 0.5-block increments so adjacent slabs always overlap and
		//no gaps appear between rows when the corridor angle isn't axis-aligned
		for (double r = startR; r <= stopR; r += 0.5) {
			double px = centerX + r * dirX;
			double pz = centerZ + r * dirZ;

			for (int w = -halfBack; w < pathWidth - halfBack; ++w) {
				int x = (int) Math.floor(px + perpX * w);
				int z = (int) Math.floor(pz + perpZ * w);

				if (mazeMap.contains(x, z) && mazeMap.getType(x, z) != null) {
					mazeMap.setType(x, z, AreaType.PATH);
				}
			}
		}
	}
}
