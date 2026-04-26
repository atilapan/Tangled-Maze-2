package me.gorgeousone.tangledmaze.generation.generator;

import me.gorgeousone.tangledmaze.generation.AreaType;
import me.gorgeousone.tangledmaze.generation.GridCell;
import me.gorgeousone.tangledmaze.generation.MazeMap;
import me.gorgeousone.tangledmaze.util.Vec2;

import java.util.HashSet;
import java.util.Set;

public abstract class BlockGen {
	
	protected static Set<Vec2> getColumns(GridCell cell, MazeMap mazeMap, AreaType areaType) {
		Set<Vec2> columns = new HashSet<>();

		for (Vec2 column : cell.getColumns()) {
			AreaType type = mazeMap.getType(column);

			if (areaType == null ^ type == areaType) {
				columns.add(column);
			}
		}
		return columns;
	}
}
