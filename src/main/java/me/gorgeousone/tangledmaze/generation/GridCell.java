package me.gorgeousone.tangledmaze.generation;

import me.gorgeousone.tangledmaze.generation.paving.PathTree;
import me.gorgeousone.tangledmaze.util.Direction;
import me.gorgeousone.tangledmaze.util.Vec2;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * A class that
 */
public class GridCell {

	private final Vec2 min;
	private final Vec2 max;
	private final Vec2 gridPos;
	private final Set<Vec2> columns;
	
	private transient PathTree tree;
	private transient GridCell parent;
	private int exitDist;

	public GridCell(Vec2 min, Vec2 size, Vec2 gridPos) {
		this.min = min.clone();
		this.max = min.clone().add(size);
		this.gridPos = gridPos;
		this.columns = null;
	}

	public GridCell(Set<Vec2> columns, Vec2 gridPos) {
		this.columns = cloneColumns(columns);
		this.gridPos = gridPos.clone();

		Map.Entry<Vec2, Vec2> bounds = calculateBounds(this.columns);
		this.min = bounds.getKey();
		this.max = bounds.getValue();
	}

	public Vec2 getGridPos() {
		return gridPos.clone();
	}

	public int gridX() {
		return gridPos.getX();
	}

	public int gridZ() {
		return gridPos.getZ();
	}

	public Vec2 getMin() {
		return min.clone();
	}

	public Vec2 getMax() {
		return max.clone();
	}

	public boolean hasCustomShape() {
		return columns != null;
	}

	public Set<Vec2> getColumns() {
		if (columns != null) {
			return cloneColumns(columns);
		}
		Set<Vec2> rectColumns = new HashSet<>();

		for (int x = min.getX(); x < max.getX(); ++x) {
			for (int z = min.getZ(); z < max.getZ(); ++z) {
				rectColumns.add(new Vec2(x, z));
			}
		}
		return rectColumns;
	}

	public boolean contains(Vec2 pos) {
		return contains(pos.getX(), pos.getZ());
	}

	public boolean contains(int x, int z) {
		if (columns != null) {
			return columns.contains(new Vec2(x, z));
		}
		return x >= min.getX() && x < max.getX() &&
				z >= min.getZ() && z < max.getZ();
	}

	public PathTree getTree() {
		return tree;
	}

	public void setTree(PathTree tree) {
		this.tree = tree;
	}

	public boolean hasParent() {
		return parent != null;
	}

	public GridCell getParent() {
		return parent;
	}

	public void setParent(GridCell parent) {
		this.parent = parent;
		this.exitDist = parent == null ? 0 : parent.getExitDist() + 1;
	}
	
	public int getExitDist() {
		return exitDist;
	}
	
	/**
	 * Returns a set of directions in which the world x and z coordinate are border of the grid cell
	 *
	 * @param x world x coordinate inside a grid cell
	 * @param z world z coordinate inside a grid cell
	 */
	public Set<Direction> getWallFacings(int x, int z) {
		Set<Direction> facings = new HashSet<>();

		if (columns != null) {
			for (Direction dir : Direction.CARDINALS) {
				if (!columns.contains(new Vec2(x + dir.getX(), z + dir.getZ()))) {
					facings.add(dir);
				}
			}
			return facings;
		}
		if (x == min.getX()) {
			facings.add(Direction.WEST);

			if (z == min.getZ()) {
				facings.add(Direction.NORTH_WEST);
			}
			if (z == max.getZ() - 1) {
				facings.add(Direction.SOUTH_WEST);
			}
		}
		if (x == max.getX() - 1) {
			facings.add(Direction.EAST);

			if (z == min.getZ()) {
				facings.add(Direction.NORTH_EAST);
			}
			if (z == max.getZ() - 1) {
				facings.add(Direction.SOUTH_EAST);
			}
		}
		if (z == min.getZ()) {
			facings.add(Direction.NORTH);
		}
		if (z == max.getZ() - 1) {
			facings.add(Direction.SOUTH);
		}
		return facings;
	}

	public List<Vec2> getWalls(Direction dir) {
		if (columns != null) {
			List<Vec2> walls = new ArrayList<>();

			for (Vec2 column : columns) {
				if (!columns.contains(column.clone().add(dir.getVec2()))) {
					walls.add(column.clone());
				}
			}
			return walls;
		}
		Vec2 iter = dir.isPositive() ? max.clone().add(-1, -1) : min.clone();
		Direction ortho = dir.isCollinearX() ? dir.getLeft() : dir.getRight();
		Vec2 step = ortho.getVec2();
		Vec2 cellSize = max.clone().sub(min);
		int limit = dir.isCollinearX() ? cellSize.getZ() : cellSize.getX();
		List<Vec2> walls = new ArrayList<>();

		for (int i = 0; i < limit; ++i) {
			walls.add(iter.clone());
			iter.add(step);
		}
		return walls;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof GridCell)) {
			return false;
		}
		GridCell gridCell = (GridCell) o;
		return Objects.equals(gridPos, gridCell.gridPos);
	}

	@Override
	public int hashCode() {
		return gridPos.hashCode();
	}

	@Override
	public String toString() {
		return "[" +
				"grid=" + gridPos +
				", min=" + min +
				", max=" + max +
				']';
	}

	private static Set<Vec2> cloneColumns(Set<Vec2> source) {
		Set<Vec2> result = new HashSet<>();

		for (Vec2 column : source) {
			result.add(column.clone());
		}
		return result;
	}

	private static Map.Entry<Vec2, Vec2> calculateBounds(Set<Vec2> columns) {
		if (columns.isEmpty()) {
			return new AbstractMap.SimpleEntry<>(new Vec2(0, 0), new Vec2(0, 0));
		}
		Vec2 min = null;
		Vec2 max = null;

		for (Vec2 column : columns) {
			if (min == null) {
				min = column.clone();
				max = column.clone();
				continue;
			}
			if (column.getX() < min.getX()) {
				min.setX(column.getX());
			}
			if (column.getZ() < min.getZ()) {
				min.setZ(column.getZ());
			}
			if (column.getX() > max.getX()) {
				max.setX(column.getX());
			}
			if (column.getZ() > max.getZ()) {
				max.setZ(column.getZ());
			}
		}
		return new AbstractMap.SimpleEntry<>(min, max.add(1, 1));
	}
}
