package me.gorgeousone.tangledmaze.generation.polar;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Iterative randomised DFS over the polar cell graph that yields a spanning tree.
 * A spanning tree has exactly one path between any two cells, which gives the maze
 * a unique solution between any two exits.
 */
public class PolarPathGen {

	public static void generate(PolarLayout layout, List<PolarCell> starts, int seed) {
		Random random = seed == 0 ? new Random() : new Random(seed);
		Set<PolarCell> visited = new HashSet<>();
		PolarCell root = starts.isEmpty() ? layout.cell(0, 0) : starts.get(0);
		dfs(layout, root, visited, random);

		//defensive sweep: re-DFS from any cell the first run missed (shouldn't happen for
		//a connected layout, but a stray orphan cell would otherwise rasterise as a sealed
		//room with no openings)
		for (List<PolarCell> ring : layout.allCells()) {
			for (PolarCell cell : ring) {
				if (!visited.contains(cell)) {
					dfs(layout, cell, visited, random);
				}
			}
		}
	}

	private static void dfs(PolarLayout layout, PolarCell start,
	                        Set<PolarCell> visited, Random random) {
		List<PolarCell> stack = new ArrayList<>();
		stack.add(start);
		visited.add(start);

		while (!stack.isEmpty()) {
			PolarCell current = stack.get(stack.size() - 1);
			List<PolarCell> candidates = new ArrayList<>(layout.neighboursOf(current));
			candidates.removeIf(visited::contains);

			if (candidates.isEmpty()) {
				stack.remove(stack.size() - 1);
				continue;
			}
			Collections.shuffle(candidates, random);
			PolarCell next = candidates.get(0);
			current.openTo(next);
			visited.add(next);
			stack.add(next);
		}
	}
}
